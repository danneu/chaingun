(ns hyzhenhok.util
  (:require
   [clojure.math.numeric-tower]
   [potemkin :refer [import-fn import-vars]]
   [clojure.string :as str]
   [gloss.io]
   [gloss.core]
   [clojure.core.typed :refer :all])
  (:import
   [clojure.lang BigInt Ratio IPersistentMap IPersistentVector]
   [java.net InetAddress]
   [java.util Date]
   [org.apache.commons.codec.binary Hex]
   [java.util Arrays Random]
   [java.security SecureRandom]
   [java.nio ByteBuffer]))

;; Global types

(def-alias HexString
  String)

(ann ^:no-check clojure.core/unchecked-byte [Any -> byte])

;; Math ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(tc-ignore
 (import-vars
  [clojure.math.numeric-tower
   abs
   ceil
   exact-integer-sqrt
   expt
   floor
   gcd
   integer-length
   lcm
   round
   sqrt]))

;; Core.typed ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defmacro non-nil-returns
  "More convenient way to declare non-nil-return."
  [& things]
  `(do
     ~@(for [[klass & methods] things
             :let [fullklass (.getCanonicalName
                              ^Class (resolve klass))]
             method methods]
         (let [fullthing (str fullklass "/" method)]
           `(non-nil-return ~(symbol fullthing) :all)))))

(def-alias ByteArray (Array byte))
(def-alias AnyInt AnyInteger)

;; Overrides

(ann ^:no-check
  expt [BigInt AnyInt -> AnyInt])
(ann ^:no-check
  clojure.core/mod [AnyInt AnyInt -> AnyInt])
(ann ^:no-check
  clojure.core/biginteger [Number -> BigInteger])
(ann ^:no-check
  clojure.core/drop [AnyInt (Seqable Any) -> (Seqable Any)])
(ann ^:no-check
  clojure.core/drop-last [AnyInt (Seq Any) -> (Seqable Any)])
(ann ^:no-check
  clojure.core/take-last [AnyInt (Seqable Any) -> (Seqable Any)])

(override-method
 clojure.lang.Numbers/multiply [AnyInt * -> AnyInt])

(non-nil-returns
 [Hex encodeHexString]
 [clojure.lang.IFn invokePrim])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(ann rand-insecure-big [Integer -> BigInteger])
(defn rand-insecure-big [bits]
  (BigInteger. ^int bits (SecureRandom.)))

(ann rand-secure-big [Integer -> BigInteger])
(defn rand-secure-big [bits]
  (BigInteger. ^int bits (Random.)))

(ann generate-nonce [Integer -> BigInteger])
(defn generate-nonce
  "Some nonces are uint32 (32 bits),
   some are uint64 (64 bits)."
  [bits]
  (rand-insecure-big ^int bits))

(ann ^:no-check gloss.core/byte-count
     (Fn [ByteBuffer -> AnyInt]
         [(Seqable ByteBuffer) -> AnyInt]))
(ann byte-count (Fn [ByteBuffer -> AnyInt]
                    [(Seqable ByteBuffer) -> AnyInt]))
(def byte-count gloss.core/byte-count)

(ann ^:no-check
  gloss.io/contiguous (Fn [ByteBuffer -> ByteBuffer]
                          [(Seqable ByteBuffer) -> ByteBuffer]))
(ann contiguous (Fn [ByteBuffer -> ByteBuffer]
                    [(Seqable ByteBuffer) -> ByteBuffer]))
(def ^{:doc "Unify a seq of ByteBuffers"}
  contiguous
  gloss.io/contiguous)

(ann str->bytes [String -> ByteArray])
(defn str->bytes [^String s]
  (.getBytes s))

(ann hex->bytes [String -> ByteArray])
(defn hex->bytes ^bytes [hex]
  (Hex/decodeHex (char-array (seq hex))))

(ann ubyte [byte -> AnyInt])
(defn ubyte [byte]
  (bit-and 0xff byte))

(ann into-byte-array [(Seqable AnyInt) -> (Array byte)])
(defn into-byte-array [coll]
  (into-array Byte/TYPE (map unchecked-byte coll)))

(ann hex->ubytes [String -> (Seqable AnyInt)])
(defn hex->ubytes [hex]
  (map ubyte (hex->bytes hex)))

(ann bytes->ubytes [ByteArray -> (Seqable AnyInt)])
(defn bytes->ubytes [bytes]
  (map ubyte bytes))

(ann hex->unum [String -> BigInteger])
(defn hex->unum
  "Converts hex string to unsigned number of arbitrary size.
   Pass in a String or Collection of hex pairs."
  [^String hex]
  (BigInteger. 1 (hex->bytes hex)))

(ann bytes->unum [ByteArray -> BigInteger])
(defn bytes->unum
  "Converts bytes (BE) to unsigned BigInteger."
  [^bytes bytes]
  (BigInteger. 1 bytes))

