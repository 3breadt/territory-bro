(ns territory-bro.ui.printouts-page-test
  (:require [clojure.string :as str]
            [clojure.test :refer :all]
            [matcher-combinators.test :refer :all]
            [reitit.ring :as ring]
            [territory-bro.domain.congregation :as congregation]
            [territory-bro.domain.demo :as demo]
            [territory-bro.domain.dmz :as dmz]
            [territory-bro.domain.dmz-test :as dmz-test]
            [territory-bro.domain.share :as share]
            [territory-bro.domain.testdata :as testdata]
            [territory-bro.infra.db :as db]
            [territory-bro.infra.permissions :as permissions]
            [territory-bro.infra.user :as user]
            [territory-bro.test.fixtures :refer :all]
            [territory-bro.test.testutil :as testutil :refer [replace-in]]
            [territory-bro.ui.html :as html]
            [territory-bro.ui.map-interaction-help-test :as map-interaction-help-test]
            [territory-bro.ui.printouts-page :as printouts-page])
  (:import (clojure.lang ExceptionInfo)
           (java.time Clock Duration ZoneOffset ZonedDateTime)
           (java.util UUID)))

(def cong-id (UUID. 0 1))
(def region-id (UUID. 0 2))
(def territory-id (UUID. 0 3))
(def user-id (random-uuid))
(def default-model
  {:congregation {:congregation/id cong-id
                  :congregation/name "Example Congregation"
                  :congregation/location testdata/wkt-helsinki
                  :congregation/timezone testdata/timezone-helsinki}
   :regions [{:region/id region-id
              :region/name "the region"
              :region/location testdata/wkt-south-helsinki}]
   :territories [{:congregation/id cong-id
                  :territory/id territory-id
                  :territory/number "123"
                  :territory/addresses "the addresses"
                  :territory/region "the region"
                  :territory/meta {:foo "bar"}
                  :territory/location testdata/wkt-helsinki-rautatientori}]
   :card-minimap-viewports [testdata/wkt-helsinki]
   :qr-codes-allowed? true
   :form {:template "TerritoryCard"
          :language "en"
          :map-raster "osmhd"
          :regions #{cong-id} ; congregation boundary is shown first in the regions list
          :territories #{territory-id}}
   :mac? false})
(def minimal-model
  {:congregation {:congregation/id cong-id
                  :congregation/name "Example Congregation"
                  :congregation/location nil
                  :congregation/timezone ZoneOffset/UTC}
   :regions []
   :territories []
   :card-minimap-viewports []
   :qr-codes-allowed? true
   :form {:template "TerritoryCard"
          :language "en"
          :map-raster "osmhd"
          :regions #{cong-id} ; congregation boundary is shown first in the regions list
          :territories #{}}
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

(def no-qr-codes-model
  (replace-in default-model [:qr-codes-allowed?] true false))

(def demo-model
  (-> default-model
      (replace-in [:congregation :congregation/id] cong-id "demo")
      (replace-in [:congregation :congregation/name] "Example Congregation" "Demo Congregation")
      (replace-in [:territories 0 :congregation/id] cong-id "demo")
      (replace-in [:form :regions] #{cong-id} #{"demo"})))

(def test-minimal-events
  (flatten [{:event/type :congregation.event/congregation-created
             :congregation/id cong-id
             :congregation/name "Example Congregation"
             :congregation/schema-name "cong1_schema"}
            (congregation/admin-permissions-granted cong-id user-id)]))
(def test-events
  (concat test-minimal-events
          [{:event/type :congregation-boundary.event/congregation-boundary-defined
            :gis-change/id 42
            :congregation/id cong-id
            :congregation-boundary/id (random-uuid)
            :congregation-boundary/location testdata/wkt-helsinki}
           {:event/type :card-minimap-viewport.event/card-minimap-viewport-defined,
            :gis-change/id 42
            :congregation/id cong-id
            :card-minimap-viewport/id (random-uuid)
            :card-minimap-viewport/location testdata/wkt-helsinki}
           {:event/type :region.event/region-defined
            :gis-change/id 42
            :congregation/id cong-id
            :region/id region-id
            :region/name "the region"
            :region/location testdata/wkt-south-helsinki}
           {:event/type :territory.event/territory-defined
            :congregation/id cong-id
            :territory/id territory-id
            :territory/number "123"
            :territory/addresses "the addresses"
            :territory/region "the region"
            :territory/meta {:foo "bar"}
            :territory/location testdata/wkt-helsinki-rautatientori}]))
(def demo-events
  (concat [demo/congregation-created]
          (into [] demo/transform-gis-events test-events)))

(deftest model!-test
  (let [request {:path-params {:congregation cong-id}}]
    (testutil/with-events (concat test-events demo-events)
      (testutil/with-user-id user-id

        (testing "default"
          (is (= default-model (printouts-page/model! request))))

        (testing "every form value changed"
          (let [request (assoc request :params {:template "NeighborhoodCard"
                                                :language "fi"
                                                :map-raster "mmlTaustakartta"
                                                :regions [(str (UUID. 0 4))
                                                          (str (UUID. 0 5))]
                                                :territories [(str (UUID. 0 6))
                                                              (str (UUID. 0 7))]})]
            (is (= form-changed-model (printouts-page/model! request)))))

        (testing "no permission to share"
          (binding [dmz/*state* (permissions/revoke dmz/*state* user-id [:share-territory-link cong-id])]
            (is (= no-qr-codes-model (printouts-page/model! request)))))

        (testing "has opened a share, cannot see all territories"
          (let [user-id (random-uuid)
                share-id (random-uuid)]
            (testutil/with-user-id user-id
              (binding [dmz/*state* (share/grant-opened-shares dmz/*state* [share-id] user-id)]
                (is (thrown-match? ExceptionInfo dmz-test/access-denied
                                   (printouts-page/model! request)))))))

        (testing "demo congregation"
          (let [request {:path-params {:congregation "demo"}}]
            (is (= demo-model
                   (printouts-page/model! request)
                   (testutil/with-anonymous-user
                     (printouts-page/model! request))))))))

    (testing "minimal data"
      (testutil/with-events test-minimal-events
        (testutil/with-user-id user-id
          (is (= minimal-model (printouts-page/model! request))))))))

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
                           "Printed 2001-01-01"))))

    (testing "hides the 'QR code only' template if creating QR codes is not allowed"
      (let [template-name "QR code only"]
        (is (str/includes? (printouts-page/view default-model) template-name))
        (is (not (str/includes? (printouts-page/view no-qr-codes-model) template-name)))))))


(deftest render-qr-code-svg-test
  (let [svg (printouts-page/render-qr-code-svg "foo")]
    (is (str/includes? svg "viewBox=\"0 0 21 21\""))
    (is (str/includes? svg "M0,0h1v1h-1z M1,0h1v1h-1z"))))

(deftest generate-qr-code!-test
  (let [request {:path-params {:congregation cong-id
                               :territory territory-id}}]
    (testutil/with-events test-events
      (binding [share/generate-share-key (constantly "abcxyz")]
        (testutil/with-user-id user-id
          (with-fixtures [fake-dispatcher-fixture]

            (let [html (printouts-page/generate-qr-code! request)]
              (is (= {:command/type :share.command/create-share
                      :command/user user-id
                      :congregation/id cong-id
                      :territory/id territory-id
                      :share/type :qr-code
                      :share/key "abcxyz"}
                     (dissoc @*last-command :command/time :share/id)))
              (is (str/includes? html "<svg ")))))))))

(deftest ^:slow parallel-qr-code-generation-test
  (with-fixtures [db-fixture]
    (let [request {:path-params {:congregation cong-id
                                 :territory territory-id}}
          handler (dmz/wrap-db-connection printouts-page/generate-qr-code!)
          user-id (db/with-transaction [conn {}]
                    (user/save-user! conn "test" {}))]
      (testutil/with-events (concat test-events
                                    (congregation/admin-permissions-granted cong-id user-id))
        (testutil/with-user-id user-id
          (testing "avoids database transaction conflicts when many QR codes are generated in parallel"
            (is (every? #(str/starts-with? % "<svg")
                        (->> (repeat 10 #(handler request))
                             (mapv future-call)
                             (mapv deref)))))

          (testing "in the unlikely event of a duplicate share key, the database will guarantee for uniqueness"
            (let [internal-server-error {:type :ring.util.http-response/response
                                         :response {:status 500
                                                    :body "Internal Server Error"
                                                    :headers {}}}
                  create-dupe (dmz/wrap-db-connection
                               (fn [_]
                                 (dmz/dispatch! {:command/type :share.command/create-share
                                                 :share/id (random-uuid)
                                                 :share/key "dupe"
                                                 :share/type :link
                                                 :congregation/id cong-id
                                                 :territory/id territory-id})))]
              (create-dupe nil)
              ;; TODO: check that the database error message mentions the event_share_key_u index
              (is (thrown-match? ExceptionInfo internal-server-error
                                 (create-dupe nil))))))))))

(deftest routes-test
  (let [handler (ring/ring-handler (ring/router printouts-page/routes))
        page-path (str "/congregation/" cong-id "/printouts")]
    (testutil/with-events test-events

      (testing "all the routes are guarded by access checks"
        (testutil/with-user-id (UUID. 0 0x666)
          (is (thrown-match? ExceptionInfo dmz-test/access-denied
                             (handler {:request-method :get
                                       :uri page-path})))
          (is (thrown-match? ExceptionInfo dmz-test/access-denied
                             (handler {:request-method :get
                                       :uri (str page-path "/qr-code/" territory-id)}))))))))
