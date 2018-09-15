; Copyright © 2015-2018 Esko Luontola
; This software is released under the Apache License 2.0.
; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.env
  (:require [clojure.tools.logging :as log]))

(def defaults
  {:init
   (fn []
     (log/info "\n-=[territory-bro started successfully]=-"))
   :stop
   (fn []
     (log/info "\n-=[territory-bro has shut down successfully]=-"))
   :middleware identity})
