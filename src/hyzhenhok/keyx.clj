(ns hyzhenhok.keyx
  (:require
   [hyzhenhok.curve :as curve]
   [hyzhenhok.util :refer :all]
   [hyzhenhok.crypto :as crypto]
   [gloss.io :refer :all
    :exclude [contiguous encode decode]]
   [gloss.core :refer :all
    :exclude [byte-count]]
   [clojure.math.numeric-tower]
   [potemkin :refer [import-fn import-vars]]
   [clojure.string :as str]
   [clojure.core.typed :refer :all])
  (:import
   [org.apache.commons.codec.binary
    Hex]
   [org.bouncycastle.crypto.signers
    ECDSASigner]
   [clojure.lang
    BigInt
    IPersistentMap
    IPersistentVector
    Keyword
    Ratio]
   [org.bouncycastle.crypto
    AsymmetricCipherKeyPair]
   [org.bouncycastle.math.ec
    ECCurve
    ECCurve$Fp
    ECFieldElement
    ECFieldElement$Fp
    ECPoint
    ECPoint$Fp]
   [org.bouncycastle.asn1.x9
    X9ECParameters]
   [org.bouncycastle.asn1.sec
    SECNamedCurves]
   [org.bouncycastle.jce.spec
    ECParameterSpec
    ECPrivateKeySpec
    ECPublicKeySpec]
   [java.io
    ByteArrayOutputStream]
   [org.bouncycastle.asn1
    ASN1InputStream
    ASN1Integer
    ASN1OctetString
    ASN1Primitive
    DERBitString
    DERInteger
    DEROctetString
    DERSequenceGenerator
    DERTaggedObject
    DLSequence]
   [java.security
    KeyFactory
    KeyPairGenerator
    MessageDigest
    SecureRandom
    Security
    Signature]
   [javax.crypto
    Mac
    SecretKey]
   [javax.crypto.spec
    SecretKeySpec]
   [org.bouncycastle.crypto.generators
    ECKeyPairGenerator]
   [org.bouncycastle.crypto.params
    AsymmetricKeyParameter
    ECDomainParameters
    ECKeyGenerationParameters
    ECPrivateKeyParameters
    ECPublicKeyParameters]
   [org.bouncycastle.jce.provider
    BouncyCastleProvider]))

(set! *warn-on-reflection* false)

(Security/insertProviderAt (BouncyCastleProvider.) 1)

;; This namespace will be the successor to hyzhenhok.key.
;; - I'm growing this namespace organically as I need it.

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; TODO: Relocate somewhere central.
(def networks
  {:mainnet {:address/ver (byte 0x00)}
   :testnet3 {:address/ver (byte 0x6f)}})

;; Move to util
(defn modpow
  "Efficient `x` to the power of `y` modulo `z`."
  [x y z]
  (bigint (.modPow (.toBigInteger x) (.toBigInteger y) z)))

(potemkin/def-derived-map Point [^ECPoint$Fp point]
  :base point
  :x (bigint (.toBigInteger (.getX point)))
  :y (bigint (.toBigInteger (.getY point)))
  :bytes (.getEncoded point))

