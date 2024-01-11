;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.ui.i18n
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [territory-bro.infra.json :as json]
            [territory-bro.infra.resources :as resources]))

(def ^:dynamic *lang* :en)

(def ^:private *i18n (atom {:resource (io/resource "i18n.json")}))

(defn i18n []
  (resources/auto-refresh *i18n #(json/read-value (slurp %))))

(defn t [key]
  (let [path (->> (str/split key #"\.")
                  (map keyword))]
    (or (-> (i18n) :resources *lang* :translation (get-in path))
        key)))
