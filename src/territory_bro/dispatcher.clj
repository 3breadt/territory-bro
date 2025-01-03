(ns territory-bro.dispatcher
  (:require [clojure.pprint :as pprint]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [territory-bro.commands :as commands]
            [territory-bro.domain.card-minimap-viewport :as card-minimap-viewport]
            [territory-bro.domain.congregation :as congregation]
            [territory-bro.domain.congregation-boundary :as congregation-boundary]
            [territory-bro.domain.do-not-calls :as do-not-calls]
            [territory-bro.domain.publisher :as publisher]
            [territory-bro.domain.region :as region]
            [territory-bro.domain.share :as share]
            [territory-bro.domain.territory :as territory]
            [territory-bro.events :as events]
            [territory-bro.gis.db-admin :as db-admin]
            [territory-bro.gis.gis-db :as gis-db]
            [territory-bro.gis.gis-user :as gis-user]
            [territory-bro.infra.authentication :as auth]
            [territory-bro.infra.db :as db]
            [territory-bro.infra.event-store :as event-store]
            [territory-bro.infra.foreign-key :as foreign-key]
            [territory-bro.infra.user :as user]))

;;;; Helpers

(defn- default-injections [command state]
  {:check-permit #(commands/check-permit state command %)})

(defn- reference-checkers [command conn state]
  {:card-minimap-viewport (fn [card-minimap-viewport-id]
                            (card-minimap-viewport/check-card-minimap-viewport-exists state (:congregation/id command) card-minimap-viewport-id)
                            true)
   :congregation-boundary (fn [congregation-boundary-id]
                            (congregation-boundary/check-congregation-boundary-exists state (:congregation/id command) congregation-boundary-id)
                            true)
   :congregation (fn [cong-id]
                   (congregation/check-congregation-exists state cong-id)
                   true)
   :publisher (fn [publisher-id]
                (publisher/check-publisher-exists conn (:congregation/id command) publisher-id)
                true)
   :region (fn [region-id]
             (region/check-region-exists state (:congregation/id command) region-id)
             true)
   :share (fn [share-id]
            (share/check-share-exists state share-id)
            true)
   :territory (fn [territory-id]
                (territory/check-territory-exists state (:congregation/id command) territory-id)
                true)
   :user (fn [user-id]
           (user/check-user-exists conn user-id)
           true)
   :user-or-anonymous (fn [user-id]
                        (when-not (auth/anonymous? user-id)
                          (user/check-user-exists conn user-id))
                        true)
   :new (fn [stream-id]
          (event-store/check-new-stream conn stream-id)
          true)
   ;; XXX: Unsafe should only be used for trusted sources, such as the GIS sync which has other mechanisms
   ;;      to verify that the ID is only used by that particular congregation and entity type.
   :unsafe (fn [stream-id]
             (some? stream-id))})

(defn validate-command [command conn state]
  (binding [foreign-key/*reference-checkers* (reference-checkers command conn state)]
    (commands/validate-command command)))

(defn- write-stream!
  "Appends events to a stream with optimistic concurrency control."
  [conn stream-id f]
  (let [*count (volatile! 0)
        old-events (->> (event-store/read-stream conn stream-id)
                        (eduction (map (fn [v]
                                         (vswap! *count inc)
                                         v))))
        new-events (f old-events)]
    (event-store/save! conn stream-id @*count new-events)))

(defn- append-stream!
  "Appends events to a stream unconditionally."
  [conn stream-id new-events]
  (event-store/save! conn stream-id nil new-events))

(defn- call! [command-handler command state-or-old-events injections]
  (->> (command-handler command state-or-old-events injections)
       (events/enrich-events command)
       (mapv events/sorted-keys)
       (events/strict-validate-events)))


;;;; Command handlers

(defn- card-minimap-viewport-command! [conn command state]
  (let [injections (default-injections command state)]
    (write-stream! conn
                   (:card-minimap-viewport/id command)
                   (fn [old-events]
                     (call! card-minimap-viewport/handle-command command old-events injections)))))

(defn- congregation-boundary-command! [conn command state]
  (let [injections (default-injections command state)]
    (write-stream! conn
                   (:congregation-boundary/id command)
                   (fn [old-events]
                     (call! congregation-boundary/handle-command command old-events injections)))))

(defn- congregation-command! [conn command state]
  (let [injections (assoc (default-injections command state)
                          :generate-tenant-schema-name (fn [cong-id]
                                                         (db/generate-tenant-schema-name conn cong-id)))]
    (write-stream! conn
                   (:congregation/id command)
                   (fn [old-events]
                     (call! congregation/handle-command command old-events injections)))))

(defn- db-admin-command! [conn command state]
  (let [injections (assoc (default-injections command state)
                          :migrate-tenant-schema! db/migrate-tenant-schema!
                          :ensure-gis-user-present! (fn [args]
                                                      (gis-db/ensure-user-present! conn args))
                          :ensure-gis-user-absent! (fn [args]
                                                     (gis-db/ensure-user-absent! conn args)))]
    (call! db-admin/handle-command command state injections)))

(defn- do-not-calls-command! [conn command state]
  (let [injections (assoc (default-injections command state)
                          :conn conn)]
    (call! do-not-calls/handle-command command state injections)))

(defn- gis-user-command! [conn command state]
  (let [injections (assoc (default-injections command state)
                          :generate-password #(gis-user/generate-password 50)
                          :db-user-exists? #(gis-db/user-exists? conn %))]
    (write-stream! conn
                   (:congregation/id command) ; TODO: the GIS user events would belong better to a user-specific stream
                   (fn [old-events]
                     (call! gis-user/handle-command command old-events injections)))))

(defn- publisher-command! [conn command state]
  (let [injections (assoc (default-injections command state)
                          :conn conn)]
    (call! publisher/handle-command command state injections)))

(defn- region-command! [conn command state]
  (let [injections (default-injections command state)]
    (write-stream! conn
                   (:region/id command)
                   (fn [old-events]
                     (call! region/handle-command command old-events injections)))))

(defn- share-command! [conn command state]
  (let [injections (default-injections command state)]
    (append-stream! conn
                    (:share/id command)
                    (call! share/handle-command command state injections))))

(defn- territory-command! [conn command state]
  (let [injections (default-injections command state)]
    (write-stream! conn
                   (:territory/id command)
                   (fn [old-events]
                     (call! territory/handle-command command old-events injections)))))

(def ^:private command-handlers
  {"card-minimap-viewport.command" card-minimap-viewport-command!
   "congregation-boundary.command" congregation-boundary-command!
   "congregation.command" congregation-command!
   "db-admin.command" db-admin-command!
   "do-not-calls.command" do-not-calls-command!
   "gis-user.command" gis-user-command!
   "publisher.command" publisher-command!
   "region.command" region-command!
   "share.command" share-command!
   "territory.command" territory-command!})


;;;; Entry point with centralized validation and logging

(defn- pretty-str [object]
  (str/trimr (with-out-str
               (print "\n")
               (pprint/pprint object))))

(defn ^:dynamic command! [conn state command]
  (let [command (commands/sorted-keys command)]
    (try
      (log/info "Dispatch command:" (pretty-str command))
      (let [command (validate-command command conn state)
            command-handler (or (get command-handlers (namespace (:command/type command)))
                                (throw (AssertionError.
                                        (str "No command handler for command: " (pr-str command)))))
            events (command-handler conn command state)]
        (log/info "Produced events:" (pretty-str events))
        events)
      (catch Throwable t
        ;; the full stack trace will be logged at a higher level
        (log/warn "Command failed:" (str t))
        (throw t)))))

(defn demo-command! [state command]
  (when-not (contains? #{:territory.command/assign-territory
                         :territory.command/delete-assignment
                         :territory.command/return-territory}
                       (:command/type command))
    (throw (IllegalArgumentException. (str "Command not allowed in demo: " (pr-str command)))))
  (try
    (let [command (commands/sorted-keys command)]
      (log/info "Dispatch demo command:" (pretty-str command))
      (let [injections (default-injections command state)
            events (territory/handle-demo-command command state injections)]
        (log/info "Produced demo events:" (pretty-str events))
        events))
    (catch Throwable t
      (log/warn "Command failed:" (str t))
      (throw t))))