(ann coerce-unformattable (Fn [BigInt -> BigInteger]
                              [Ratio -> Double]
                              [Any -> Any]))
(defn coerce-unformattable
  "Coerce unformattable Clojure number-types to
   formattable Java types."
  [x]
  (cond (instance? BigInt x) (biginteger x)
        (instance? Ratio x) (double x)
        :else x))

(ann num->hex (Fn [AnyInt -> String]
                  [AnyInt AnyInt -> String]))
(defn num->hex
  ([n]
     (let [hex (num->hex n 1)]
       (if (odd? (count hex))
         (str \0 hex)
         hex)))
  ([n pad]
     (format (str "%0" pad "x") (coerce-unformattable n))))

(ann num->bytes [AnyInteger -> ByteArray])
(defn num->bytes ^bytes [n]
  (hex->bytes (num->hex n)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(override-method
 clojure.lang.Numbers/max (Fn [AnyInt * -> AnyInt]))

(ann bytes->hex (Fn [ByteArray -> HexString]
                    [ByteArray AnyInt -> HexString]))

(defn bytes->hex
  ([bytes] (bytes->hex bytes 0))
  ([bytes min-length]
     (let [hex (Hex/encodeHexString bytes)
           pad-length (max 0 (- min-length (count hex)))]
       (str (str/join (repeat pad-length \0)) hex))))

(ann ubytes->hex [(Seqable Integer) -> HexString])
(defn ubytes->hex [ubytes]
  (bytes->hex (byte-array (map unchecked-byte ubytes))))

(ann ubytes->bytes [(Seqable Integer) -> ByteArray])
(defn ubytes->bytes [ubytes]
  (byte-array (map unchecked-byte ubytes)))

(ann ubytes->unum [(Seqable Integer) -> AnyInt])
(defn ubytes->unum [ubytes]
  (bytes->unum (ubytes->bytes ubytes)))

(ann drop-bytes [AnyInt ByteArray -> ByteArray])
(defn drop-bytes [n bytes]
  (byte-array (drop n bytes)))

(ann drop-last-bytes [AnyInt ByteArray -> ByteArray])
(defn drop-last-bytes [n bytes]
  (byte-array (drop-last n bytes)))

(ann concat-bytes [(U ByteArray (Seq byte)) *
                   -> ByteArray])
(defn concat-bytes [& bytes]
  (byte-array (apply concat bytes)))

(ann take-bytes [AnyInt (Seqable byte) -> ByteArray])
(defn take-bytes [n bytes]
  (byte-array (take n bytes)))

(ann
  take-last-bytes [AnyInt (Seqable byte) -> ByteArray])
(defn take-last-bytes [n bytes]
  (byte-array (take-last n bytes)))

(ann pad-bytes [AnyInt ByteArray -> ByteArray])
(defn pad-bytes [n bytes]
  (let [padding (byte-array (- n (count bytes)))]
    (concat-bytes padding bytes)))

(ann reverse-bytes [(Seqable byte) -> ByteArray])
(defn reverse-bytes [bytes]
  (byte-array (reverse bytes)))

(ann biginteger->bytes [BigInteger AnyInt -> ByteArray])
(defn biginteger->bytes
  "`to-byte-count` is the desired size of output.
   Useful for ensuring no BigInteger sign-byte.
   Ex: (biginteger->bytes n 32)"
  [^BigInteger n to-byte-count]
  (let [nbytes (.toByteArray n)]
    ;; If n has 1 more byte than to-byte-count, then it's the
    ;; extra sign byte that .toByteArray can add and we remove it.
    (if (= (count nbytes) (inc to-byte-count))
      (drop-bytes 1 nbytes)
      nbytes)))

(ann take-string [AnyInt String -> String])
(defn take-string [n string]
  (str/join (take n string)))

(ann take-ubytes [AnyInt ByteArray -> (Seqable AnyInt)])
(defn take-ubytes [n bytes]
  (map ubyte (take-bytes n bytes)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(ann generate-timestamp [-> AnyInt])
(defn generate-timestamp
  "Seconds since epoch."
  []
  (quot (.getTime (Date.)) 1000))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(non-nil-returns
 [InetAddress getByAddress getHostAddress getByName])

(ann bytes->ip [ByteArray -> String])
(defn bytes->ip [bytes]
  (.getHostAddress
   (InetAddress/getByAddress bytes)))

(ann ip->bytes [String -> ByteArray])
(defn ip->bytes
  "(ip->hex \"98.200.229.32\") -> [62 c8 e5 20]"
  [ip-string]
  (.getAddress (InetAddress/getByName ip-string)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(non-nil-returns
 [ByteBuffer wrap])

(ann buf->bytes (Fn [ByteBuffer -> ByteArray]
                    [(Seqable ByteBuffer) -> ByteArray]))
(defn buf->bytes
  "Extract a unified byte-array from a seq of ByteBuffers."
  [buf]
  (if (= 0 (byte-count buf))
    (byte-array 0)
    (let [buf' (contiguous buf)
          rem (.remaining ^ByteBuffer buf')
          b (byte-array rem)]
      (.get ^ByteBuffer buf' b)
      b)))

(ann buf->hex (Fn [ByteBuffer -> String]
                  [(Seqable ByteBuffer) -> String]))
(defn buf->hex
  "Hex string of unified byte-array."
  [buf]
  (bytes->hex (buf->bytes buf)))

(ann ^:no-check byte-array? [Any -> Boolean])
(defn byte-array? [thing]
  (instance? (Class/forName "[B") thing))

(ann biginteger? [Any -> Boolean])
(defn biginteger? [thing]
  (instance? BigInteger thing))

(ann sub-bytes [ByteArray AnyInt AnyInt -> ByteArray])
(defn sub-bytes [src-array start end]
  (let [length (- end start)
        dst-array (byte-array length)]
    (System/arraycopy
     src-array (int start) dst-array 0 (int length))
    dst-array))

(ann make-encoder
     (Fn [String -> (Fn
                     [ByteArray AnyInt -> String]
                     [ByteArray -> String])]
         [String AnyInt -> (Fn
                            [ByteArray AnyInt -> String]
                            [ByteArray -> String])]))
(defn make-encoder
  "Returns an encoder function that encodes strings using
   the given alphabet as the code-string.
   - `min-length-init` can be provided to this constructor
     to set the minimum length of encoded output. It will
     pad shortfall with the first char of the alphabet.
     * Defaults to 0 (no padding)
   - However, it can be overridden a la cart via the second
     arg of the returned encoder function.

     Ex:
     ;; Construct a decimal encoder that pads to 4 chars.
     (def base10-encode (make-encoder \"0123456789\" 4))
     (base10-encode (num->bytes 42))    ;=> \"0042\"
     ;; Override min-length this time:
     (base10-encode (num->bytes 42) 6)  ;=> \"000042\""
  ([alphabet]
     (make-encoder alphabet 0))
  ([alphabet min-length-init]
     (let [radix (count alphabet)]
       (fn> encoder
         (:- String [bytes :- ByteArray]
           (encoder bytes (or min-length-init 0)))
         (:- String [bytes :- ByteArray, min-length :- AnyInt]
           (let [;; Count leading 0s so they can be reapplied
                 ;; later. Converting bytes to a number will
                 ;; drop that information.
                 leading0s (count
                            (take-while (partial = 0) bytes))]
             (let [output
                   (if (= leading0s (count bytes))
                     ""
                     (loop> [output :- String, ""
                             n :- AnyInt, (bytes->unum bytes)]
                       (if (>= n radix)
                         (let [remainder (mod n radix)
                               c (nth alphabet remainder)]
                           (recur (str c output) (quot n radix)))
                         (str (nth alphabet n) output))))]
               (let [;; Re-attach leading0s
                     leading0-prefix (str/join
                                      (repeat
                                       leading0s
                                       (first alphabet)))
                     pre-padded (str leading0-prefix output)
                     pad-length (- min-length
                                   (count pre-padded))]
                 ;; Inflate to min-length if one was given.
                 (str
                  (str/join
                   (repeat pad-length (first alphabet)))
                  pre-padded)))))))))


(ann make-decoder [String -> [String -> ByteArray]])
(defn make-decoder
  "Returns an decoder function that decodes strings using
   the given alphabet as the code-string."
  [^String alphabet]
  (let [radix (count alphabet)]
    (fn [s]
      (let [idxs (map #(.indexOf alphabet (str %)) s)
            leading0s (count (take-while (partial = 0) idxs))]
        (let [output
              (if (= leading0s (count idxs))
                (byte-array 0)
                (as-> (reverse idxs) _
                      (map-indexed
                       (fn> :- AnyInt
                         [power :- AnyInt, n :- AnyInt]
                         (* n (expt (bigint radix) power)))
                       _)
                      (reduce + _)
                      (num->bytes _)))]
          (let [leading0-prefix (repeat leading0s (byte 0))]
            (concat-bytes leading0-prefix output)))))))


(ann bytes-equal? [ByteArray ByteArray -> Boolean])
(defn bytes-equal? [bytes1 bytes2]
  (Arrays/equals bytes1 bytes2))

(defn hexify-structure
  "Creates [possibly nested] datastructure where all byte-arrays
   are represented has hexstring values. Particularly good for
   testing since byte-arrays can't be compared with `=`."
  [m]
  (clojure.walk/walk
   ;; Inner
   (fn [[k v]]
     (cond
      (map? v) [k (hexify-structure v)]
      (coll? v) [k (map hexify-structure v)]
      (byte-array? v) [k (bytes->hex v)]
      :else [k v]))
   ;; Outer
   identity
   m))

(defmacro symp
  "Print out 'symbol-name: symbol-val' and return symbol-val."
  [sym]
  `(do (print (str ~(name sym) ": " (prn-str ~sym)))
       ~sym))
