(ns territory-bro.infra.permissions-test
  (:require [clojure.test :refer :all]
            [territory-bro.infra.permissions :as permissions]
            [territory-bro.test.testutil :refer [re-contains thrown-with-msg? thrown?]])
  (:import (java.util UUID)
           (territory_bro NoPermitException)))

(deftest changing-permissions-test
  (let [user-id (UUID. 0 1)
        user-id2 (UUID. 0 2)
        cong-id (UUID. 0 10)
        cong-id2 (UUID. 0 20)
        resource-id (UUID. 0 100)]

    (testing "granting,"
      (testing "one permission"
        (is (= {::permissions/permissions {user-id {cong-id {:foo true}}}}
               (-> nil
                   (permissions/grant user-id [:foo cong-id])))))

      (testing "many permissions"
        (is (= {::permissions/permissions {user-id {cong-id {:foo true
                                                             :bar true}}}}
               (-> nil
                   (permissions/grant user-id [:foo cong-id])
                   (permissions/grant user-id [:bar cong-id])))))

      (testing "many congregations"
        (is (= {::permissions/permissions {user-id {cong-id {:foo true}
                                                    cong-id2 {:foo true}}}}
               (-> nil
                   (permissions/grant user-id [:foo cong-id])
                   (permissions/grant user-id [:foo cong-id2])))))

      (testing "many users"
        (is (= {::permissions/permissions {user-id {cong-id {:foo true}}
                                           user-id2 {cong-id {:foo true}}}}
               (-> nil
                   (permissions/grant user-id [:foo cong-id])
                   (permissions/grant user-id2 [:foo cong-id])))))

      (testing "narrow permits"
        (is (= {::permissions/permissions {user-id {cong-id {:foo true
                                                             resource-id {:bar true}
                                                             :gazonk true}}}}
               (-> nil
                   (permissions/grant user-id [:foo cong-id])
                   (permissions/grant user-id [:bar cong-id resource-id])
                   (permissions/grant user-id [:gazonk cong-id])))))

      (testing "super user"
        (is (= {::permissions/permissions {user-id {:foo true}}}
               (-> nil
                   (permissions/grant user-id [:foo]))))))

    (testing "revoking,"
      (testing "some permissions"
        (is (= {::permissions/permissions {user-id {cong-id {:bar true}}}}
               (-> nil
                   (permissions/grant user-id [:foo cong-id])
                   (permissions/grant user-id [:bar cong-id])
                   (permissions/revoke user-id [:foo cong-id])))))

      (testing "some congregations"
        (is (= {::permissions/permissions {user-id {cong-id2 {:foo true}}}}
               (-> nil
                   (permissions/grant user-id [:foo cong-id])
                   (permissions/grant user-id [:foo cong-id2])
                   (permissions/revoke user-id [:foo cong-id])))))

      (testing "some users"
        (is (= {::permissions/permissions {user-id2 {cong-id {:foo true}}}}
               (-> nil
                   (permissions/grant user-id [:foo cong-id])
                   (permissions/grant user-id2 [:foo cong-id])
                   (permissions/revoke user-id [:foo cong-id])))))

      (testing "narrow permits"
        (is (= {::permissions/permissions {user-id {cong-id {:bar true}}}}
               (-> nil
                   (permissions/grant user-id [:foo cong-id resource-id])
                   (permissions/grant user-id [:bar cong-id])
                   (permissions/revoke user-id [:foo cong-id resource-id])))))

      (testing "super user"
        (is (= {::permissions/permissions {user-id {:bar true}}}
               (-> nil
                   (permissions/grant user-id [:foo])
                   (permissions/grant user-id [:bar])
                   (permissions/revoke user-id [:foo])))))

      (testing "all permits"
        (is (= {}
               (-> nil
                   (permissions/grant user-id [:foo cong-id])
                   (permissions/revoke user-id [:foo cong-id]))))))

    (testing "error: empty permit"
      (is (thrown-with-msg? AssertionError (re-contains "{:permit []}")
                            (permissions/grant nil user-id [])))
      (is (thrown-with-msg? AssertionError (re-contains "{:permit nil}")
                            (permissions/grant nil user-id nil))))))

