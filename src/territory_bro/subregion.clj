;; Copyright © 2015-2020 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.subregion
  (:require [medley.core :refer [dissoc-in]]))

;;;; Read model

(defmulti projection (fn [_state event]
                       (:event/type event)))

(defmethod projection :default [state _event]
  state)

(defmethod projection :subregion.event/subregion-defined
  [state event]
  (update-in state [::subregions (:congregation/id event) (:subregion/id event)]
             (fn [subregion]
               (-> subregion
                   (assoc :subregion/id (:subregion/id event))
                   (assoc :subregion/name (:subregion/name event))
                   (assoc :subregion/location (:subregion/location event))))))

(defmethod projection :subregion.event/subregion-deleted
  [state event]
  (dissoc-in state [::subregions (:congregation/id event) (:subregion/id event)]))


;;;; Write model

(defn- write-model [events]
  (let [[{cong-id :congregation/id, subregion-id :subregion/id}] events]
    (-> (reduce projection nil events)
        (get-in [::subregions cong-id subregion-id]))))


;;;; Command handlers

(defmulti ^:private command-handler (fn [command _subregion _injections]
                                      (:command/type command)))

(defmethod command-handler :subregion.command/create-subregion
  [command subregion {:keys [check-permit]}]
  (let [cong-id (:congregation/id command)
        subregion-id (:subregion/id command)]
    (check-permit [:create-subregion cong-id])
    (when (nil? subregion)
      [{:event/type :subregion.event/subregion-defined
        :event/version 1
        :congregation/id cong-id
        :subregion/id subregion-id
        :subregion/name (:subregion/name command)
        :subregion/location (:subregion/location command)}])))

(defmethod command-handler :subregion.command/update-subregion
  [command subregion {:keys [check-permit]}]
  (let [cong-id (:congregation/id command)
        subregion-id (:subregion/id command)
        old-vals (select-keys subregion [:subregion/name :subregion/location])
        new-vals (select-keys command [:subregion/name :subregion/location])]
    (check-permit [:update-subregion cong-id subregion-id])
    (when (not= old-vals new-vals)
      [{:event/type :subregion.event/subregion-defined
        :event/version 1
        :congregation/id cong-id
        :subregion/id subregion-id
        :subregion/name (:subregion/name command)
        :subregion/location (:subregion/location command)}])))

(defmethod command-handler :subregion.command/delete-subregion
  [command subregion {:keys [check-permit]}]
  (let [cong-id (:congregation/id command)
        subregion-id (:subregion/id command)]
    (check-permit [:delete-subregion cong-id subregion-id])
    (when (some? subregion)
      [{:event/type :subregion.event/subregion-deleted
        :event/version 1
        :congregation/id cong-id
        :subregion/id subregion-id}])))

(defn handle-command [command events injections]
  (command-handler command (write-model events) injections))