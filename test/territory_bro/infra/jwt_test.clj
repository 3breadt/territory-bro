(ns territory-bro.infra.jwt-test
  (:require [clojure.string :as str]
            [clojure.test :refer :all]
            [territory-bro.infra.jwt :as jwt]
            [territory-bro.test.fixtures :refer :all]
            [territory-bro.test.testutil :refer [thrown-with-msg?]])
  (:import (com.auth0.jwk Jwk JwkProvider)
           (com.auth0.jwt.exceptions InvalidClaimException SignatureVerificationException TokenExpiredException)
           (java.time Instant)
           (java.util Base64 Map)))

;; key cached from https://luontola.eu.auth0.com/.well-known/jwks.json
(def jwk {"alg" "RS256",
          "kty" "RSA",
          "use" "sig",
          "x5c" ["MIIC8jCCAdqgAwIBAgIJHoFouif+0twQMA0GCSqGSIb3DQEBBQUAMCAxHjAcBgNVBAMTFWx1b250b2xhLmV1LmF1dGgwLmNvbTAeFw0xNjA5MTYwODM4MjhaFw0zMDA1MjYwODM4MjhaMCAxHjAcBgNVBAMTFWx1b250b2xhLmV1LmF1dGgwLmNvbTCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBAOAvQieNzD29VsOdQc3YHPzpLkNkShpeMuFYB76WHRb6UQpUBAKSEVpxvu1G0DG2shMJ+DObsQ81ID+WFYW445Dz6sJE4dRGmSx9oEGPB7kiDGZx1bb2O14n6v17/qzz2PHgCT05BIU+AmrpN5GNZdnJya0jU4r0UQInDRD5/qZwUF8oXfcG7eewcYLak7ZwsjA1Kf4HADkMIZo8NZ+9TtvN2cToPzPtlGSInsjW7oZP1m/qO4xvEyAQUtj11QV8so9F5NPyd9h5PYlo5t792I4bOUykpck1KR81RUJuZ3HLt5104JNFYcEe2tjnt9DtBAXfMvMtdiJZ85BRE9XJ9NMCAwEAAaMvMC0wDAYDVR0TBAUwAwEB/zAdBgNVHQ4EFgQUj8UVIFeOuo6D0UE3eogA7Ht623cwDQYJKoZIhvcNAQEFBQADggEBAJfJ5yi3Akh9pGD+PMiN0AqzT9kJr6g6Z3EZJ0qP6iiGZLsMBSDeulpjWMOeYvbW0OCKJI8X+YidXAlxOGNyIxkyu8IXJ7E5Z+DSg4H9D6YG26VQKqKsorhZ2YxRsckagaMEYqH7KIKesS5iiK32ULR5iV5+NdBGafoNLNwBxX6Pge1f2QskJJy22vWlh9NA2jmBbCIl5OzNxEouMn34jCnq/F+zg0fDEAOM9ZdcsjXRMT3a2Dta7L4G9bnkX8a9gGe6cRcqINeaIMY4/Jpp6Lb6t1lvWYG+TbhWAoeHl3ZfqjNm4cnnvoNAkiVLC73rC7SHhzzyKDwZS8p31QtEB1E="],
          "n" "4C9CJ43MPb1Ww51Bzdgc_OkuQ2RKGl4y4VgHvpYdFvpRClQEApIRWnG-7UbQMbayEwn4M5uxDzUgP5YVhbjjkPPqwkTh1EaZLH2gQY8HuSIMZnHVtvY7Xifq_Xv-rPPY8eAJPTkEhT4Cauk3kY1l2cnJrSNTivRRAicNEPn-pnBQXyhd9wbt57BxgtqTtnCyMDUp_gcAOQwhmjw1n71O283ZxOg_M-2UZIieyNbuhk_Wb-o7jG8TIBBS2PXVBXyyj0Xk0_J32Hk9iWjm3v3Yjhs5TKSlyTUpHzVFQm5nccu3nXTgk0VhwR7a2Oe30O0EBd8y8y12IlnzkFET1cn00w",
          "e" "AQAB",
          "kid" "RjY1MzA3NTJGRkM1QTkyNUZFMTk3NkU2OTcwQUEwRjEzMjRCQTBCNA",
          "x5t" "RjY1MzA3NTJGRkM1QTkyNUZFMTk3NkU2OTcwQUEwRjEzMjRCQTBCNA"})

