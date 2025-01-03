(ns territory-bro.ui.support-page-test
  (:require [clojure.test :refer :all]
            [territory-bro.infra.config :as config]
            [territory-bro.test.fixtures :refer :all]
            [territory-bro.test.testutil :as testutil :refer [replace-in]]
            [territory-bro.ui.html :as html]
            [territory-bro.ui.support-page :as support-page]))

(def private-model
  {:support-email "support@example.com"})
(def public-model
  {:support-email nil})

(deftest model!-test
  (let [user-id (random-uuid)
        request {}]
    (binding [config/env {:support-email "support@example.com"}]
      (testutil/with-user-id user-id

        (testing "logged in"
          (is (= private-model (support-page/model! request))))

        (testing "anonymous"
          (testutil/with-anonymous-user
            (is (= public-model (support-page/model! request)))))

        (testing "no support email configured"
          (binding [config/env (replace-in config/env [:support-email] "support@example.com" nil)]
            (is (= public-model (support-page/model! request)))))))))

(deftest view-test
  (testing "default"
    (is (= (html/normalize-whitespace
            "Support
             Territory Bro is an open source project developed by Esko Luontola.
             We recommend subscribing to our mailing list to be notified about important Territory Bro updates.
             The user guide should answer the most common questions related to creating territory maps.
             If that is not enough, you may email support@example.com to ask for help with using Territory Bro.
             See the translation instructions if you would like to help improve the current translations or add new languages.
             Bugs and feature requests may also be reported to this project's issue tracker.
             Privacy policy")
           (-> (support-page/view private-model)
               html/visible-text))))

  (testing "anonymous"
    (is (= (html/normalize-whitespace
            "Support
             Territory Bro is an open source project developed by Esko Luontola.
             We recommend subscribing to our mailing list to be notified about important Territory Bro updates.
             The user guide should answer the most common questions related to creating territory maps.
             See the translation instructions if you would like to help improve the current translations or add new languages.
             Bugs and feature requests may also be reported to this project's issue tracker.
             Privacy policy")
           (-> (support-page/view public-model)
               html/visible-text)))))
