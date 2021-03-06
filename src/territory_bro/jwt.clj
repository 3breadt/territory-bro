;; Copyright © 2015-2019 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.jwt
  (:require [clojure.data.json :as json]
            [mount.core :as mount]
            [territory-bro.config :refer [env]]
            [territory-bro.util :refer [getx]])
  (:import (com.auth0.jwk JwkProviderBuilder JwkProvider)
           (com.auth0.jwt JWT JWTVerifier$BaseVerification)
           (com.auth0.jwt.algorithms Algorithm)
           (com.auth0.jwt.interfaces Clock)
           (java.nio.charset StandardCharsets)
           (java.time Instant)
           (java.util Base64 Date)))

(mount/defstate ^:dynamic ^JwkProvider jwk-provider
  :start (-> (JwkProviderBuilder. ^String (getx env :auth0-domain))
             (.build)))

(defn- fetch-public-key [^String jwt]
  (let [key-id (.getKeyId (JWT/decode jwt))]
    (.getPublicKey (.get jwk-provider key-id))))

(defn- decode-base64url [^String base64-str]
  (-> (Base64/getUrlDecoder)
      (.decode base64-str)
      (String. StandardCharsets/UTF_8)))

(defn validate [^String jwt env]
  (let [public-key (fetch-public-key jwt)
        algorithm (Algorithm/RSA256 public-key nil)
        clock (reify Clock
                (getToday [_]
                  (Date/from ((getx env :now)))))
        verifier (-> (JWT/require algorithm)
                     (.withIssuer (into-array String [(getx env :jwt-issuer)]))
                     (.withAudience (into-array String [(getx env :jwt-audience)]))
                     (->> ^JWTVerifier$BaseVerification (cast JWTVerifier$BaseVerification))
                     (.build clock))]
    (-> (.verify verifier jwt)
        (.getPayload)
        (decode-base64url)
        (json/read-str :key-fn keyword))))

(defn expired?
  ([jwt]
   (expired? jwt (Instant/now)))
  ([jwt ^Instant now]
   (< (:exp jwt) (.getEpochSecond now))))
