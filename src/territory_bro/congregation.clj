;; Copyright © 2015-2019 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.congregation
  (:require [clojure.string :as str]
            [clojure.tools.logging :as log]
            [mount.core :as mount]
            [territory-bro.config :as config]
            [territory-bro.db :as db]
            [territory-bro.event-store :as event-store]
            [territory-bro.events :as events])
  (:import (java.util UUID)))

(defmulti ^:private update-congregation (fn [_congregation event] (:event/type event)))

(defmethod update-congregation :default [congregation _event]
  congregation)

(defmethod update-congregation :congregation.event/congregation-created
  [congregation event]
  (-> congregation
      (assoc :congregation/id (:congregation/id event))
      (assoc :congregation/name (:congregation/name event))
      (assoc :congregation/schema-name (:congregation/schema-name event))))

(defmethod update-congregation :congregation.event/permission-granted
  [congregation event]
  (-> congregation
      (update-in [:congregation/user-permissions (:user/id event)]
                 (fnil conj #{})
                 (:permission/id event))))

(defmethod update-congregation :congregation.event/permission-revoked
  [congregation event]
  (-> congregation
      ;; TODO: remove user when no more permissions remain
      (update-in [:congregation/user-permissions (:user/id event)]
                 disj
                 (:permission/id event))))

(defn congregations-view [congregations event]
  (update congregations (:congregation/id event) update-congregation event))


(def ^:private query! (db/compile-queries "db/hugsql/congregation.sql"))

(defn- format-congregation [row]
  {:congregation/id (:id row)
   :congregation/name (:name row)
   :congregation/schema-name (:schema_name row)})

(defn get-unrestricted-congregations
  ([conn]
   (get-unrestricted-congregations conn {}))
  ([conn search]
   (->> (query! conn :get-congregations search)
        (map format-congregation)
        (doall))))

(defn get-unrestricted-congregation [conn cong-id]
  (first (get-unrestricted-congregations conn {:ids [cong-id]})))

(defn get-my-congregations ; TODO: remove me
  ([conn user-id]
   (get-my-congregations conn user-id {}))
  ([conn user-id search]
   (get-unrestricted-congregations conn (assoc search
                                               :user user-id))))

(defn get-my-congregations2 [state user-id]
  (->> state
       (filter (fn [[_cong-id cong]]
                 (let [permissions (get-in cong [:congregation/user-permissions user-id])]
                   (contains? permissions :view-congregation))))))

(defn get-my-congregation2 [state cong-id user-id]
  (let [cong (get state cong-id)
        permissions (get-in cong [:congregation/user-permissions user-id])]
    (when (contains? permissions :view-congregation)
      cong)))

(defn get-my-congregation [conn cong-id user-id] ; TODO: remove me
  (first (get-my-congregations conn user-id {:ids [cong-id]})))

(defn use-schema [conn cong-id] ; TODO: create a better helper?
  (let [cong (get-unrestricted-congregation conn cong-id)]
    (db/use-tenant-schema conn (:congregation/schema-name cong))))

(defn create-congregation! [conn name]
  (let [id (UUID/randomUUID)
        master-schema (:database-schema config/env)
        tenant-schema (str master-schema
                           "_"
                           (str/replace (str id) "-" ""))]
    (assert (not (contains? (set (db/get-schemas conn))
                            tenant-schema))
            {:schema-name tenant-schema})
    (event-store/save! conn id 0 [(assoc (events/defaults)
                                         :event/type :congregation.event/congregation-created
                                         :congregation/id id
                                         :congregation/name name
                                         :congregation/schema-name tenant-schema)])
    (query! conn :create-congregation {:id id
                                       :name name
                                       :schema_name tenant-schema})
    (-> (db/tenant-schema tenant-schema master-schema)
        (.migrate))
    (log/info "Congregation created:" id)
    id))

;;;; User access

(defn get-users [conn cong-id] ; TODO: remove me
  (->> (query! conn :get-users {:congregation cong-id})
       (map :user)
       (doall)))

(defn get-users2 [state cong-id]
  (let [cong (get state cong-id)]
    (->> (:congregation/user-permissions cong)
         ;; TODO: remove old users already in the projection
         (filter (fn [[_user-id permissions]]
                   (not (empty? permissions))))
         (keys))))

(defn grant-access! [conn cong-id user-id]
  (event-store/save! conn cong-id nil
                     [(assoc (events/defaults)
                             :event/type :congregation.event/permission-granted
                             :congregation/id cong-id
                             :user/id user-id
                             :permission/id :view-congregation)])
  (query! conn :grant-access {:congregation cong-id
                              :user user-id})
  nil)

(defn revoke-access! [conn cong-id user-id]
  (event-store/save! conn cong-id nil
                     [(assoc (events/defaults)
                             :event/type :congregation.event/permission-revoked
                             :congregation/id cong-id
                             :user/id user-id
                             :permission/id :view-congregation)])
  (query! conn :revoke-access {:congregation cong-id
                               :user user-id})
  nil)


(mount/defstate cache
  :start (atom {:last-event nil
                :state nil}))

(defn update-cache! [conn]
  (let [cached @cache
        new-events (event-store/read-all-events conn {:since (:event/global-revision (:last-event cached))})]
    (when-let [last-event (last new-events)]
      (log/info "Updating with" (count new-events) "new events")
      (let [updated {:last-event last-event
                     :state (reduce congregations-view (:state cached) new-events)}]
        ;; with concurrent requests, only one of them will update the cache
        (compare-and-set! cache cached updated)))))

(comment
  (count (:state @cache))
  (update-cache! db/database))
