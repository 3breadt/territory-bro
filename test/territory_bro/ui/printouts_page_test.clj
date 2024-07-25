;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.ui.printouts-page-test
  (:require [clojure.string :as str]
            [clojure.test :refer :all]
            [clojure.walk :as walk]
            [territory-bro.api-test :as at]
            [territory-bro.domain.testdata :as testdata]
            [territory-bro.gis.geometry :as geometry]
            [territory-bro.infra.authentication :as auth]
            [territory-bro.infra.config :as config]
            [territory-bro.test.fixtures :refer :all]
            [territory-bro.test.testutil :refer [replace-in]]
            [territory-bro.ui.html :as html]
            [territory-bro.ui.map-interaction-help-test :as map-interaction-help-test]
            [territory-bro.ui.printouts-page :as printouts-page])
  (:import (java.time Clock Duration ZoneOffset ZonedDateTime)
           (java.util UUID)))

(def default-model
  {:congregation {:congregation/id (UUID. 0 1)
                  :congregation/name "Example Congregation"
                  :congregation/location (str (geometry/parse-wkt testdata/wkt-helsinki))
                  :congregation/timezone testdata/timezone-helsinki}
   :regions [{:region/id (UUID. 0 2)
              :region/name "the region"
              :region/location testdata/wkt-south-helsinki}]
   :territories [{:territory/id (UUID. 0 3)
                  :territory/number "123"
                  :territory/addresses "the addresses"
                  :territory/region "the region"
                  :territory/meta {:foo "bar"}
                  :territory/location testdata/wkt-helsinki-rautatientori}]
   :card-minimap-viewports [testdata/wkt-helsinki]
   :form {:template "TerritoryCard"
          :language "en"
          :map-raster "osmhd"
          :regions #{(UUID. 0 1)} ; congregation boundary is shown first in the regions list
          :territories #{(UUID. 0 3)}}
   :mac? false})

(def form-changed-model
  (assoc default-model
         :form {:template "NeighborhoodCard"
                :language "fi"
                :map-raster "mmlTaustakartta"
                :regions #{(UUID. 0 4)
                           (UUID. 0 5)}
                :territories #{(UUID. 0 6)
                               (UUID. 0 7)}}))

(def demo-model
  (-> default-model
      (replace-in [:congregation :congregation/id] (UUID. 0 1) "demo")
      (replace-in [:congregation :congregation/name] "Example Congregation" "Demo Congregation")
      (replace-in [:form :regions] #{(UUID. 0 1)} #{"demo"})))

(deftest ^:slow model!-test
  (with-fixtures [db-fixture api-fixture]
    ;; TODO: decouple this test from the database
    (let [session (at/login! at/app)
          user-id (at/get-user-id session)
          cong-id (at/create-congregation! session "Example Congregation")
          _ (at/create-congregation-boundary! cong-id)
          _ (at/create-card-minimap-viewport! cong-id)
          region-id (at/create-region! cong-id)
          territory-id (at/create-territory! cong-id)
          request {:params {:congregation (str cong-id)}}
          fix (fn [model]
                (walk/postwalk-replace {(UUID. 0 1) cong-id
                                        (UUID. 0 2) region-id
                                        (UUID. 0 3) territory-id}
                                       model))]
      (auth/with-user-id user-id

        (testing "default"
          (is (= (fix default-model)
                 (printouts-page/model! request))))

        (testing "every form value changed"
          (let [request (update request :params merge {:template "NeighborhoodCard"
                                                       :language "fi"
                                                       :map-raster "mmlTaustakartta"
                                                       :regions [(str (UUID. 0 4))
                                                                 (str (UUID. 0 5))]
                                                       :territories [(str (UUID. 0 6))
                                                                     (str (UUID. 0 7))]})]
            (is (= (fix form-changed-model)
                   (printouts-page/model! request)))))

        (testing "demo congregation"
          (binding [config/env (replace-in config/env [:demo-congregation] nil cong-id)]
            (let [request {:params {:congregation "demo"}}]
              (is (= (fix demo-model)
                     (printouts-page/model! request))))))))))

(deftest parse-uuid-multiselect-test
  (is (= #{} (printouts-page/parse-uuid-multiselect nil)))
  (is (= #{} (printouts-page/parse-uuid-multiselect "")))
  (is (= #{"demo"} (printouts-page/parse-uuid-multiselect "demo")))
  (is (= #{(UUID. 0 1)}
         (printouts-page/parse-uuid-multiselect "00000000-0000-0000-0000-000000000001")))
  (is (= #{(UUID. 0 1)
           (UUID. 0 2)}
         (printouts-page/parse-uuid-multiselect ["00000000-0000-0000-0000-000000000001"
                                                 "00000000-0000-0000-0000-000000000002"]))))

(deftest render-qr-code-svg-test
  (let [svg (printouts-page/render-qr-code-svg "foo")]
    (is (str/includes? svg "viewBox=\"0 0 21 21\""))
    (is (str/includes? svg "M0,0h1v1h-1z M1,0h1v1h-1z"))))

(deftest view-test
  (binding [printouts-page/*clock* (-> (.toInstant (ZonedDateTime/of 2000 12 31 23 59 0 0 testdata/timezone-helsinki))
                                       (Clock/fixed ZoneOffset/UTC))]
    (testing "territory printout"
      (is (= (html/normalize-whitespace
              "Printouts

               Print options
                 Template [Territory card]
                 Language [English]
                 Background map [World - OpenStreetMap (RRZE server, high DPI)]
                 Regions [Example Congregation]
                 Territories [123 - the region]

               Territory Map Card
                 the region
                 123
                 Printed 2000-12-31 with TerritoryBro.com
                 the addresses
                 Please keep this card in the envelope. Do not soil, mark or bend it.
                 Each time the territory is covered, please inform the brother who cares for the territory files."
              map-interaction-help-test/default-visible-text)
             (-> (printouts-page/view default-model)
                 html/visible-text))))

    (testing "region printout"
      (is (= (html/normalize-whitespace
              "Printouts

               Print options
                 Template [Region map]
                 Language [English]
                 Background map [World - OpenStreetMap (RRZE server, high DPI)]
                 Regions [Example Congregation]
                 Territories [123 - the region]

               Example Congregation
                 Printed 2000-12-31 with TerritoryBro.com"
              map-interaction-help-test/default-visible-text)
             (-> (printouts-page/view (assoc-in default-model [:form :template] "RegionPrintout"))
                 html/visible-text))))

    (binding [printouts-page/*clock* (Clock/offset printouts-page/*clock* (Duration/ofMinutes 1))]
      (testing "print date uses the congregation timezone"
        (is (str/includes? (-> (printouts-page/view default-model)
                               html/visible-text)
                           "Printed 2001-01-01"))))))
