;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.ui.layout-test
  (:require [clojure.test :refer :all]
            [hiccup2.core :as h]
            [territory-bro.api-test :as at]
            [territory-bro.infra.authentication :as auth]
            [territory-bro.test.fixtures :refer :all]
            [territory-bro.ui.html :as html]
            [territory-bro.ui.layout :as layout])
  (:import (java.util UUID)))

(def anonymous-model
  {:title "the title"
   :congregation nil})
(def logged-in-model
  {:title "the title"
   :congregation nil})
(def congregation-model
  {:title "the title"
   :congregation {:id (UUID. 0 1)
                  :name "the congregation"}})

(deftest ^:slow model!-test
  (with-fixtures [db-fixture api-fixture]
    (let [session (at/login! at/app)
          user-id (at/get-user-id session)
          cong-id (at/create-congregation! session "the congregation")]

      (testing "top level, anonymous"
        (let [request {}]
          (is (= anonymous-model
                 (layout/model! request {:title "the title"})))))

      (testing "top level, logged in"
        (let [request {:session {::auth/user {:user/id user-id}}}]
          (is (= logged-in-model
                 (layout/model! request {:title "the title"})))))

      (testing "congregation level"
        (let [request {:params {:congregation (str cong-id)}
                       :session {::auth/user {:user/id user-id}}}]
          (is (= (assoc-in congregation-model [:congregation :id] cong-id)
                 (layout/model! request {:title "the title"}))))))))

(deftest page-test
  (testing "minimal data"
    (is (= (html/normalize-whitespace
            "Territory Bro

             🏠 Home
             User guide {fa-external-link-alt}
             News {fa-external-link-alt}
             🛟 Support

             Sorry, something went wrong 🥺
             Close")
           (html/visible-text
            (layout/page {:title nil
                          :congregation nil}
              "")))))

  (testing "top-level navigation"
    (is (= (html/normalize-whitespace
            "the title - Territory Bro

             🏠 Home
             User guide {fa-external-link-alt}
             News {fa-external-link-alt}
             🛟 Support

             Sorry, something went wrong 🥺
             Close

             the content")
           (html/visible-text
            (layout/page anonymous-model
              (h/html [:p "the content"]))))))

  ;; TODO: logged in vs anonymous

  (testing "congregation-level navigation"
    (is (= (html/normalize-whitespace
            "the title - Territory Bro

             🏠 Home
             the congregation
             📍 Territories
             🖨️ Printouts
             ⚙️ Settings
             🛟 Support

             Sorry, something went wrong 🥺
             Close

             the content")
           (html/visible-text
            (layout/page congregation-model
              (h/html [:p "the content"])))))))


;;;; Components and helpers

(deftest active-link?-test
  (testing "on home page"
    (let [current-page "/"]
      (is (true? (layout/active-link? "/" current-page)))
      (is (false? (layout/active-link? "/support" current-page)))
      (is (false? (layout/active-link? "/congregation/123" current-page)))
      (is (false? (layout/active-link? "/congregation/123/territories" current-page)))))

  (testing "on another global page"
    (let [current-page "/support"]
      (is (false? (layout/active-link? "/" current-page)))
      (is (true? (layout/active-link? "/support" current-page)))
      (is (false? (layout/active-link? "/congregation/123" current-page)))
      (is (false? (layout/active-link? "/congregation/123/territories" current-page)))))

  (testing "on congregation home page"
    (let [current-page "/congregation/123"]
      (is (false? (layout/active-link? "/" current-page)))
      (is (false? (layout/active-link? "/support" current-page)))
      (is (true? (layout/active-link? "/congregation/123" current-page)))
      (is (false? (layout/active-link? "/congregation/123/territories" current-page)))))

  (testing "on another congregation page"
    (let [current-page "/congregation/123/territories"]
      (is (false? (layout/active-link? "/" current-page)))
      (is (false? (layout/active-link? "/support" current-page)))
      (is (true? (layout/active-link? "/congregation/123" current-page)))
      (is (true? (layout/active-link? "/congregation/123/territories" current-page))))))