(defn ->ECFieldElement [n]
  (ECFieldElement$Fp. curve/Q (biginteger n)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Base58 encode/decode ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(let [base58-alphabet (str "123456789"
                           "ABCDEFGHJKLMNPQRSTUVWXYZ"
                           "abcdefghijkmnopqrstuvwxyz")]

  (def base58-encode (make-encoder base58-alphabet))

  (def base58-decode (make-decoder base58-alphabet)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(declare compress compressed? ->privkey ->pubkey)

(defn get-pubkey-format [pub]
  (cond
   (instance? Point pub) (if (compressed? (:bytes pub))
                           :point-compressed
                           :point-uncompressed)
   ;; :bin-compressed
   (and (byte-array? pub)
        (= 33 (count pub))
        (contains? #{2 3} (first pub)))
   :bin-compressed
   ;; :bin-uncompressed
   (and (byte-array? pub)
        (= 65 (count pub)))
   :bin-uncompressed
   ;; :hex-compressed
   (and (string? pub)
        (= 66 (count pub))
        (contains? #{"02" "03"} (str/join (take 2 pub))))
   :hex-compressed
   ;; :hex-uncompressed
   (and (string? pub)
        (= 130 (count pub))
        (= [\0 \4] (take 2 pub)))
   :hex-uncompressed
   :else (throw (ex-info "Unknown pubkey format" {:pub pub}))))

;; FIXME: Avoid repetition with get-pubkey-format.
(defn pubkey? [key]
  (or (instance? Point key)
      ;; :bin-compressed
      (and (byte-array? key)
           (= 33 (count key))
           (contains? #{2 3} (first key)))
      ;; :bin-uncompressed
      (and (byte-array? key)
           (= 65 (count key)))
      ;; :hex-compressed
      (and (string? key)
           (= 66 (count key))
           (contains? #{"02" "03"} (str/join (take 2 key))))
      ;; :hex-uncompressed
      (and (string? key)
           (= 130 (count key))
           (= [\0 \4] (take 2 key)))))

(def privkey? (complement pubkey?))

(defn ->pubkey [key]
  (if (privkey? key)
    (let [privkey (->privkey key)
          uncompressed-pubkey (->> (biginteger privkey)
                                   (.multiply curve/G)
                                   ->Point)]
      (if (compressed? key)
        (compress uncompressed-pubkey)
        uncompressed-pubkey))
    (let [pub key  ; Then we know it's a pubkey
          format (get-pubkey-format pub)]
      (case format
        :hex-compressed (->pubkey (hex->bytes pub))
        :hex-uncompressed (->pubkey (hex->bytes pub))
        :bin-uncompressed (let [x (-> (sub-bytes pub 1 33)
                                      bytes->unum)
                                y (-> (sub-bytes pub 33 65)
                                      bytes->unum)]
                            (-> (ECPoint$Fp. curve/ec-curve
                                             (->ECFieldElement x)
                                             (->ECFieldElement y))
                                ->Point))
        :bin-compressed
        (let [x (bytes->unum (sub-bytes pub 1 33))
              beta (modpow (-> (* x x x) (+ 7))
                           (quot (inc curve/Q) 4)
                           curve/Q)
              y  (if (zero? (-> (first pub) (+ beta) (mod 2)))
                   beta
                   (- curve/Q beta))]
          (->Point (ECPoint$Fp. curve/ec-curve
                                (->ECFieldElement x)
                                (->ECFieldElement y)
                                true)))))))

(defn get-privkey-format [priv]
  (if (integer? priv)
    :decimal-compressed
    (if (byte-array? priv)
      ;; Byte-array
      (case (count priv)
        32 :bin-uncompressed
        33 :bin-compressed
        51 :wif-bin-uncompressed
        52 :wif-bin-compressed
        (case (take-ubytes 2 priv)
          [0x30 0x82] :der-bin-uncompressed
          [0x30 0x81] :der-bin-compressed
          (throw (ex-info "Unknown privkey format"
                          {:priv priv}))))
      ;; String
      (case (count priv)
        64 :hex-uncompressed
        66 :hex-compressed
        51 :wif-str-uncompressed
        52 :wif-str-compressed
        (case (str/join (take 4 priv))
          "3082" :der-hex-uncompressed
          "3081" :der-hex-compressed
          (throw (ex-info "Unknown privkey format"
                          {:priv priv})))))))

(defn compressed? [key]
  (let [format (if (pubkey? key)
                 (get-pubkey-format key)
                 (get-privkey-format key))]
    (boolean (re-find #"-compressed" (name format)))))

(defn compress [point]
  (-> (ECPoint$Fp. curve/ec-curve
                   (->ECFieldElement (:x point))
                   (->ECFieldElement (:y point))
                   true)
      ->Point))

;; (ann wif->priv [String -> BigInteger])
(defn wif->priv
  "Expands WIF priv-key into BigInteger.
   - Asserts that computed checksum is equivalent
     to given checksum."
  [wif]
  (let [bytes (base58-decode wif)]
    (let [ver+priv (drop-last-bytes 4 bytes)
          checksum (take-last-bytes 4 bytes)
          priv (drop-bytes 1 ver+priv)]
      ;; Equality not implemented for byte-array.
      (assert (= (seq checksum) (seq (crypto/calc-checksum
                                      ver+priv))))
      (bytes->unum priv))))

;; (ann ^:no-check asn1->priv [ByteArray -> BigInteger])
(defn der->priv
  "Decode ASN.1 priv-key into BigInteger."
  [^bytes encoded-priv]
  (let [decoder (ASN1InputStream. encoded-priv)
        dl-seq ^DLSequence (.readObject decoder)]
    (assert (= 4 (.size dl-seq)))
    (assert (-> (.getValue (.getObjectAt dl-seq 0))
                (.equals BigInteger/ONE)))
    (let [obj (.getObjectAt dl-seq 1)
          bits(.getOctets ^DEROctetString obj)]
      (.close decoder)
      (bytes->unum bits))))

(defn ->privkey [priv]
  (case (get-privkey-format priv)
    :decimal-compressed (bigint priv)
    :hex-compressed (hex->unum priv)
    :hex-uncompressed (hex->unum priv)
    ;:wif-str-compressed (wif->priv priv)
    ;:wif-bin-compressed (wif->priv (str/join (map char priv)))
    :der-hex-compressed (->privkey (hex->bytes priv))
    :der-bin-compressed (der->priv priv)))

(defn ->address
  ([key] (->address key :mainnet))
  ([key network]
     (let [pub (cond
                (and (pubkey? key) (instance? Point key)) key
                ;; Might already be hash160 pubkey
                (and (byte-array? key) (= 20 (count key))) key
                :else (->pubkey key))
           ;; pub (if (pubkey? key)
           ;;       key
           ;;       (->pubkey key))
           ver (byte-array [(-> networks network :address/ver)])
           hash160 (if (= 20 (count pub)) ;; Dont do this again
                     pub
                     (crypto/hash160 (:bytes pub)))
           ver+hash160 (concat-bytes ver hash160)
           checksum (crypto/calc-checksum ver+hash160)
           ver+hash160+checksum (concat-bytes ver+hash160
                                              checksum)]
       (base58-encode ver+hash160+checksum))))

(defn generate-pair
  "Generates fresh private/public key-pair where
   - pub-key is compressed."
  []
  (let [gen-params (ECKeyGenerationParameters.
                    curve/ec-params (SecureRandom.))
        gen (doto (ECKeyPairGenerator.) (.init gen-params))
        pair (.generateKeyPair gen)]
    (let [priv (.getD ^ECPrivateKeyParameters (.getPrivate pair))
          pub (.getQ ^ECPublicKeyParameters (.getPublic pair))]
      {:privkey (bigint priv)
       :pubkey (compress (->Point pub))})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Signature (Move to hyzhenhok.sig)
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Either HexString or ByteArray
(defn der->sigmap [data]
  (let [bytes (if (byte-array? data) data (hex->bytes data))
        decoder (ASN1InputStream. bytes)
        dl-seq ^DLSequence (.readObject decoder)
        r ^DERInteger (.getObjectAt dl-seq 0)
        s ^DERInteger (.getObjectAt dl-seq 1)]
    (.close decoder)
    {:r (.getPositiveValue r)
     :s (.getPositiveValue s)}))

(defn canonicalize [{:keys [r s] :as sig}]
  (if (> s (/ curve/N 2))
    {:s (biginteger (- curve/N s)), :r r}
    sig))

(defn sign [data privkey]
  (let [signer (ECDSASigner.)
        priv-params (ECPrivateKeyParameters.
                     (biginteger (->privkey privkey))
                     curve/ec-params)]
    (.init signer true priv-params)
    (let [components  (.generateSignature signer data)
          sig {:r ^BigInteger (aget components 0),
               :s ^BigInteger (aget components 1)}]
      (canonicalize sig))))

;; (ann verify (Fn [ByteArray SigMap ByteArray -> Boolean]
;;                 [ByteArray ByteArray ByteArray -> Boolean]))
(defn verify [data sig ^bytes pub]
  (let [signer (ECDSASigner.)
        pub-params (ECPublicKeyParameters.
                    (.decodePoint curve/ec-curve pub)
                    curve/ec-params)]
    (.init signer false pub-params)
    (let [sig' (if (map? sig)
                 sig
                 (der->sigmap sig))]
      (.verifySignature signer data (:r sig') (:s sig')))))
