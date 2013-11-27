(ns hyzhenhok.key
  (:require
   [hyzhenhok.curve :as curve]
   [hyzhenhok.util :refer :all]
   [hyzhenhok.crypto :as crypto]
   [clojure.test :refer [is]]
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

;; (set! *warn-on-reflection* false)

;; (Security/insertProviderAt (BouncyCastleProvider.) 1)

;; ;; ECPoint decompression ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; ;; TODO: Test that result is 65 bytes.

;; ;; (let [x (bytes->unum (sub-bytes pub 1 33))
;; ;;       beta (-> (* x x x)
;; ;;                (+ 7)
;; ;;                (expt (quot (inc P) 4))
;; ;;                (mod P))
;; ;;       y (if (zero? (-> (first pub)
;; ;;                        (+ beta)
;; ;;                        (mod 2)))
;; ;;           (- P beta)
;; ;;           beta)]
;; ;;   {:x x :y y})

;; ;; (ann decompress-point [BigInteger Boolean -> ECPoint$Fp])
;; ;; (defn decompress-point [x-bn y-bit]
;; ;;   (let [x (ECFieldElement$Fp. Q x-bn)
;; ;;         alpha ^ECFieldElement (.multiply x (-> (.square x)
;; ;;                                                (.add A)
;; ;;                                                (.add B)))]
;; ;;     (if-let [beta ^ECFieldElement (.sqrt alpha)]
;; ;;       (if (= y-bit (-> (.toBigInteger beta) (.testBit 0)))
;; ;;         (ECPoint$Fp. ec-curve x beta true)
;; ;;         (let [y (ECFieldElement$Fp. Q (->> (.toBigInteger beta)
;; ;;                                            (.subtract Q)))]
;; ;;           (ECPoint$Fp. ec-curve x y true)))
;; ;;       (throw (ex-info "Invalid point compression.")))))

;; ;; ECPoint compression ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; ;; - Compressed: 33 bytes
;; ;; - Uncompressed: 65 bytes

;; ;; Base58 encode/decode ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; (ann b58-alphabet String)
;; (def b58-alphabet
;;   (str "123456789"
;;        "ABCDEFGHJKLMNPQRSTUVWXYZ"
;;        "abcdefghijkmnopqrstuvwxyz"))

;; (ann b58-encode (Fn [ByteArray -> String]
;;                     [ByteArray AnyInt -> String]))
;; (def b58-encode (make-encoder b58-alphabet))

;; (ann b58-decode (Fn [String -> ByteArray]))
;; (def b58-decode (make-decoder b58-alphabet))

;; ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; ;(priv->pub (:priv (generate-pair)))

;; (ann priv->wif [BigInteger -> String])
;; (defn priv->wif
;;   "Converts priv-key into the compact Wallet Import Format.
;;    - https://en.bitcoin.it/wiki/Wallet_import_format
;;    - <1-byte version><32-byte privkey><4-byte checksum>"
;;   [priv]
;;   (let [priv-hex (num->hex priv)
;;         ver-hex "80"
;;         checksum-hex (bytes->hex
;;                       (crypto/calc-checksum
;;                        (hex->bytes (str ver-hex priv-hex))))]
;;     (let [full-hex (str ver-hex priv-hex checksum-hex)]
;;       (b58-encode (hex->bytes full-hex)))))

;; ;; If the public key/address for a particular private key
;; ;; are to be derived from the compressed encoding of the
;; ;; public key, the private key gets an extra 0x01 byte at
;; ;; the end, resulting in a base58 form that starts with
;; ;; 'K' or 'L'.
;; (ann b58check-encode (Fn [ByteArray -> String]
;;                          [ByteArray AnyInt -> String]))
;; (defn b58check-encode
;;   ([bytes] (b58check-encode bytes 0))
;;   ([bytes ver]
;;      (let [magic (unchecked-byte ver)
;;            magic+bytes (concat-bytes [magic] bytes)
;;            checksum (crypto/calc-checksum magic+bytes)
;;            magic+bytes+checksum (concat-bytes
;;                                  magic+bytes checksum)]
;;        (b58-encode magic+bytes+checksum))))

;; (ann wif->priv [String -> BigInteger])
;; (defn wif->priv
;;   "Expands WIF priv-key into BigInteger.
;;    - Asserts that computed checksum is equivalent
;;      to given checksum."
;;   [wif]
;;   (let [bytes (b58-decode wif)]
;;     (let [ver+priv (drop-last-bytes 4 bytes)
;;           checksum (take-last-bytes 4 bytes)
;;           priv (drop-bytes 1 ver+priv)]
;;       ;; Equality not implemented for byte-array.
;;       (assert (= (seq checksum) (seq (crypto/calc-checksum
;;                                       ver+priv))))
;;       (bytes->unum priv))))

;; ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; ;; ASN1/DER ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; (declare priv->pub)

;; (non-nil-returns
;;  [DLSequence size getObjectAt]
;;  [ASN1InputStream readObject new]
;;  [DEROctetString getOctets]
;;  [X9ECParameters toASN1Primitive])

;; (ann ^:no-check asn1->priv [ByteArray -> BigInteger])
;; (defn asn1->priv
;;   "Decode ASN.1 priv-key into BigInteger."
;;   [^bytes encoded-priv]
;;   (let [decoder (ASN1InputStream. encoded-priv)
;;         dl-seq ^DLSequence (.readObject decoder)]
;;     (assert (= 4 (.size dl-seq)))
;;     (assert (-> (.getValue (.getObjectAt dl-seq 0))
;;                 (.equals BigInteger/ONE)))
;;     (let [obj (.getObjectAt dl-seq 1)
;;           bits(.getOctets ^DEROctetString obj)]
;;       (.close decoder)
;;       (bytes->unum bits))))

;; (ann priv->asn1 [BigInteger -> ByteArray])
;; (defn priv->asn1
;;   "ASN.1-encodes priv-key."
;;   [priv]
;;   (let [baos (ByteArrayOutputStream. 400)]
;;     (doto (DERSequenceGenerator. baos)
;;       (.addObject (ASN1Integer. 1))
;;       (.addObject (DEROctetString. (num->bytes priv)))
;;       (.addObject (as-> (.toASN1Primitive curve/x9ec-params) _
;;                         (DERTaggedObject. 0 _)))
;;       (.addObject (as-> (priv->pub priv) _
;;                         (.getEncoded _)
;;                         (DERBitString. _)
;;                         (DERTaggedObject. 1 _)))
;;       (.close))
;;     (.toByteArray baos)))

;; ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; ;; Priv/pub transcoder;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; ;; What follows is a failed experiment in abstraction through
;; ;; meta-data + multimethod-dispatch on that meta-data.

;; ;; The original motive here was to mark priv-keys as ^:compressed
;; ;; when importing WIF and WIF-compressed priv-key formats
;; ;; so that I later knew how the btc-address had been derived
;; ;; (i.e. how to convert the priv before hashing it).

;; ;; A better solution would be to just canonicalize keys into
;; ;; WIF form and then keep them that way, converting to
;; ;; BigInteger for point-arithmetic.

;; ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; (defn determine-pub-meta [pub]
;;   (cond
;;    (and (instance? ECPoint$Fp pub)
;;         (= 65 (count (.getEncoded pub))))
;;    {:compression :uncompressed, :format :ecpoint}
;;    (and (instance? ECPoint$Fp pub)
;;         (= 33 (count (.getEncoded pub))))
;;    {:compression :compression, :format :ecpoint}
;;    (and (byte-array? pub)
;;         (= 65 (count pub)))
;;    {:compression :uncompressed, :format :bin}
;;    (and (byte-array? pub)
;;         (= 33 (count pub))
;;         (contains? #{2 3} (first pub)))
;;    {:compression :compressed, :format :bin}
;;    (and (string? pub)
;;         (= 130 (count pub))
;;         (= [\0 \4] (take 2 pub)))
;;    {:compression :uncompressed, :format :hex}
;;    (and (string? pub)
;;         (= 66 (count pub))
;;         (contains? #{"02" "03"} (str (take 2 pub))))
;;    {:compression :compressed, :format :hex}
;;    :else (throw (ex-info "Unknown pub format" {:pub pub}))))

;; (defn determine-pub
;;   "Returns a pub annotated with :compression and :format."
;;   [pub]
;;   (with-meta {:value pub} (determine-pub-meta pub)))

;; (ann decode-pub [Any -> ECPoint$Fp])
;; (defmulti decode-pub (fn [any-pub]
;;                        ((juxt :compression :format)
;;                         (meta any-pub))))

;; (defmethod decode-pub [:uncompressed :ecpoint]
;;   [{:keys [value]}]
;;   (let [x (.getX value)
;;         y (.getY value)]
;;     (ECPoint$Fp. curve/ec-curve x y)))

;; (defmethod decode-pub [:uncompressed :bin]
;;   [{:keys [value]}]
;;   (let [x (sub-bytes value 1 33)
;;         y (sub-bytes value 33 65)]
;;     (ECPoint$Fp. curve/ec-curve x y)))

;; (defmethod decode-pub [:compressed :bin]
;;   [{:keys [value]}]
;;   (let [x (bytes->unum (sub-bytes value 1 33))
;;         beta (-> (* x x x)
;;                  (+ 7)
;;                  (expt (quot (inc curve/Q) 4))
;;                  (mod curve/Q))
;;         y (if (zero? (-> (first value)
;;                          (+ beta)
;;                          (mod 2)))
;;             (- curve/Q beta)
;;             beta)]
;;     (ECPoint$Fp. curve/ec-curve x y true)))

;; (defmethod decode-pub [:uncompressed :hex]
;;   [{:keys [value] :as pub}]
;;   (-> {:value (hex->bytes value)}
;;       (with-meta {:compression :uncompressed, :format :bin})
;;       (decode-pub)))

;; (defmethod decode-pub [:compressed :hex]
;;   [{:keys [value] :as pub}]
;;   (-> {:value (hex->bytes value)}
;;       (with-meta {:compression :compressed, :format :bin})
;;       (decode-pub)))

;; (defn decode-pub-with-meta [pub]
;;   (let [compression (:compression (meta pub))]
;;     ^{:compression compression, :format :ecpoint}
;;     {:value (decode-pub pub)}))


;; (defmulti encode-pub (fn [pub format]
;;                         [(:compression (meta pub)) format]))

;; ;; Uncompressed
;; (defmethod encode-pub [:uncompressed :bin]
;;   [{:keys [value]} _]
;;   (.getEncoded value))
;; (defmethod encode-pub [:uncompressed :hex]
;;   [{:keys [value]} _]
;;   (bytes->hex (.getEncoded value)))
;; (defmethod encode-pub [:uncompressed :ecpoint]
;;   [{:keys [value]} _]
;;   value)
;; ;; Compressed
;; (defmethod encode-pub [:compressed :bin]
;;   [{:keys [value]} _]
;;   (.getEncoded value))
;; (defmethod encode-pub [:compressed :hex]
;;   [{:keys [value]} _]
;;   (bytes->hex (.getEncoded value)))
;; (defmethod encode-pub [:compressed :ecpoint]
;;   [{:keys [value]} _]
;;   value)

;; (defn decode-pub-with-meta [pub]
;;   (let [compression (:compression (meta pub))]
;;     ^{:compression compression, :format :ecpoint}
;;     {:value (decode-pub pub)}))

;; (defn encode-pub-with-meta [pub format]
;;   (let [compression (:compression (meta pub))]
;;     ^{:compression compression, :format format}
;;     {:value (encode-pub pub)}))

;; ;; :wif is 51 base58 chars starting with a "5".
;; ;; :wif-compressed is 52 base58 chars starting with "K" or "L".
;; (defn determine-priv-meta [priv]
;;   (if (instance? BigInteger priv)
;;     ;; BigIntegers are the canonical form of privs in my system
;;     ;; and I know that if a BigInteger is not already annotated
;;     ;; with meta that it originated from my system where
;;     ;; things are :compressed by default.
;;     {:compression :compressed, :format :biginteger}
;;     (case (count priv)
;;       32 {:compression :uncompressed, :format :bin}
;;       33 {:compression :compressed,   :format :bin}
;;       64 {:compression :uncompressed, :format :hex}
;;       66 {:compression :compressed,   :format :hex}
;;       51 {:compression :uncompressed, :format :wif}
;;       52 {:compression :compressed,   :format :wif}
;;       (case (take-ubytes 2 priv)
;;         [0x30 0x82] {:compression :uncompressed, :format :der}
;;         [0x30 0x81] {:compression :compressed, :format :der}
;;         (throw (ex-info "Unknown priv format" {:priv priv}))))))

;; (defn determine-priv
;;   "Returns a priv annotated with :compression and :format."
;;   [priv]
;;   (if (= #{:compression :format} (set (keys (meta priv))))
;;     priv
;;     (with-meta {:value priv} (determine-priv-meta priv))))

;; ;; Decoding ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; ;; - Decodes any priv format into canonical {:value BigInteger}
;; ;;   annotated with :compression and :format meta-data.

;; (ann decode-priv [Any -> BigInteger])
;; (defmulti decode-priv
;;   "Converts (canonicalizes) any priv into an
;;    annotated BigInteger."
;;   (fn [any-priv]
;;     ((juxt :compression :format)
;;      (meta any-priv))))

;; ;; Uncompressed
;; (defmethod decode-priv [:uncompressed :biginteger]
;;   [{:keys [value]}]
;;   value)
;; (defmethod decode-priv [:uncompressed :bin]
;;   [{:keys [value]}]
;;   (hex->unum 1 value))
;; (defmethod decode-priv [:uncompressed :hex]
;;   [{:keys [value]}]
;;   (hex->unum value))
;; (defmethod decode-priv [:uncompressed :wif]
;;   [{:keys [value]}]
;;   (wif->priv value))
;; (defmethod decode-priv [:uncompressed :der]
;;   [{:keys [value]}]
;;   (asn1->priv value))

;; ;; Compressed
;; (defmethod decode-priv [:compressed :biginteger]
;;   [{:keys [value]}]
;;   value)
;; (defmethod decode-priv [:compressed :bin]
;;   [{:keys [value]}]
;;   (bytes->unum (take-bytes 32 value)))
;; (defmethod decode-priv [:compressed :hex]
;;   [{:keys [value]}]
;;   (hex->unum (take-string 64 value)))
;; (defmethod decode-priv [:compressed :wif]
;;   [{:keys [value]}]
;;   (wif->priv value))
;; (defmethod decode-priv [:compressed :der]
;;   [{:keys [value]}]
;;   (asn1->priv value))

;; ;; Encoding;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; ;; - Encodes canonical {:value BigInteger} into another format
;; ;;   annotated with new meta-data. :compression is preserved
;; ;;   but it'll have a new :format.

;; (declare encode-priv-with-meta)

;; (defmulti encode-priv (fn [priv format]
;;                         [(:compression (meta priv)) format]))

;; ;; Uncompressed
;; (defmethod encode-priv [:uncompressed :biginteger]
;;   [{:keys [value]} _]
;;   value)
;; (defmethod encode-priv [:uncompressed :bin]
;;   [{:keys [value]} _]
;;   (num->bytes value))
;; (defmethod encode-priv [:uncompressed :hex]
;;   [{:keys [value]} _]
;;   (num->hex value))
;; (defmethod encode-priv [:uncompressed :wif]
;;   [{:keys [value]} _]
;;   (priv->wif value))
;; (defmethod encode-priv [:uncompressed :der]
;;   [{:keys [value]} _]
;;   (priv->asn1 value))

;; ;; Compressed
;; (defmethod encode-priv [:compressed :biginteger]
;;   [{:keys [value]} _]
;;   value)
;; (defmethod encode-priv [:compressed :bin]
;;   [{:keys [value]} _]
;;   (concat-bytes (biginteger->bytes value 32) [(byte 1)]))
;; (defmethod encode-priv [:compressed :hex]
;;   [{:keys [value]} _]
;;   (num->hex value))
;; (defmethod encode-priv [:compressed :wif]
;;   [priv _]
;;   (-> (encode-priv-with-meta priv :bin)
;;       :value (b58check-encode 0x80)))
;; (defmethod encode-priv [:compressed :der]
;;   [{:keys [value]} _]
;;   (priv->asn1 value))

;; (defn decode-priv-with-meta [priv]
;;   (let [compression (:compression (meta priv))]
;;     ^{:compression compression, :format :biginteger}
;;     {:value (decode-priv priv)}))

;; (defn encode-priv-with-meta [priv format]
;;   (let [compression (:compression (meta priv))]
;;     ^{:compression compression, :format format}
;;     {:value (encode-priv priv)}))

;; ;; Transcoder
;; (defn transcode-priv [any-priv format]
;;   (let [into-priv (fn [val] {:value val})
;;         priv (determine-priv any-priv)
;;         compression (:compression (meta priv))]
;;     (-> (decode-priv-with-meta priv)
;;         (encode-priv format)
;;         (into-priv)
;;         (with-meta {:compression compression, :format format}))))

;; (defn transcode-pub [any-pub format]
;;   (let [into-pub (fn [val] {:value val})
;;         pub (determine-pub any-pub)
;;         compression (:compression (meta pub))]
;;     (-> (decode-pub-with-meta pub)
;;         (encode-pub format)
;;         (into-pub)
;;         (with-meta {:compression compression, :format format}))))

;; ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; ;; Key pair generation ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; (ann ^:no-check generate-pair [-> '{:priv BigInteger
;;                                         :pub ByteArray}])
;; (defn generate-pair
;;   "Generates fresh private/public key-pair where
;;    - pub-key is compressed."
;;   []
;;   (let [gen-params (ECKeyGenerationParameters.
;;                     curve/ec-params (SecureRandom.))
;;         gen (doto (ECKeyPairGenerator.) (.init gen-params))
;;         pair (.generateKeyPair gen)]
;;     (let [priv (.getD ^ECPrivateKeyParameters (.getPrivate pair))
;;           pub (.getQ ^ECPublicKeyParameters (.getPublic pair))]
;;       {:priv (determine-priv priv)
;;        :pub (transcode-pub pub :bin)})))

;; (ann priv->pub [BigInteger -> ECPoint$Fp])
;; (defn priv->pub
;;   "Calculates pub-key (uncompressed) from priv-key."
;;   [^BigInteger priv]
;;   (.multiply ^ECPoint$Fp curve/G priv))

;; (ann priv->address [BigInteger -> String])
;; (defn priv->address
;;   "Converts a priv-key into a Bitcoin address."
;;   [^BigInteger priv]
;;   (let [ver (byte-array [(byte 0x00)])
;;         pub (priv->pub priv)]
;;     (let [hash160 (crypto/hash160 (.getEncoded pub))
;;           ver+hash160 (concat-bytes ver hash160)
;;           checksum (crypto/calc-checksum ver+hash160)]
;;       (let [ver+hash160+checksum (concat-bytes ver+hash160
;;                                                checksum)]
;;         (b58-encode ver+hash160+checksum)))))

;; ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; ;; Signature ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; ;; - ASN.1/DER code mostly uses BitcoinJ's implementation.

;; (non-nil-returns
;;  [DERInteger getPositiveValue]
;;  [DLSequence getObjectAt])

;; (def-alias SigMap
;;   '{:r BigInteger
;;     :s BigInteger})

;; ;; Tracks BitcoinJ's ECDSASignature#DecodeFromDER()
;; (ann ^:no-check der->sigmap [ByteArray -> SigMap])
;; (defn der->sigmap [^bytes bytes]
;;   (let [decoder (ASN1InputStream. bytes)
;;         dl-seq ^DLSequence (.readObject decoder)
;;         r ^DERInteger (.getObjectAt dl-seq 0)
;;         s ^DERInteger (.getObjectAt dl-seq 1)]
;;     (.close decoder)
;;     {:r (.getPositiveValue r)
;;      :s (.getPositiveValue s)}))

;; ;; Tracks BitcoinJ's ECDSASignature#derByteStream()
;; (ann sigmap->der-stream [SigMap -> ByteArrayOutputStream])
;; (defn sigmap->der-stream [{:keys [^BigInteger r ^BigInteger s]}]
;;   (let [stream (ByteArrayOutputStream. 72)]
;;     (doto (DERSequenceGenerator. stream)
;;       (.addObject (DERInteger. r))
;;       (.addObject (DERInteger. s))
;;       (.close))
;;     stream))

;; (ann sigmap->der [SigMap -> ByteArray])
;; (defn sigmap->der [sigmap]
;;   (.toByteArray (sigmap->der-stream sigmap)))

;; (ann canonical [SigMap -> Boolean])
;; (defn canonical? [{:keys [s]}]
;;   (<= s (/ curve/N 2)))

;; (ann canonicalize [SigMap -> SigMap])
;; (defn canonicalize [{:keys [r s] :as sig}]
;;   (if (> s (/ curve/N 2))
;;     {:s (biginteger (- curve/N s)), :r r}
;;     sig))

;; (non-nil-returns
;;  [ECDSASigner generateSignature])

;; (ann sign [ByteArray BigInteger -> SigMap])
;; (defn sign [data priv]
;;   (let [signer (ECDSASigner.)
;;         priv-params (ECPrivateKeyParameters.
;;                      (:value (transcode-priv priv :biginteger))
;;                      curve/ec-params)]
;;     (.init signer true priv-params)
;;     (let [components (ann-form (.generateSignature signer data)
;;                                (Array BigInteger))
;;           sig {:r ^BigInteger (aget components 0),
;;                :s ^BigInteger (aget components 1)}]
;;       (canonicalize sig))))

;; ;; Sig is ASN.1-encoded

;; (non-nil-returns
;;  [ECCurve decodePoint])

;; (ann verify (Fn [ByteArray SigMap ByteArray -> Boolean]
;;                 [ByteArray ByteArray ByteArray -> Boolean]))

;; (defn verify [data sig ^bytes pub]
;;   (let [signer (ECDSASigner.)
;;         pub-params (ECPublicKeyParameters.
;;                     (.decodePoint curve/ec-curve pub)
;;                     curve/ec-params)]
;;     (.init signer false pub-params)
;;     (let [sig' (ann-form (if (map? sig)
;;                            sig
;;                            (der->sigmap sig))
;;                          SigMap)]
;;       (.verifySignature signer data (:r sig') (:s sig')))))
