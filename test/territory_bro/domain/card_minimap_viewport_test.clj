(ns territory-bro.domain.card-minimap-viewport-test
  (:require [clojure.test :refer :all]
            [territory-bro.domain.card-minimap-viewport :as card-minimap-viewport]
            [territory-bro.domain.testdata :as testdata]
            [territory-bro.events :as events]
            [territory-bro.test.testutil :as testutil :refer [re-equals thrown-with-msg? thrown?]])
  (:import (java.time Instant)
           (java.util UUID)
           (territory_bro NoPermitException ValidationException)))

(def cong-id (UUID. 0 1))
(def card-minimap-viewport-id (UUID. 0 2))
(def user-id (UUID. 0 3))
(def gis-change-id 42)
(def card-minimap-viewport-defined
  {:event/type :card-minimap-viewport.event/card-minimap-viewport-defined
   :gis-change/id gis-change-id
   :congregation/id cong-id
   :card-minimap-viewport/id card-minimap-viewport-id
   :card-minimap-viewport/location testdata/wkt-polygon})
(def card-minimap-viewport-deleted
  {:event/type :card-minimap-viewport.event/card-minimap-viewport-deleted
   :gis-change/id gis-change-id
   :congregation/id cong-id
   :card-minimap-viewport/id card-minimap-viewport-id})

(defn- apply-events [events]
  (testutil/apply-events card-minimap-viewport/projection events))

(defn- handle-command [command events injections]
  (->> (card-minimap-viewport/handle-command (testutil/validate-command command)
                                             (events/validate-events events)
                                             injections)
       (events/validate-events)))


;;;; Projection

(deftest card-minimap-viewport-projection-test
  (testing "created"
    (let [events [card-minimap-viewport-defined]
          expected {::card-minimap-viewport/card-minimap-viewports
                    {cong-id {card-minimap-viewport-id {:card-minimap-viewport/id card-minimap-viewport-id
                                                        :card-minimap-viewport/location testdata/wkt-polygon}}}}]
      (is (= expected (apply-events events)))

      (testing "> updated"
        (let [events (conj events (assoc card-minimap-viewport-defined
                                         :card-minimap-viewport/location "new location"))
              expected (assoc-in expected [::card-minimap-viewport/card-minimap-viewports cong-id card-minimap-viewport-id
                                           :card-minimap-viewport/location] "new location")]
          (is (= expected (apply-events events)))))

      (testing "> deleted"
        (let [events (conj events card-minimap-viewport-deleted)
              expected {}]
          (is (= expected (apply-events events))))))))


;;;; Queries

(deftest check-card-minimap-viewport-exists-test
  (let [state (apply-events [card-minimap-viewport-defined])]

    (testing "exists"
      (is (nil? (card-minimap-viewport/check-card-minimap-viewport-exists state cong-id card-minimap-viewport-id))))

    (testing "doesn't exist"
      (is (thrown-with-msg?
           ValidationException (re-equals "[[:no-such-card-minimap-viewport #uuid \"00000000-0000-0000-0000-000000000001\" #uuid \"00000000-0000-0000-0000-000000000666\"]]")
           (card-minimap-viewport/check-card-minimap-viewport-exists state cong-id (UUID. 0 0x666)))))))


;;;; Commands

(deftest define-card-minimap-viewport-test
  (let [injections {:check-permit (fn [_permit])}
        define-command {:command/type :card-minimap-viewport.command/define-card-minimap-viewport
                        :command/time (Instant/now)
                        :command/user user-id
                        :gis-change/id gis-change-id
                        :congregation/id cong-id
                        :card-minimap-viewport/id card-minimap-viewport-id
                        :card-minimap-viewport/location testdata/wkt-polygon}]

    (testing "created"
      (is (= [card-minimap-viewport-defined]
             (handle-command define-command [] injections))))

    (testing "location changed"
      (is (= [card-minimap-viewport-defined]
             (handle-command define-command [(assoc card-minimap-viewport-defined :card-minimap-viewport/location "old location")] injections))))

    (testing "is idempotent"
      (is (empty? (handle-command define-command [card-minimap-viewport-defined] injections))))

    (testing "checks permits"
      (let [injections {:check-permit (fn [permit]
                                        (is (= [:define-card-minimap-viewport cong-id card-minimap-viewport-id] permit))
                                        (throw (NoPermitException. nil nil)))}]
        (is (thrown? NoPermitException
                     (handle-command define-command [] injections)))))))

(deftest delete-card-minimap-viewport-test
  (let [injections {:check-permit (fn [_permit])}
        delete-command {:command/type :card-minimap-viewport.command/delete-card-minimap-viewport
                        :command/time (Instant/now)
                        :command/user user-id
                        :gis-change/id gis-change-id
                        :congregation/id cong-id
                        :card-minimap-viewport/id card-minimap-viewport-id}]

    (testing "deleted"
      (is (= [card-minimap-viewport-deleted]
             (handle-command delete-command [card-minimap-viewport-defined] injections))))

    (testing "is idempotent"
      (is (empty? (handle-command delete-command [card-minimap-viewport-defined card-minimap-viewport-deleted] injections))))

    (testing "checks permits"
      (let [injections {:check-permit (fn [permit]
                                        (is (= [:delete-card-minimap-viewport cong-id card-minimap-viewport-id] permit))
                                        (throw (NoPermitException. nil nil)))}]
        (is (thrown? NoPermitException
                     (handle-command delete-command [card-minimap-viewport-defined] injections)))))))
  