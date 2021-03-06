;; Copyright © 2015-2020 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.config
  (:require [clojure.string :as str]
            [cprop.core :as cprop]
            [mount.core :as mount]
            [territory-bro.util :refer [getx]])
  (:import (java.time Instant)
           (java.util UUID)))

(defn try-parse-uuid [s]
  (try
    (UUID/fromString s)
    (catch Exception _
      s)))

(defn enrich-env [env]
  (assoc env
         :now #(Instant/now)
         :jwt-issuer (str "https://" (getx env :auth0-domain) "/")
         :jwt-audience (getx env :auth0-client-id)
         :super-users (->> (str/split (or (:super-users env) "")
                                      #"\s+")
                           (remove str/blank?)
                           (map try-parse-uuid)
                           (set))
         :demo-congregation (try-parse-uuid (:demo-congregation env))))

(mount/defstate ^:dynamic env
  :start (-> (cprop/load-config :resource "config-defaults.edn") ; TODO: use ":as-is? true" and schema coercion?
             (enrich-env)))