(deftest checking-permissions-test
  (let [user-id (UUID. 0 1)
        user-id2 (UUID. 0 2)
        cong-id (UUID. 0 10)
        cong-id2 (UUID. 0 20)
        resource-id (UUID. 0 100)]

    (let [state (permissions/grant nil user-id [:foo cong-id])]
      (testing "exact permit"
        (is (true? (permissions/allowed? state user-id [:foo cong-id]))))

      (testing "different permission"
        (is (false? (permissions/allowed? state user-id [:bar cong-id]))))

      (testing "different congregation"
        (is (false? (permissions/allowed? state user-id [:foo cong-id2]))))

      (testing "different user"
        (is (false? (permissions/allowed? state user-id2 [:foo cong-id])))))

    (testing "broad permit implies narrower permits"
      (is (true? (-> nil
                     (permissions/grant user-id [:foo cong-id])
                     (permissions/allowed? user-id [:foo cong-id resource-id]))))
      (is (true? (-> nil
                     (permissions/grant user-id [:foo])
                     (permissions/allowed? user-id [:foo cong-id resource-id])))))

    (testing "narrow permit doesn't imply broader permits"
      (is (false? (-> nil
                      (permissions/grant user-id [:foo cong-id resource-id])
                      (permissions/allowed? user-id [:foo cong-id]))))
      (is (false? (-> nil
                      (permissions/grant user-id [:foo cong-id resource-id])
                      (permissions/allowed? user-id [:foo])))))

    (testing "checker"
      (let [state (-> nil
                      (permissions/grant user-id [:foo cong-id]))]
        (testing "is silent when user has the permit"
          (is (nil? (permissions/check state user-id [:foo cong-id]))))

        (testing "throws when user doesn't have the permit"
          (is (thrown? NoPermitException
                       (permissions/check state user-id [:bar cong-id]))))))

    (testing "error: nil parameters"
      (is (thrown-with-msg? AssertionError (re-contains "{:permit [nil]}")
                            (permissions/allowed? nil user-id [nil])))
      (is (thrown-with-msg? AssertionError (re-contains "{:permit [:foo nil]}")
                            (permissions/allowed? nil user-id [:foo nil])))
      (is (thrown-with-msg? AssertionError (re-contains "{:permit [:foo nil #uuid \"00000000-0000-0000-0000-000000000064\"]}")
                            (permissions/allowed? nil user-id [:foo nil resource-id])))
      (is (thrown-with-msg? AssertionError (re-contains "{:permit [:foo #uuid \"00000000-0000-0000-0000-00000000000a\" nil]}")
                            (permissions/allowed? nil user-id [:foo cong-id nil]))))

    (testing "error: empty permit" ; due to recursion it's not an error, but it should at least be always false
      (is (false? (permissions/allowed? nil user-id nil)))
      (is (false? (permissions/allowed? nil user-id []))))))

(deftest listing-permissions-test
  (let [user-id (UUID. 0 1)
        user-id2 (UUID. 0 2)
        cong-id (UUID. 0 10)
        cong-id2 (UUID. 0 20)
        resource-id (UUID. 0 100)]

    (let [state (-> nil
                    (permissions/grant user-id [:foo cong-id])
                    (permissions/grant user-id [:bar cong-id])
                    (permissions/grant user-id [:gazonk cong-id resource-id]))]
      (testing "congregation-level permissions"
        (is (= #{:foo :bar} (permissions/list-permissions state user-id [cong-id]))))

      (testing "resource-level permissions"
        (is (= #{:foo :bar :gazonk} (permissions/list-permissions state user-id [cong-id resource-id]))))

      (testing "different congregation"
        (is (= #{} (permissions/list-permissions state user-id [cong-id2]))))

      (testing "different user"
        (is (= #{} (permissions/list-permissions state user-id2 [cong-id]))))

      (testing "super user"
        (let [state (permissions/grant state user-id [:admin])]
          (is (= #{:admin} (permissions/list-permissions state user-id [])))
          (is (= #{:foo :bar :admin} (permissions/list-permissions state user-id [cong-id]))))))))

(deftest finding-permissions-test
  (let [user-id (UUID. 0 0x1)
        user-id2 (UUID. 0 0x2)
        cong-id (UUID. 0 0x10)
        cong-id2 (UUID. 0 0x20)
        resource-id (UUID. 0 0x100)
        resource-id2 (UUID. 0 0x200)]

    (let [state (-> nil
                    (permissions/grant user-id [:foo cong-id])
                    (permissions/grant user-id [:foo cong-id2])
                    (permissions/grant user-id [:bar cong-id resource-id])
                    (permissions/grant user-id [:bar cong-id resource-id2])
                    (permissions/grant user-id [:bar cong-id2 resource-id])
                    (permissions/grant user-id [:bar cong-id2 resource-id2])
                    (permissions/grant user-id2 [:foo cong-id]))]
      (testing "not found"
        (is (= []
               (permissions/match state user-id [:foo (UUID. 0 0x666)])))
        (is (= []
               (permissions/match state user-id [:gazonk cong-id]))))

      (testing "exact match"
        (is (= [[:foo cong-id]]
               (permissions/match state user-id [:foo cong-id])))
        (is (= [[:bar cong-id resource-id]]
               (permissions/match state user-id [:bar cong-id resource-id]))))

      (testing "resource wildcards"
        (is (= [[:foo cong-id]
                [:foo cong-id2]]
               (permissions/match state user-id [:foo '*])))
        (is (= [[:bar cong-id resource-id]
                [:bar cong-id resource-id2]]
               (permissions/match state user-id [:bar cong-id '*])))
        (is (= [[:bar cong-id resource-id]
                [:bar cong-id2 resource-id]]
               (permissions/match state user-id [:bar '* resource-id])))
        (is (= [[:bar cong-id resource-id]
                [:bar cong-id resource-id2]
                [:bar cong-id2 resource-id]
                [:bar cong-id2 resource-id2]]
               (permissions/match state user-id [:bar '* '*]))))

      (testing "permission wildcards")))) ; TODO
