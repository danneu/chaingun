(ns chaingun.crypto
  (:require
   [chaingun.util :refer :all]
   [clojure.core.typed :refer :all])
  (:import
   [java.security
    MessageDigest
    Security]
   [org.bouncycastle.jce.provider
    BouncyCastleProvider]))

(Security/insertProviderAt (BouncyCastleProvider.) 1)

(non-nil-returns
 [MessageDigest getInstance])

(ann sha256 [ByteArray -> ByteArray])
(defn sha256 [data]
  (-> (MessageDigest/getInstance "SHA-256" "BC")
      (.digest data)))

(ann rmd160 [ByteArray -> ByteArray])
(defn rmd160 [data]
  (-> (MessageDigest/getInstance "RIPEMD160" "BC")
      (.digest data)))

(ann hash256 [ByteArray -> ByteArray])
(defn hash256
  "SHA256 twice. (Satoshi-client colloquialism)"
  [data]
  (sha256 (sha256 data)))
(def double-sha256 hash256)

(ann hash160 [ByteArray -> ByteArray])
(defn hash160
  "SHA256 then RIPEMD160. (Satoshi-client colloquialism)"
  [data]
  (rmd160 (sha256 data)))

(ann calc-checksum [ByteArray -> ByteArray])
(defn calc-checksum
  [data]
  (byte-array (take 4 (hash256 data))))
