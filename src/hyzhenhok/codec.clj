(ns hyzhenhok.codec
  (:require
   [hyzhenhok.util :refer :all]
   [hyzhenhok.crypto :as crypto]
   [gloss.io :refer :all
    :exclude [contiguous encode decode]]
   [gloss.core :refer :all :exclude [byte-count]]
   [clojure.string :as str]
   [clojure.core.typed :refer :all])
  (:import
   [clojure.lang
    IPersistentMap
    PersistentArrayMap]
   [java.nio
    ByteBuffer
    ByteBuffer]))

(ann ^:no-check clojure.core/select-keys
  [(IPersistentMap Any Any) (Seqable Any)
   -> (IPersistentMap Any Any)])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def-alias CompiledFrame
  gloss.core.protocols.Reader)

;; More convenient importing these anywhere we require this ns
;; than to have to require gloss.io.

(ann ^:no-check
  gloss.io/encode [CompiledFrame Any -> ByteBuffer])
(ann encode [CompiledFrame Any -> ByteBuffer])

(def
  ^{:doc "Encodes maps/values into ByteBuffers to be
          sent over the network.

          Ex:
          (encode <codec> <value>)"}
  encode
  gloss.io/encode)

(ann ^:no-check
  gloss.io/decode [CompiledFrame (Seqable ByteBuffer) -> Any])
(ann decode [CompiledFrame (Seqable ByteBuffer)
             -> Any])
(def
  ^{:doc "Decodes ByteBuffers into maps/values.

           Ex:
           (decode <codec> <byte buffers>)"}
  decode
  gloss.io/decode)


;; Var-int ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Tested encoding
(ann ^:no-check var-int-codec CompiledFrame )
(defcodec var-int-codec
  (compile-frame
   (header
    :ubyte
    ;; header value -> body codec
    (fn [^long header-byte]
      (case header-byte
        0xfd (compile-frame :uint16-le)
        0xfe (compile-frame :uint32-le)
        0xff (compile-frame :uint64-le)
        (compile-frame
         nil-frame
         ;; Pre-encode
         (fn [body]
           body)
         ;; Post-decode
         (fn [_]
           header-byte))))
    ;; Body value -> Header value
    (fn [body-val]
      (cond
       (<  body-val 0xfd)       body-val
       (<= body-val 0xffff)     0xfd
       (<= body-val 0xffffffff) 0xfe
       :else                    0xff)))))


;; Var-str ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Tested encoding
(ann ^:no-check var-str-codec CompiledFrame)
(defcodec var-str-codec
  (finite-frame var-int-codec (string :us-ascii)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Tested encoding
(ann ^:no-check services-codec CompiledFrame)
(defcodec
  ^{:doc "Bitcoin: uint64_t"}
  services-codec
  (enum :uint64-le
        {:node-network 1}))


;; Tested encoding
(ann ^:no-check hash-codec CompiledFrame)
(defcodec
  ^{:doc "Bitcoin: char[32]-le "}
  hash-codec
  (compile-frame
   (finite-frame 32 (repeated :byte :prefix :none))
   ;; Pre-encode (Flip to little endian)
   (fn [hex]
     (reverse-bytes (hex->bytes hex)))
   ;; Post-decode (Flip to big endian)
   (fn [bytes]
     (bytes->hex (reverse-bytes bytes)))))


;; Message Header (24 bytes) ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; - magic    :uint32-le  4
;; - command  :us-ascii   12
;; - length   :uint32-le  4
;; - checksum :uint32-le  4

;; Tested encoding
(ann ^:no-check magic-codec CompiledFrame)
(defcodec ^{:doc "Bitcoin: uint32-le"}
  magic-codec
  (enum :uint32-le
        {:mainnet 0xd9b4bef9
         :testnet 0xdab5bffa
         :testnet3 0x0709110b
         :namecoin 0xfeb4bef9}))

;; Tested encoding
(ann ^:no-check command-codec CompiledFrame)
(defcodec
  ^{:doc "Bitcoin: char[12]-be us-ascii"}
  command-codec
  (compile-frame
   (string :us-ascii :length 12)
   ;; Pre-encode
   (fn> :- String [cmd :- Keyword]
     ;; (str/join
     ;;  (take 12 (concat (name s)
     ;;                   (repeat 12 (char 0)))))
     (let [s (name cmd)
           pad-length (- 12 (count s))]
       (str s (str/join (repeat pad-length (char 0))))))
   ;; Post-decode
   (fn> :- Keyword [s :- String]
     (keyword (str/join (remove (partial = (char 0)) s))))))

;; Meh, not testing
(ann ^:no-check length-codec CompiledFrame)
(defcodec length-codec :uint32-le)

(ann ^:no-check checksum-codec CompiledFrame)
(defcodec
  ^{:doc "Bitcoin: uint32-be"}
  checksum-codec
  (compile-frame
   :uint32-be
   ;; Pre-encode
   (fn> :- AnyInt [hex :- HexString]
     (hex->unum hex))
   ;; Post-decode
   (fn> :- HexString [n :- AnyInt]
     (num->hex n))))

;; Tested round-trip.
(ann ^:no-check message-header-codec CompiledFrame)
(defcodec message-header-codec
  (ordered-map
   :magic magic-codec
   :command command-codec
   :length length-codec
   :checksum checksum-codec))


;; Net-addr ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; - time     :uint32-le 4
;; - services :uint64-le 8
;; - ipv6/4   :char[16] 16 (be)
;; - port     :uint16-be

;; Tested encoding + roundtrip
(ann ^:no-check ip-codec CompiledFrame)
(defcodec ip-codec
  (compile-frame
   (finite-frame 16 (repeated :byte :prefix :none))
   ;; Pre-encode
   (fn> :- (Seqable byte)
     [ip-string :- String]
     (map unchecked-byte
          (concat (repeat 10 0x00)
                  (repeat 2 0xff)
                  (ip->bytes ip-string))))
   ;; Post-decode
   (fn> :- String
     [bytes :- ByteArray]
     (bytes->ip (take-last-bytes 4 bytes)))))

;; Tested.
(ann ^:no-check port-codec CompiledFrame)
(defcodec port-codec :uint16-be)

;; Tested poorly.
(ann ^:no-check net-addr-codec CompiledFrame)
(defcodec net-addr-codec
  (ordered-map
   :services services-codec
   :ip       ip-codec
   :port     port-codec))

(ann ^:no-check timed-net-addr-codec CompiledFrame)
(defcodec
  ^{:doc "Only used in `addr` codec"}
  timed-net-addr-codec
  (ordered-map
   :time     :uint32-le
   :services services-codec
   :ip       ip-codec
   :port     port-codec))

;; Payloads ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Version (85 bytes minimum)
;; - protocol-version :int32-le  4
;; - services         :uint64-le 8
;; - timestamp        :int64-le  8
;; - addr-recv        :net-addr  26
;; - addr-from        :net-addr  26
;; - nonce            :uint64-le 8
;; - user-agent       :var-str (0x00 if string is 0 bytes long)
;; - start-height     :int32-le  4
;; - relay            :bool      1

(ann ^:no-check protocol-ver-codec CompiledFrame)
(defcodec protocol-ver-codec :uint32-le)

;; If false then broadcast transactions will not be announced
;; until a filter{load,add,clear} command is received.
;; If missing or true, no change in protocol behaviour occurs.
;;
;; TODO: Implement MISSING->TRUE (pre-encode somewhere?
;;       like in version-codec?
;; Tested.
(ann ^:no-check relay-codec CompiledFrame)
(defcodec relay-codec
  (enum :byte {true  1, false 0}))

(ann ^:no-check user-agent-codec CompiledFrame)
(defcodec user-agent-codec var-str-codec)

(ann ^:no-check version-payload-codec CompiledFrame)
(defcodec version-payload-codec
  (ordered-map
   :protocol-version protocol-ver-codec
   :services         services-codec
   :time             :uint64-le
   :addr-recv        net-addr-codec
   :addr-from        net-addr-codec
   :nonce            :uint64-le  ; Some nonces are uint32
   :user-agent       var-str-codec
   :start-height     :int32-le
   :relay            relay-codec))

(ann ^:no-check verack-payload-codec CompiledFrame)
(defcodec verack-payload-codec
  nil-frame)

;; Block processing ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(declare block-header-codec)

;; TODO:
;;
;; The idea here was to static-type parsed codecs into CodecMaps
;; which would be post-processed into PayloadMaps via
;; `gloss._/compile-frame`'s post-decode argument.
;; I would use this map as an intermediate target for
;; network-data <-> db-data conversion.
;;
;; But now I would prefer to parse data directly into db-schema
;; maps.

(def-alias BlockHeaderCodecMap
  '{:block-ver   Long
    :prev-hash   HexString
    :merkle-root HexString
    :time        Long
    :bits        HexString
    :nonce       Long})

(def-alias BlockHeaderPayloadMap
  (I BlockHeaderCodecMap
     '{:block-hash  HexString}))

(def-alias BlockCodecMap
  (I BlockHeaderCodecMap
     '{:txns (Seqable (IPersistentMap Keyword Any))}))

(def-alias BlockPayloadMap
  (I BlockHeaderCodecMap
     '{:block-hash HexString}))

(def-alias TxnCodecMap
  '{:txn-ver   Long
    :txins     (Seqable (IPersistentMap Keyword Any))
    :txouts    (Seqable (IPersistentMap Keyword Any))
    :lock-time Long})

(def-alias TxnPayloadMap
  (I TxnCodecMap
     '{:txn-hash HexString}))

(def-alias AddrMap
  '{:services Keyword
    :ip       String
    :port     AnyInt})

(def-alias VersionPayloadMap
  '{:protocol-ver AnyInt
    :services     Keyword
    :time         AnyInt
    :addr-recv    AddrMap
    :addr-from    AddrMap
    :nonce        AnyInt
    :user-agent   String
    :start-height AnyInt
    :relay        Boolean})

(def-alias AnyPayloadMap
  (U BlockHeaderPayloadMap
     BlockPayloadMap
     VersionPayloadMap))


;; CodecMaps are passed in since this generates the :block-hash
;; key necessary to promote them into a PayloadMaps.
(ann calc-block-hash [(U BlockHeaderCodecMap BlockCodecMap)
                      -> HexString])
(defn calc-block-hash [blk]
  (let [blk-header (merge (select-keys
                           blk [:block-ver
                                :prev-block
                                :merkle-root
                                :time
                                :bits
                                :nonce])
                          ;; Even though :txn-count isn't hashed,
                          ;; we set it so that the codec can
                          ;; actually encode it even though
                          ;; we end up only taking the first 80
                          ;; bytes which doesn't include the
                          ;; :txn-count.
                          {:txn-count 0})]
    (as-> (encode block-header-codec blk-header) _
          (contiguous _)
          (.array ^ByteBuffer _)
          (take-bytes 80 _)
          (crypto/hash256 _)
          (reverse-bytes _)
          (bytes->hex _))))

(ann ^:no-check bits-codec CompiledFrame)
(defcodec bits-codec
  (compile-frame
   :uint32-le
   ;; Pre-encode
   (fn> :- AnyInt
     [hex :- HexString]
     (hex->unum hex))
   ;; Post-decode
   (fn> :- HexString
     [n :- AnyInt]
     (num->hex n))))

;; A vector of these are embedded in a `headers` message.
(ann ^:no-check block-header-codec CompiledFrame)
(def block-header-codec
  (compile-frame
   (ordered-map                      ; Total: 80 + var-int
    :block-ver   protocol-ver-codec  ; 4
    :prev-block  hash-codec          ; 32
    :merkle-root hash-codec          ; 32
    :time        :uint32-le          ; 4
    :bits        bits-codec          ; 4 (aka nBits)
    :nonce       :uint32-le          ; 4
    :txn-count   var-int-codec       ; ??
    )
   ;; Pre-encode
   identity
   ;; Post-decode -- Calc and append block-hash
   (fn> :- BlockHeaderPayloadMap
     [header :- BlockHeaderCodecMap]
     (assoc header :block-hash (calc-block-hash header)))))

(ann ^:no-check locator-codec CompiledFrame)
(defcodec locator-codec
  (repeated hash-codec :prefix var-int-codec))

;; Responded with a headers message.
(ann ^:no-check getheaders-payload-codec CompiledFrame)
(defcodec getheaders-payload-codec
  (ordered-map
   :protocol-ver protocol-ver-codec
   ;; :hash-count var-int-codec
   :locator locator-codec
   :hash-stop hash-codec))

(ann ^:no-check getblocks-payload-codec CompiledFrame)
(defcodec getblocks-payload-codec
  (ordered-map
   :protocol-ver protocol-ver-codec
   :locator locator-codec
   :hash-stop hash-codec))


;; Txn ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(ann ^:no-check script-codec CompiledFrame)
(defcodec script-codec
  (compile-frame
   (repeated :ubyte :prefix var-int-codec)
   ;; Pre-encode
   (fn> :- ByteArray
     [hex :- HexString]
     (as-> (hex->bytes hex) _
           (map (partial bit-and 0xff) _)))
   ;; Post-decode
   (fn> :- HexString
     [byte-seq :- (Seqable byte)]
     (bytes->hex
      (byte-array (map unchecked-byte byte-seq))))))

;; Txn input
(ann ^:no-check txin-codec CompiledFrame)
(defcodec txin-codec
  (ordered-map
   :prev-output {:idx :uint32-le
                 :hash hash-codec}
   :script      script-codec
   :sequence    :uint32-le))

;; Txn output
(ann ^:no-check txout-codec CompiledFrame)
(defcodec txout-codec
  (ordered-map
   ;; Satoshis (BTC/10^8)
   :value  :uint64-le
   :script script-codec))

;; Txn

(declare txn-codec)

(ann ^:no-check calc-txn-hash [TxnCodecMap -> HexString])
(defn calc-txn-hash [txn]
  (as-> (encode txn-codec txn) _
        (buf->bytes _)
        (crypto/hash256 _)
        (reverse-bytes _)
        (bytes->hex _)))

(ann ^:no-check txn-codec CompiledFrame)
(defcodec txn-codec
  (compile-frame
   (ordered-map
    :txn-ver   :uint32-le
    :txins     (repeated txin-codec :prefix var-int-codec)
    :txouts    (repeated txout-codec :prefix var-int-codec)
    :lock-time :uint32-le)
   ;; Pre-encode
   identity
   ;; Post-decode
   (fn> :- TxnPayloadMap
     [txn-map :- TxnCodecMap]
     (assoc txn-map :txn-hash (calc-txn-hash txn-map)))))

(ann ^:no-check block-payload-codec CompiledFrame)
(defcodec block-payload-codec
  (compile-frame
   (ordered-map
    :block-ver   :uint32-le
    :prev-block  hash-codec
    :merkle-root hash-codec
    :time        :uint32-le
    :bits        bits-codec
    :nonce       :uint32-le
    :txns        (repeated txn-codec :prefix var-int-codec))
   ;; Pre-encode
   identity
   ;; Post-decode
   (fn> :- BlockPayloadMap [block :- BlockCodecMap]
     (assoc block :block-hash (calc-block-hash block)))))

;; In response to getheaders.
;; It contains a vector of block-headers.
(ann ^:no-check headers-payload-codec CompiledFrame)
(defcodec headers-payload-codec
  (repeated block-header-codec :prefix var-int-codec))

;; Inventory item (36+) ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; - type uint32-le 4
;; - hash char[32]  32

(ann ^:no-check inventory-codec CompiledFrame)
(defcodec inventory-codec
  (ordered-map
   :type (enum :uint32-le
               {:error 0
                :txn   1
                :block 2})
   :hash hash-codec))

;; 37 bytes minimum (01 - contains one hash,
;;                   01 00 00 00 - type is txn
;;                   32 byte hash of that txn
(ann ^:no-check inv-payload-codec CompiledFrame)
(defcodec inv-payload-codec
  (repeated inventory-codec :prefix var-int-codec))

;; ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(ann ^:no-check ping-payload-codec CompiledFrame)
(defcodec ping-payload-codec
  {:nonce :uint64-le})

(ann ^:no-check pong-payload-codec CompiledFrame)
(defcodec pong-payload-codec
  {:nonce :uint64-le})

(ann ^:no-check addr-payload-codec CompiledFrame)
(defcodec addr-payload-codec
  ;; This is the one place timed-net-addr-codec is used.
  (repeated timed-net-addr-codec :prefix var-int-codec))

(ann ^:no-check getdata-payload-codec CompiledFrame)
(defcodec getdata-payload-codec
  (repeated inventory-codec :prefix var-int-codec))

;; Full message encoding/decoding ;;;;;;;;;;;;;;;;;;;;;;;;;;

(ann payload-codecs [Keyword -> CompiledFrame])
(defn payload-codecs
  "Payload codec lookup table."
  [command]
  (case command
    :addr       addr-payload-codec
    :getblocks  getblocks-payload-codec
    :getdata    getdata-payload-codec
    :getheaders getheaders-payload-codec
    :headers    headers-payload-codec
    :inv        inv-payload-codec
    :ping       ping-payload-codec
    :pong       pong-payload-codec
    :verack     verack-payload-codec
    :version    version-payload-codec
    :block      block-payload-codec))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Make message header ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; MessageHeader + AnyPayload = Message

(def-alias MessageHeaderMap
  '{:magic    Keyword
    :command  Keyword
    :length   AnyInt
    :checksum HexString})

(def-alias MessageMapEncodedPayload
  (I MessageHeaderMap
     '{:payload ByteBuffer}))

(def-alias MessageMapDecodedPayload
  (I MessageHeaderMap
     '{:payload AnyPayloadMap}))


;; Parse maps/bytebuffers into message maps

(ann ^:no-check make-message-header
  (Fn [Keyword AnyPayloadMap -> MessageHeaderMap]
      [Keyword ByteBuffer -> MessageHeaderMap]))

(defmulti make-message-header (fn [_ payload] (class payload)))

(defmethod make-message-header ByteBuffer
  [command encoded-payload]
  {:magic    :testnet3
   :command  command
   :length   (byte-count encoded-payload)
   :checksum (bytes->hex
              (crypto/calc-checksum
               (buf->bytes encoded-payload)))})

(defmethod make-message-header nil [command nil-value]
  (make-message-header command ))

(defmethod make-message-header IPersistentMap
  [command payload-map]
  (let [payload-codec (payload-codecs command)
        encoded-payload (encode payload-codec payload-map)]
    (make-message-header command encoded-payload)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(ann ^:no-check message-codec CompiledFrame)
(defcodec message-codec
  (compile-frame
   (header message-header-codec
           (fn [h]
             (compile-frame
              (finite-block (:length h))
              ;; Pre-encode
              ;; This we encode twice. Plz fix.
              (fn [payload-map]
                (encode (payload-codecs (:command h))
                        payload-map))
              ;; Post-decode (Attach encoded-payload
              ;; to header map)
              (fn [p]
                (assoc h :payload p))))
           ;; BodyVal(payloadmap) -> Headerval
           ;; I.e. calc headermap from payloadmap.
           ;; headermap is then passed to h->bcodec to
           ;;   get the codec to encode payloadmap.
           (fn [payload-map]
             (let [header-val
                   (make-message-header
                    (:command (meta payload-map))
                    payload-map)]
               header-val)))
   ;; Pre-encode
   identity
   ;; Post-decode
   identity
   ;; (fn [message]
   ;;   (let [command (:command message)]
   ;;     (update-in message
   ;;                [:payload]
   ;;                #(decode (payload-codecs command) %))))
   ))

(ann ^:no-check make-message [Keyword * -> AnyPayloadMap])
(defmulti make-message (fn [command & _] command))

(defmethod make-message :verack
  [_ &]
  ^{:command :verack}
  {}
  )

(defmethod make-message :version
  [_ {:keys [host port start-height]}]
  (with-meta
    {:protocol-ver 70001
     :services :node-network
     :time      (generate-timestamp)
     :addr-recv {:services :node-network
                 :ip host
                 :port port}
     :addr-from {:services :node-network
                 :ip "8.0.8.0"
                 :port 8080}
     :nonce (generate-nonce 64)
     :user-agent "/hyzhenhok:0.0.1/"
     :start-height start-height
     :relay true}
    {:command :version}))

;; ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(ann genesis-hash HexString)
(def genesis-hash (str/join (repeat 64 \0)))

(ann max-stop HexString)
(def max-stop (str/join (repeat 64 \0)))

(defmethod make-message :getheaders
  [_ {:keys [locator hash-stop]
      :or {locator [genesis-hash]
           hash-stop max-stop}}]
  (assert (not-empty locator))
  (assert (not-empty hash-stop))
  (with-meta
    {:protocol-ver 70001
     :locator locator
     :hash-stop hash-stop}
    {:command :getheaders}))

(defmethod make-message :getblocks
  [_ {:keys [locator hash-stop]
      :or {locator [genesis-hash]
           hash-stop max-stop}}]
  (assert (not-empty locator))
  (assert (not-empty hash-stop))
  (with-meta
    {:protocol-ver 70001
     :locator      locator
     :hash-stop    hash-stop}
    {:command :getblocks}))

(defmethod make-message :ping [& _]
  (with-meta
    {:nonce (generate-nonce 64)}
    {:command :ping}))

;; :type is :txn or :block
(defmethod make-message :getdata
  [_ {:keys [type hashes]}]
  (with-meta
    (mapv #(assoc {:type type} :hash %) hashes)
    {:command :getdata}))

;; ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; The format of a blkXXXXX.dat file is:
;; <:magic><:size><:block>
;;
;; :size includes the bytes in the :block + the 4 bytes
;; that represent the :size itself (but not :magic).
(ann ^:no-check blockdat-codec CompiledFrame)
(defcodec blockdat-codec
  (ordered-map
   :magic magic-codec
   :size :uint32-le
   :block block-payload-codec))

;; Genesis ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(require '[clojure.java.io :as io])

(defn genesis-hex
  "Ship with the genesis hex, the one block we can trust
   without verification. Every other block chains back
   to this block through :block/prevBlock."
  []
  (->> (io/resource "genesis.dat")
       (slurp)
       (str/trim-newline)))

(def genesis-hash
  (let [hash (->> (genesis-hex)
                  (hex->bytes)
                  (decode block-payload-codec)
                  (calc-block-hash))]
    (assert (= hash "000000000019d6689c085ae165831e934ff763ae46a2a6c172b3f1b60a8ce26f"))
    hash))
