;; Copyright © 2015-2019 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.middleware
  (:require [clojure.tools.logging :as log]
            [immutant.web.middleware :refer [wrap-session]]
            [ring.logger :as logger]
            [ring.middleware.defaults :refer [site-defaults wrap-defaults]]
            [ring.middleware.format :refer [wrap-restful-format]]
            [ring.middleware.http-response :refer [wrap-http-response]]
            [ring.middleware.reload :refer [wrap-reload]]
            [ring.middleware.session.memory :as memory-session]
            [ring.util.http-response :refer [internal-server-error]]
            [ring.util.response :as response]
            [territory-bro.config :refer [env]]
            [territory-bro.projections :as projections]
            [territory-bro.util :as util])
  (:import (java.time Duration)))

(defn wrap-internal-error [handler]
  (fn [req]
    (try
      (handler req)
      (catch Throwable t
        ;; XXX: clojure.tools.logging/error does not log the ex-data by default https://clojure.atlassian.net/browse/TLOG-17
        (log/error t (str "Uncaught exception "
                          (pr-str (select-keys req [:request-method :uri]))
                          "\n"
                          (pr-str t)))
        (-> (internal-server-error "Internal Server Error")
            (response/content-type "text/html; charset=utf-8"))))))

(defn wrap-sqlexception-chain [handler]
  (fn [req]
    (try
      (handler req)
      (catch Throwable t
        (throw (util/fix-sqlexception-chain t))))))

(defn wrap-formats [handler]
  ;; TODO: is this needed or is liberator self-sufficient?
  (wrap-restful-format handler {:formats [:json-kw :transit-json :transit-msgpack]}))

(defn wrap-default-content-type [handler]
  ;; Chrome gives an ERR_INVALID_RESPONSE error about 4xx pages
  ;; with application/octet-stream content type, which is Ring's
  ;; default when no content type is not defined.
  (fn [req]
    (let [resp (handler req)]
      (if (response/get-header resp "Content-Type")
        resp
        (response/content-type resp "text/plain")))))

;; Avoid forgetting sessions every time the code is reloaded in development mode
(defonce session-store (memory-session/memory-store))

(def ^:private mutative-operation #{:put :post :delete})

(defn wrap-projections-refresh [handler]
  (fn [req]
    (let [resp (handler req)]
      (when (contains? mutative-operation (:request-method req))
        (projections/refresh-async!)
        ;; TODO: store the observed revision in session and await before the next read if the cache is out of date
        (projections/await-refreshed (Duration/ofSeconds 10)))
      resp)))

(defn wrap-base [handler]
  (-> handler
      wrap-projections-refresh
      (cond->
        (:dev env) (wrap-reload {:dirs ["src" "resources"]}))
      wrap-sqlexception-chain
      wrap-http-response
      wrap-default-content-type
      (logger/wrap-with-logger {:request-keys (conj logger/default-request-keys :remote-addr)})
      (wrap-defaults (-> site-defaults
                         (assoc :proxy true)
                         (assoc-in [:security :anti-forgery] false) ; TODO: enable CSRF
                         (assoc-in [:session :store] session-store)
                         (assoc-in [:session :flash] false)))
      wrap-internal-error))
