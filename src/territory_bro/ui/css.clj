;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.ui.css
  (:require [clojure.string :as str]
            [territory-bro.infra.json :as json]
            [territory-bro.infra.resources :as resources]))

(def ^:private *css-modules (atom {:resource-path "css-modules.json"}))

(defn modules []
  (resources/auto-refresh *css-modules #(json/read-value (slurp %))))


(defn classes [& names]
  (str/join " " names))
