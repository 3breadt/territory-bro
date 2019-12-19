;; Copyright © 2015-2019 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.todo-tracker-test
  (:require [clojure.test :refer :all]
            [territory-bro.todo-tracker :as todo-tracker]))

(deftest state-test
  (let [simplify (fn [m]
                   (-> m
                       (todo-tracker/inspect ::test :the-key)
                       (select-keys [:state])))]
    (testing "default"
      (is (= {:state nil}
             (-> {}
                 (simplify)))))
    (testing "add state"
      (is (= {:state {:foo 1
                      :bar 2}}
             (-> {}
                 (todo-tracker/merge-state ::test :the-key {:foo 1
                                                            :bar 2})
                 (simplify)))))
    (testing "merge state"
      (is (= {:state {:foo 1
                      :bar 3
                      :gazonk 4}}
             (-> {}
                 (todo-tracker/merge-state ::test :the-key {:foo 1
                                                            :bar 2})
                 (todo-tracker/merge-state ::test :the-key {:bar 3
                                                            :gazonk 4})
                 (simplify)))))
    (testing "isolated keyspaces"
      (is (= {:state {:foo 1}}
             (-> {}
                 (todo-tracker/merge-state ::test :the-key {:foo 1})
                 (todo-tracker/merge-state ::unrelated :the-key {:bar 2})
                 (simplify)))))))

(deftest desired-test
  (let [simplify (fn [m]
                   (-> m
                       (todo-tracker/inspect ::test :the-key)
                       (select-keys [:desired])))]
    (testing "default"
      (is (= {:desired :absent}
             (-> {}
                 (simplify)))))
    (testing "set"
      (is (= {:desired :present}
             (-> {}
                 (todo-tracker/set-desired ::test :the-key :present)
                 (simplify))))
      (is (= {:desired :absent}
             (-> {}
                 (todo-tracker/set-desired ::test :the-key :absent)
                 (simplify)))))
    (testing "isolated keyspaces"
      (is (= {:desired :present}
             (-> {}
                 (todo-tracker/set-desired ::test :the-key :present)
                 (todo-tracker/set-desired ::unrelated :the-key :absent)
                 (simplify)))))))

(deftest actual-test
  (let [simplify (fn [m]
                   (-> m
                       (todo-tracker/inspect ::test :the-key)
                       (select-keys [:actual])))]
    (testing "default"
      (is (= {:actual :absent}
             (-> {}
                 (simplify)))))
    (testing "set"
      (is (= {:actual :present}
             (-> {}
                 (todo-tracker/set-actual ::test :the-key :present)
                 (simplify))))
      (is (= {:actual :absent}
             (-> {}
                 (todo-tracker/set-actual ::test :the-key :absent)
                 (simplify)))))
    (testing "isolated keyspaces"
      (is (= {:actual :present}
             (-> {}
                 (todo-tracker/set-actual ::test :the-key :present)
                 (todo-tracker/set-actual ::unrelated :the-key :absent)
                 (simplify)))))))

(deftest action-test
  (let [simplify (fn [m]
                   (-> m
                       (todo-tracker/inspect ::test :the-key)
                       (select-keys [:action])))]
    (testing "absent absent -> ignore"
      (is (= {:action :ignore}
             (-> {}
                 (todo-tracker/set-desired ::test :the-key :absent)
                 (todo-tracker/set-actual ::test :the-key :absent)
                 (simplify)))))
    (testing "present absent -> create"
      (is (= {:action :create}
             (-> {}
                 (todo-tracker/set-desired ::test :the-key :present)
                 (todo-tracker/set-actual ::test :the-key :absent)
                 (simplify)))))
    (testing "absent present -> delete"
      (is (= {:action :delete}
             (-> {}
                 (todo-tracker/set-desired ::test :the-key :absent)
                 (todo-tracker/set-actual ::test :the-key :present)
                 (simplify)))))
    (testing "present present -> ignore"
      (is (= {:action :ignore}
             (-> {}
                 (todo-tracker/set-desired ::test :the-key :present)
                 (todo-tracker/set-actual ::test :the-key :present)
                 (simplify)))))))

(deftest creatable-test
  (testing "default"
    (is (= []
           (-> {}
               (todo-tracker/creatable ::test)))))
  (testing "want to create"
    (is (= [{:foo 1}]
           (-> {}
               (todo-tracker/merge-state ::test :the-key {:foo 1})
               (todo-tracker/set-desired ::test :the-key :present)
               (todo-tracker/creatable ::test)))))
  (testing "wanted to create, but changed mind"
    (is (= []
           (-> {}
               (todo-tracker/merge-state ::test :the-key {:foo 1})
               (todo-tracker/set-desired ::test :the-key :present)
               (todo-tracker/set-desired ::test :the-key :absent)
               (todo-tracker/creatable ::test)))))
  (testing "was created"
    (is (= []
           (-> {}
               (todo-tracker/merge-state ::test :the-key {:foo 1})
               (todo-tracker/set-desired ::test :the-key :present)
               (todo-tracker/set-actual ::test :the-key :present)
               (todo-tracker/creatable ::test)))))
  (testing "isolated keyspaces"
    (is (= [{:foo 1}]
           (-> {}
               (todo-tracker/merge-state ::test :the-key {:foo 1})
               (todo-tracker/set-desired ::test :the-key :present)
               (todo-tracker/set-desired ::unrelated :the-key :absent)
               (todo-tracker/creatable ::test))))))

(deftest deletable-test
  (testing "default"
    (is (= []
           (-> {}
               (todo-tracker/deletable ::test)))))
  (testing "unexpectedly present"
    (is (= [{:foo 1}]
           (-> {}
               (todo-tracker/merge-state ::test :the-key {:foo 1})
               (todo-tracker/set-actual ::test :the-key :present)
               (todo-tracker/deletable ::test)))))
  (testing "want to delete"
    (is (= [{:foo 1}]
           (-> {}
               (todo-tracker/merge-state ::test :the-key {:foo 1})
               (todo-tracker/set-actual ::test :the-key :present)
               (todo-tracker/set-desired ::test :the-key :absent)
               (todo-tracker/deletable ::test)))))
  (testing "wanted to delete, but changed mind"
    (is (= []
           (-> {}
               (todo-tracker/merge-state ::test :the-key {:foo 1})
               (todo-tracker/set-actual ::test :the-key :present)
               (todo-tracker/set-desired ::test :the-key :absent)
               (todo-tracker/set-desired ::test :the-key :present)
               (todo-tracker/deletable ::test)))))
  (testing "was deleted"
    (is (= []
           (-> {}
               (todo-tracker/merge-state ::test :the-key {:foo 1})
               (todo-tracker/set-actual ::test :the-key :present)
               (todo-tracker/set-desired ::test :the-key :absent)
               (todo-tracker/set-actual ::test :the-key :absent)
               (todo-tracker/deletable ::test)))))
  (testing "isolated keyspaces"
    (is (= [{:foo 1}]
           (-> {}
               (todo-tracker/merge-state ::test :the-key {:foo 1})
               (todo-tracker/set-actual ::test :the-key :present)
               (todo-tracker/set-actual ::unrelated :the-key :absent)
               (todo-tracker/deletable ::test))))))
