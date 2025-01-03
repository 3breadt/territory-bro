(ns territory-bro.infra.config-test
  (:require [clojure.test :refer :all]
            [territory-bro.infra.config :as config]
            [territory-bro.infra.util :refer [getx]]
            [territory-bro.test.testutil :refer [re-contains thrown-with-msg?]])
  (:import (clojure.lang ExceptionInfo)))

(deftest enrich-env-test
  (let [env (-> (config/load-config)
                (assoc :auth0-domain "example.eu.auth0.com"
                       :auth0-client-id "m14ziOMuEVgHB4LIzoLKeDXazSReXCZo"
                       :super-users "user1 user2 ac66bb30-0b9b-11ea-8d71-362b9e155667"
                       :demo-congregation "7df983b1-6be6-42a4-b3b7-75b165005b03")
                (config/enrich-env)
                (config/validate-env))]

    (testing ":jwt-issuer is https://YOUR_AUTH0_DOMAIN/"
      (is (= "https://example.eu.auth0.com/"
             (getx env :jwt-issuer))))

    (testing ":jwt-audience is the Auth0 Client ID"
      (is (= "m14ziOMuEVgHB4LIzoLKeDXazSReXCZo"
             (getx env :jwt-audience))))

    (testing ":super-users is parsed as a set"
      (is (= #{"user1" "user2" (parse-uuid "ac66bb30-0b9b-11ea-8d71-362b9e155667")}
             (getx env :super-users))))

    (testing ":demo-congregation is parsed as a UUID"
      (is (= (parse-uuid "7df983b1-6be6-42a4-b3b7-75b165005b03")
             (getx env :demo-congregation))))

    (testing ":public-url must not end in a slash"
      (is (thrown-with-msg?
           ExceptionInfo (re-contains "{:public-url (not (re-matches \"https?://.+[^/]\" \"https://example.com/\"))}")
           (-> env
               (assoc :public-url "https://example.com/")
               (config/validate-env)))))))