(def token "eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiIsImtpZCI6IlJqWTFNekEzTlRKR1JrTTFRVGt5TlVaRk1UazNOa1UyT1Rjd1FVRXdSakV6TWpSQ1FUQkNOQSJ9.eyJnaXZlbl9uYW1lIjoiRXNrbyIsImZhbWlseV9uYW1lIjoiTHVvbnRvbGEiLCJuaWNrbmFtZSI6ImVza28ubHVvbnRvbGEiLCJuYW1lIjoiRXNrbyBMdW9udG9sYSIsInBpY3R1cmUiOiJodHRwczovL2xoNi5nb29nbGV1c2VyY29udGVudC5jb20vLUFtRHYtVlZoUUJVL0FBQUFBQUFBQUFJL0FBQUFBQUFBQWVJL2JIUDhsVk5ZMWFBL3Bob3RvLmpwZyIsImxvY2FsZSI6ImVuLUdCIiwidXBkYXRlZF9hdCI6IjIwMTgtMTEtMDNUMTY6MTY6NDcuNTAyWiIsImlzcyI6Imh0dHBzOi8vbHVvbnRvbGEuZXUuYXV0aDAuY29tLyIsInN1YiI6Imdvb2dsZS1vYXV0aDJ8MTAyODgzMjM3Nzk0NDUxMTExNDU5IiwiYXVkIjoiOHRWa2Rmbnc4eW5aNnJYTm5kRDZlWjZFcnNIZElnUGkiLCJpYXQiOjE1NDEyNjE4MDcsImV4cCI6MTU0MTI5NzgwNywibm9uY2UiOiI5UGlFemxtTDk3d0xudTFBLVVPakZ-VFAyekVYU3dvLSJ9.v3avK87uM7ncT3Bx8aJ7NbbCaOjgv_TQ9lRR6hs6CTFteA3yhbZpX0isB3_2Lxf46AsqVEWWFvY-Afslc_32_UzLfaWEPH5HwQwRAwUW9m34tx-RYhNtP02jFAmJIZG-akhz0TYlEzcblU1tOKJbLFuVHyRAOWKRSvlJXioVDfqEdsApNAI78-aoEjhf3ouLzDQVl15AfPBP8Czmp2wmwRfD_2ES66e-_q7cm9zzkcWTjub0wLmiNhDCQfnZJxfA9r5XUQLThbUFHHPnSx-QfLqP8tXmMLm9B9BV8J7G1humG8gaCycq_Q-9ieSDAjpvZ8C5ePTNLVOga4j-MaaFtA")

(def env {:jwt-issuer "https://luontola.eu.auth0.com/"
          :jwt-audience "8tVkdfnw8ynZ6rXNndD6eZ6ErsHdIgPi"})
(def test-time (Instant/parse "2018-11-03T16:17:00.000Z"))

(def fake-jwk-provider
  (reify JwkProvider
    (get [_ _]
      (let [m (.getDeclaredMethod Jwk "fromValues" (into-array Class [Map]))]
        (.setAccessible m true)
        (.invoke m nil (into-array [jwk]))))))

(defn- base64-url-decode ^bytes [^String s]
  (.decode (Base64/getUrlDecoder) s))

(defn- base64-url-encode ^String [^bytes bs]
  (.encodeToString (Base64/getUrlEncoder) bs))

(defn- mutate-jwt-payload [token mutation]
  (let [[header payload signature] (str/split token #"\.")
        mutated-payload (-> payload
                            (base64-url-decode)
                            (String.)
                            ^String (mutation)
                            (.getBytes)
                            (base64-url-encode))]
    (str header "." mutated-payload "." signature)))

(use-fixtures :once (fixed-clock-fixture test-time))


(deftest jwt-validate-test
  (binding [jwt/jwk-provider fake-jwk-provider]
    (testing "decodes valid tokens"
      (is (= {:name "Esko Luontola"}
             (select-keys (jwt/validate token env) [:name]))))

    ;; What needs to be validated from a JWT token
    ;; https://auth0.com/docs/tokens/id-token#validate-an-id-token

    (testing "verifies token signature"
      (let [mutated-token (mutate-jwt-payload token #(str/replace % "Esko" "Matti"))]
        (is (thrown-with-msg? SignatureVerificationException #"The Token's Signature resulted invalid"
                              (jwt/validate mutated-token env)))))

    (testing "validates token expiration"
      (with-fixtures [(fixed-clock-fixture (Instant/parse "2018-11-04T16:17:00.000Z"))]
        (is (thrown-with-msg? TokenExpiredException #"The Token has expired"
                              (jwt/validate token env)))))

    (testing "validates token issuer"
      (is (thrown-with-msg? InvalidClaimException #"The Claim 'iss' value doesn't match the required issuer."
                            (jwt/validate token (assoc env :jwt-issuer "x")))))

    (testing "validates token audience"
      (is (thrown-with-msg? InvalidClaimException #"The Claim 'aud' value doesn't contain the required audience."
                            (jwt/validate token (assoc env :jwt-audience "x")))))))

(deftest jwt-expired-test
  (testing "expire time in past"
    (is (true? (jwt/expired? {:exp 1})))
    (is (true? (jwt/expired? {:exp 1530000000} (Instant/ofEpochSecond 1540000000)))))
  (testing "expire time now"
    (is (false? (jwt/expired? {:exp 1540000000} (Instant/ofEpochSecond 1540000000)))))
  (testing "expire time in future"
    (is (false? (jwt/expired? {:exp Integer/MAX_VALUE})))
    (is (false? (jwt/expired? {:exp 1550000000} (Instant/ofEpochSecond 1540000000))))))
