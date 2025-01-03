(ns territory-bro.infra.db
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [hikari-cp.core :as hikari-cp]
            [hugsql.adapter.next-jdbc :as next-adapter]
            [hugsql.core :as hugsql]
            [mount.core :as mount]
            [next.jdbc :as jdbc]
            [next.jdbc.prepare :as prepare]
            [next.jdbc.result-set :as result-set]
            [next.jdbc.transaction :as transaction]
            [territory-bro.infra.config :as config]
            [territory-bro.infra.json :as json]
            [territory-bro.infra.resources :as resources]
            [territory-bro.infra.util :refer [getx]])
  (:import (clojure.lang IPersistentMap IPersistentVector)
           (com.zaxxer.hikari HikariDataSource)
           (java.net URI)
           (java.nio.file Paths)
           (java.sql Array Connection Date PreparedStatement Timestamp)
           (java.time Instant ZoneOffset)
           (org.apache.http.client.utils URIBuilder)
           (org.flywaydb.core Flyway)
           (org.flywaydb.core.api FlywayException)
           (org.postgresql.jdbc PgResultSetMetaData)
           (org.postgresql.util PGobject)))

(alter-var-root #'transaction/*nested-tx* (constantly :prohibit))

(def jdbc-opts {:builder-fn result-set/as-unqualified-lower-maps})
(hugsql/set-adapter! (next-adapter/hugsql-adapter-next-jdbc jdbc-opts))

(def expected-postgresql-version 16)
(def ^:dynamic *explain* false)

;; PostgreSQL error codes
;; https://www.postgresql.org/docs/11/errcodes-appendix.html
;; Some are also in org.postgresql.util.PSQLState but not all, so we list them here explicitly.
(def psql-serialization-failure "40001")
(def psql-deadlock-detected "40P01")
(def psql-undefined-object "42704")
(def psql-duplicate-object "42710")

(def ^:private public-schema "public") ; contains PostGIS types

(defn master-search-path []
  (str/join "," [(:database-schema config/env)
                 public-schema]))

(defn tenant-search-path [tenant-schema]
  (str/join "," [tenant-schema
                 (:database-schema config/env)
                 public-schema]))

(defn alter-jdbc-url [url f]
  (let [url (str/replace-first url "jdbc:" "")]
    (str "jdbc:" (f (URIBuilder. url)))))

(defn- hide-password [url]
  (str/replace url #"password=\w+" "password=******"))

(defn connect! ^HikariDataSource [database-url]
  (log/info "Connect" (hide-password database-url))
  (hikari-cp/make-datasource {:jdbc-url database-url}))

(defn disconnect! [^HikariDataSource datasource]
  (log/info "Disconnect" (hide-password (.getJdbcUrl datasource)))
  (.close datasource))

(mount/defstate ^HikariDataSource datasource
  :start (connect! (-> (getx config/env :database-url)
                       (alter-jdbc-url #(-> ^URIBuilder %
                                            (.setParameter "currentSchema" (master-search-path))))))
  :stop (disconnect! datasource))

(defn get-connection ^Connection []
  (jdbc/get-connection datasource))

(defn get-tenant-connection ^Connection [schema]
  (let [url (-> (getx config/env :database-url)
                (alter-jdbc-url #(-> ^URIBuilder %
                                     (.setParameter "currentSchema" (tenant-search-path schema)))))]
    (jdbc/get-connection {:connection-uri url})))

(defmacro with-transaction [binding & body]
  (let [conn (first binding)
        options (second binding)]
    `(with-open [conn# (get-connection)]
       (jdbc/with-transaction [~conn conn# (merge {:isolation :read-committed}
                                                  ~options)]
         ~@body))))

(defn execute! [connectable sql-params]
  (jdbc/execute! connectable sql-params jdbc-opts))

(defn execute-one! [connectable sql-params]
  (jdbc/execute-one! connectable sql-params jdbc-opts))


;;;; SQL type conversions

(defn- array? [obj]
  (.isArray (class obj)))

(defn- array->vector
  "Converts multidimensional arrays to nested vectors."
  [obj]
  (if (array? obj)
    (mapv array->vector obj)
    obj))

(defn- pg-json->clj [^PGobject val]
  (let [type (.getType val)
        value (.getValue val)]
    (case type
      "json" (json/read-value value)
      "jsonb" (json/read-value value)
      value)))

(defn clj->pg-json [value]
  (doto (PGobject.)
    (.setType "jsonb")
    (.setValue (json/write-value-as-string value))))

(extend-protocol result-set/ReadableColumn
  Date
  (read-column-by-index [val ^PgResultSetMetaData _rsmeta _idx]
    (.toLocalDate val))
  (read-column-by-label [val _label]
    (result-set/read-column-by-index val nil nil))

  Timestamp
  (read-column-by-index [val ^PgResultSetMetaData _rsmeta _idx]
    (.toInstant val))
  (read-column-by-label [val _label]
    (result-set/read-column-by-index val nil nil))

  Array
  (read-column-by-index [val ^PgResultSetMetaData _rsmeta _idx]
    (array->vector (.getArray val)))
  (read-column-by-label [val _label]
    (result-set/read-column-by-index val nil nil))

  PGobject
  (read-column-by-index [val ^PgResultSetMetaData _rsmeta _idx]
    (pg-json->clj val))
  (read-column-by-label [val _label]
    (result-set/read-column-by-index val nil nil)))

(defn- pg-array-parameter-element-type [^PreparedStatement stmt idx]
  (let [meta (.getParameterMetaData stmt)
        type-name (.getParameterTypeName meta idx)]
    (when (str/starts-with? type-name "_")
      (subs type-name 1))))

(extend-protocol prepare/SettableParameter
  Instant
  (set-parameter [val ^PreparedStatement stmt idx]
    (.setObject stmt idx (.atOffset val ZoneOffset/UTC)))

  IPersistentMap
  (set-parameter [val ^PreparedStatement stmt idx]
    (.setObject stmt idx (clj->pg-json val)))

  IPersistentVector
  (set-parameter [val ^PreparedStatement stmt idx]
    (if-let [element-type (pg-array-parameter-element-type stmt idx)]
      (.setObject stmt idx (-> (.getConnection stmt)
                               (.createArrayOf element-type (to-array val))))
      (.setObject stmt idx (clj->pg-json val)))))


;;;; Database schemas

(def ^:dynamic *clean-disabled* true)

(defn- ^String/1 strings [& ss]
  (into-array String ss))

(defn ^Flyway master-schema [schema]
  (-> (Flyway/configure)
      (.dataSource datasource)
      (.schemas (strings schema))
      (.locations (strings "classpath:db/flyway/master"))
      (.placeholders {"masterSchema" schema})
      (.cleanDisabled *clean-disabled*)
      (.load)))

(defn ^Flyway tenant-schema [schema master-schema]
  (-> (Flyway/configure)
      (.dataSource datasource)
      (.schemas (strings schema))
      (.locations (strings "classpath:db/flyway/tenant"))
      (.placeholders {"masterSchema" master-schema})
      (.cleanDisabled *clean-disabled*)
      (.load)))

(defn migrate-master-schema! []
  (let [schema (:database-schema config/env)]
    (log/info "Migrating master schema:" schema)
    (-> (master-schema schema)
        (.migrate))))

(defn migrate-tenant-schema! [schema]
  (log/info "Migrating tenant schema:" schema)
  (-> (tenant-schema schema (:database-schema config/env))
      (.migrate)))

(defn tenant-schema-up-to-date? [schema]
  (try
    (-> (tenant-schema schema (:database-schema config/env))
        (.validate))
    true
    (catch FlywayException _
      false)))

(defn get-schemas [conn]
  (->> (execute! conn ["select schema_name from information_schema.schemata"])
       (mapv :schema_name)))

(defn generate-tenant-schema-name [conn cong-id]
  (let [master-schema (:database-schema config/env)
        tenant-schema (str master-schema
                           "_"
                           (str/replace (str cong-id) "-" ""))]
    (assert (not (contains? (set (get-schemas conn))
                            tenant-schema))
            {:schema-name tenant-schema})
    tenant-schema))


;;;; Queries

(defn check-database-version [minimum-version]
  (with-transaction [conn {:read-only true}]
    (let [metadata (.getMetaData ^Connection conn)
          version (.getDatabaseMajorVersion metadata)]
      (assert (>= version minimum-version)
              (str "Expected the database to be PostgreSQL " minimum-version " but it was "
                   (.getDatabaseProductName metadata) " " (.getDatabaseProductVersion metadata))))))

(defn auto-explain* [conn min-duration f]
  (execute-one! conn ["LOAD 'auto_explain'"])
  (execute-one! conn [(str "SET auto_explain.log_min_duration = " (int min-duration))])
  (execute-one! conn ["SET auto_explain.log_analyze = true"])
  (execute-one! conn ["SET auto_explain.log_buffers = true"])
  (execute-one! conn ["SET auto_explain.log_triggers = true"])
  (execute-one! conn ["SET auto_explain.log_verbose = true"])
  ;; will explain also the queries inside triggers, which territory-bro.infra.db/explain-query cannot do
  (execute-one! conn ["SET auto_explain.log_nested_statements = true"])
  (let [result (f)]
    (execute-one! conn ["SET auto_explain.log_min_duration = -1"])
    result))

(defmacro auto-explain [conn min-duration & body]
  `(auto-explain* ~conn ~min-duration (fn [] ~@body)))

(defn log-all-queries-in-this-transaction! [conn]
  (execute-one! conn ["SELECT set_config('log_statement', 'all', true)"]))

(defn- explain-query [conn sql params]
  (execute-one! conn ["SAVEPOINT explain_analyze"])
  (try
    (->> (execute! conn (cons (str "EXPLAIN (ANALYZE, VERBOSE, SETTINGS, WAL, BUFFERS"
                                   (when (<= 17 expected-postgresql-version)
                                     ", MEMORY")
                                   ") "
                                   sql)
                              params))
         (mapv (keyword "query plan")))
    (finally
      ;; ANALYZE will actually execute the query, so any side effects
      ;; must be rolled back to avoid executing them twice
      (execute-one! conn ["ROLLBACK TO SAVEPOINT explain_analyze"]))))

(defn- prefix-join [prefix ss]
  (str prefix (str/join prefix ss)))

(defn- log-query [conn queries query-name params]
  (when *explain*
    (let [query (get-in queries [:sqlvec-fns query-name])
          filename (.getFileName (Paths/get (URI. (-> query :meta :file))))
          sqlvec-fn (:fn query)
          [sql & params] (apply sqlvec-fn params)
          query-plan (explain-query conn sql params)]
      (log/debug (str "Explain SQL query " (name query-name) " in " filename ":\n"
                      sql
                      (when (not (empty? params))
                        (str "\n-- Parameters:"
                             (prefix-join "\n--\t" (map pr-str params))))
                      "\n-- Query plan:"
                      (prefix-join "\n--\t" query-plan))))))


(defn query! [conn queries-cache query-name & params]
  (let [queries (queries-cache)
        query-fn (or (get-in queries [:db-fns query-name :fn])
                     (throw (IllegalArgumentException. (str "Query not found: " query-name))))]
    (log-query conn queries query-name params)
    (apply query-fn conn params)))

(defn plan-query [conn queries-cache query-name & params]
  (let [queries (queries-cache)
        sqlvec-fn (or (get-in queries [:sqlvec-fns query-name :fn])
                      (throw (IllegalArgumentException. (str "Query not found: " query-name))))]
    (log-query conn queries query-name params)
    (jdbc/plan conn (apply sqlvec-fn params) jdbc-opts)))

(defn- load-queries [resource]
  {:db-fns (hugsql/map-of-db-fns resource {:quoting :ansi})
   :sqlvec-fns (hugsql/map-of-sqlvec-fns resource {:quoting :ansi
                                                   :fn-suffix ""})})

(defn compile-queries [path]
  (resources/auto-refresher (io/resource path) load-queries))
