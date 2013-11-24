(ns hyzhenhok.codec
  (:require
   [hyzhenhok.util :refer :all]
   [hyzhenhok.crypto :as crypto]
   [clojure.java.io :as io]
   [gloss.io :refer :all
    :exclude [contiguous encode decode]]
   [gloss.core :refer :all :exclude [byte-count]]
   [clojure.string :as str]
   [hyzhenhok.db :as db]
   [clojure.core.typed :refer :all])
  (:import
   [clojure.lang
    IPersistentMap
    PersistentArrayMap]
   [java.util
    Date]
   [java.nio
    ByteBuffer
    ByteBuffer]))

(ann ^:no-check clojure.core/select-keys
  [(IPersistentMap Any Any) (Seqable Any)
   -> (IPersistentMap Any Any)])

;; TODO
;; - Consider a more common ns to move things like genesis-block
;;   and genesis-hash.
;; - Split up wire codecs from message/payload codecs.
;; - Remove the old codec code.

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(declare genesis-block genesis-hash)

(def-alias CompiledFrame
  gloss.core.protocols.Reader)

;; More convenient importing these anywhere we require this ns
;; than to have to require gloss.io.

(ann ^:no-check
  gloss.io/encode [CompiledFrame Any -> ByteBuffer])
(ann encode [CompiledFrame Any -> ByteBuffer])

(def encode
  "Encodes maps/values into ByteBuffers to be
  sent over the network.
  Ex: (encode <codec> <value>)"
  gloss.io/encode)

(ann ^:no-check
  gloss.io/decode [CompiledFrame (Seqable ByteBuffer) -> Any])
(ann decode [CompiledFrame (Seqable ByteBuffer)
             -> Any])
(def decode
  "Decodes ByteBuffers into maps/values.
   Ex: (decode <codec> <byte buffers>)"
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

;; (ann genesis-hash HexString)
;; (def genesis-hash (str/join (repeat 64 \0)))

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


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Used to convert Long<->Instant (:block/time)
;; Need to ensure roundtrip parity between these two
;; functions or else serialization roundtrips won't be equal!
(defn seconds->instant [secs]
  (Date. (* 1000 secs)))
(defn instant->seconds [inst]
  (-> (.getTime inst) (quot 1000)))


(declare TxnCodec)

(defn calc-txn-hash2 [txn]
  (as-> (encode TxnCodec txn) _
        (buf->bytes _)
        (crypto/hash256 _)
        (reverse-bytes _)))

(declare BlockHeaderCodec)

(defn calc-block-hash2 [blk]
  (let [blk-header (merge (select-keys
                           blk [:block/ver
                                :prevBlockHash
                                :block/merkleRoot
                                :block/time
                                :block/bits
                                :block/nonce])
                          ;; Even though :txn-count isn't hashed,
                          ;; we set it so that the codec can
                          ;; actually encode it even though
                          ;; we end up only taking the first 80
                          ;; bytes which doesn't include the
                          ;; :txn-count.
                          {:txnCount 0})]
    (as-> (encode BlockHeaderCodec blk-header) _
          (contiguous _)
          (.array ^ByteBuffer _)
          (take-bytes 80 _)
          (crypto/hash256 _)
          (reverse-bytes _))))

;; Experimenting with a more 1:1 Codec<->DatomicEntity
;; conversion. Perhaps I can do this without introducing
;; dependency on a running transactor just to encode.

;; "Entity-ready" keys will look like :block/merkleRoot,
;; but if a key/value needs further processing or if it
;; can't be determined without a db lookup, it'll be naked:
;;
;;   EX:
;;   :prevBlockHash (bytes) vs the :block/prevBlock (ref)
;;      you'd see in the database.

(defcodec MagicCodec
  (enum :uint32-le
        {:mainnet 0xd9b4bef9
         :testnet 0xdab5bffa
         :testnet3 0x0709110b
         :namecoin 0xfeb4bef9}))

(defcodec VarIntCodec
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
         (fn [body] body)
         ;; Post-decode
         (fn [_] header-byte))))
    ;; Body value -> Header value
    (fn [body-val]
      (cond
       (<  body-val 0xfd)       body-val
       (<= body-val 0xffff)     0xfd
       (<= body-val 0xffffffff) 0xfe
       :else                    0xff)))))

(defcodec ScriptCodec
  (compile-frame
   (finite-block VarIntCodec)
   ;; Pre-encode
   identity
   ;; Post-decode
   (fn [heapbuf]
     (buf->bytes heapbuf))))

(defcodec VarStrCodec
  (finite-frame VarIntCodec (string :us-ascii)))

(defcodec BitsCodec
  (compile-frame
   :uint32-le
   ;; Pre-encode
   (fn [bytes]
     (bytes->unum bytes))
   ;; Post-decode
   (fn [n]
     (num->bytes n))))

(defcodec HashCodec
  (compile-frame
   (finite-block 32)
   ;; Pre-encode (Flip to little endian)
   (fn [bytes-be]
     (reverse-bytes bytes-be))
   ;; Post-decode (Flip to big endian)
   (fn [heapbuf-le]
     (reverse-bytes (buf->bytes heapbuf-le)))))

(defcodec BlockHeaderCodec
  (compile-frame
   (ordered-map                            ; Total: 80 + var-int
    :block/ver         protocol-ver-codec  ; 4
    ;; BlockEntity has :block/prevBlock ref.
    :prevBlockHash     HashCodec           ; 32
    :block/merkleRoot  HashCodec           ; 32
    :block/time        :uint32-le          ; 4
    :block/bits        BitsCodec           ; 4 (aka nBits)
    :block/nonce       :uint32-le          ; 4
    ;; BlockEntity has :block/txns instead.
    :txnCount          VarIntCodec         ; ??
    )
   ;; Pre-encode
   (fn [header]
     (-> header
         (update-in [:block/time] (partial instant->seconds))))
   ;; Post-decode -- Calc and append block-hash
   (fn [header]
     (as-> header _
           (update-in _ [:block/time] (partial seconds->instant))
           (assoc _ :block/hash (calc-block-hash2 _))))))

(defcodec TxInCodec
  (compile-frame
   (ordered-map
    ;; Turns into :txIn/prevTxOut (ref).
    ;; - txOut/idx and txn/hash used to find txOut ref.
    :prevTxOut     (ordered-map
                    :txn/hash  HashCodec
                    :txOut/idx :uint32-le)
    :txIn/script   ScriptCodec
    :txIn/sequence :uint32-le)
   ;; Pre-encode
   ;; - :txIn/prevTxOut -> :prevTxOut
   ;; - It's probably an awful idea to introduce db dependency
   ;;   during codec encode/decode, but perhaps I can keep it
   ;;   completely out of the way unless I actually have a
   ;;   db entity. Alternatively, I can create some db-entity->map
   ;;   function and leave the db dep there.
   (fn [txin]
     (if (:prevTxOut txin)
       ;; If it already has :prevTxOut, do nothing.
       txin
       ;; Else derive :prevTxOut
       (let [{prev-txout :txIn/prevTxOut} txin
             txout-idx (:txOut/idx prev-txout)
             txn-hash (-> prev-txout db/parent-txn :txn/hash)]
         (assoc txin :prevTxOut {:txn/hash txn-hash
                                 :txOut/idx txout-idx}))))
   ;; Post-decode
   identity))

;; Txn output
(defcodec TxOutCodec
  (ordered-map
   ;; Satoshis (BTC/10^8)
   :txOut/value  :uint64-le
   :txOut/script ScriptCodec))

(defcodec TxnCodec
  (compile-frame
   (ordered-map
    :txn/ver      :uint32-le
    :txn/txIns    (repeated TxInCodec :prefix VarIntCodec)
    :txn/txOuts   (repeated TxOutCodec :prefix VarIntCodec)
    :txn/lockTime :uint32-le)
   ;; Pre-encode
   ;; - Sort txIns and txOuts by :idx since order matters.
   ;;   Note: Of course, if we then decode it again, there
   ;;   are no :idx keys anymore since they're added during
   ;;   construction. Although I'm wary of moving db-related
   ;;   business here, I think I'll experiment with it til
   ;;   I see how it bites me.
   (fn [txin]
     (-> txin
         (update-in [:txn/txIns] (partial sort-by :txIn/idx))
         (update-in [:txn/txOuts] (partial sort-by :txOut/idx))))
   ;; Post-decode
   ;; - As above, consider adding :txIn/idx and :txOut/idx on
   ;;   decode. That would let me remove it from db constructor.
   (fn [txn-map]
     (assoc txn-map :txn/hash (calc-txn-hash2 txn-map)))))

(defcodec BlockCodec
  (compile-frame
   (ordered-map
    :block/ver         :uint32-le
    :prevBlockHash     HashCodec
    :block/merkleRoot  HashCodec
    :block/time        :uint32-le
    :block/bits        BitsCodec
    :block/nonce       :uint32-le
    :txns              (repeated TxnCodec :prefix VarIntCodec))
   ;; Pre-encode
   (fn [block]
     (-> block
         (update-in [:block/time] (partial instant->seconds))))
   ;; Post-decode
   (fn [block]
     (as-> block _
           (update-in _ [:block/time]
                      (partial seconds->instant))
           (assoc _ :block/hash (calc-block-hash2 _))
           ;; :time Long -> :block/time Date
           ))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defcodec BlkDatCodec
  (ordered-map
   :magic MagicCodec
   :size :uint32-le
   :block BlockCodec))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Seed DB ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn resource-bytes
  "Returns file contents as a byte-array.
   `filename` is relative to the resources dir.
   Ex: (resource-bytes \"blk00000.dat\") => [B"
  [filename]
  (let [stream (io/input-stream (io/resource filename))
        available (.available stream)
        bytes-out (byte-array available)]
    (.read stream bytes-out 0 available)
    bytes-out))

(defn lazy-blkdat-frames [filename]
  (gloss.io/lazy-decode-all BlkDatCodec
                            (resource-bytes filename)))

;;(println (count (lazy-blkdat-frames "blk00001.dat")))
;=> 11272

;;(println (count (lazy-blkdat-frames "blk00090.dat")))
;=> Insufficient bytes. Looks like its tail is padded with \0 bytes.

(defn seed-db-demo []
  (println "Creating database...")
  (db/create-database)
  (print "Creating genesis block...")
  (when (db/create-block
         (decode BlockCodec genesis-block))
    (println "Done."))
  (print "Creating the first 299 post-genesis blocks...")
  (doseq [blkdat (->> (lazy-blkdat-frames "blocks300.dat")
                      (drop 1)
                      (take 299))]
    (when (db/create-block (:block blkdat))
      (print ".") (flush)))
  (println "Done. Blocks in database:" (db/get-block-count)))

(defn write-blocks300 []
  (with-open [stream (io/output-stream
                      "resources/blocks300.dat")]
    (print "Writing to resources/blocks300.dat...")
    (flush)
    (let [blocks (take 300 (lazy-blkdat-frames "blk00000.dat"))]
      (gloss.io/encode-to-stream BlkDatCodec stream blocks)
      (println "Done."))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; The problem is that since blocks and txins have
;; attributes that point to existing blocks and txouts,
;; we need to (1) lookup blocks/txouts in the db or
;; (2) lookup in block->tempid and txout->tempid
;; maps if it's not in the db (i.e. it only exists
;; earlier in this datomic-tx).
;;
;; Ideally we'd be able to reduce an extended
;; `d/with` db, but unfortunately
;; `d/with` doesn't produce the same :db/idxs that the
;; actual transact will produce.
;;
;; I couldn't think of a nice way to do this without
;; either using atoms or making function signatures
;; hard to understand, so I used atoms since this
;; function altogether is already gonna be jokes.

(require '[datomic.api :as d])

;; Gotta differentiate the lingo:
;; - dtx: Datomic transaction
;; - txn: Bitcoin transaction
;; - txin: Bitcoin transaction input
;; - txout: Bitcoin transaction output
(defn blk-dtxs
  "This function whispers, 'Kill me.'

   Ideally I'd be able to:
   (reduce (fn [db blk] ... (d/with ...)) blks).

   I wanted a way to be able to construct arbitrary
   stretches of blocks before committing them. This
   function can lookup prevBocks and prevTxOuts in
   both the db and in blocks/txout constructed within
   it."
  [db blks]
  (let [blk->tempid (atom {})
        txout->tempid (atom {})]
    (for [blk blks]
      (let [blk-tempid (db/tempid)]
        (print ".") (flush)
        ;; Add this blk's tempid to blk lookup.
        (swap! blk->tempid
               conj
               [(seq (:block/hash blk)) blk-tempid])
        ;; Construct blk
        (merge
         ;; Lookup prevBlock in db or in ->tempid map.
         ;; TODO: nil should fail during import.
         (when-let [prev-id
                    (or (:db/id (db/find-block-by-hash
                                 db (:prevBlockHash blk)))
                        (@blk->tempid (seq (:prevBlockHash blk))))]
           {:block/prevBlock prev-id})
         {:db/id blk-tempid
          :block/hash (:block/hash blk)
          :block/ver (:block/ver blk)
          :block/merkleRoot (:block/merkleRoot blk)
          :block/time (:block/time blk)
          :block/bits (:block/bits blk)
          :block/nonce (:block/nonce blk)
          ;; Construct txns
          :block/txns (map-indexed
                       (fn [txn-idx txn]
                         (let [txn-tempid (db/tempid)
                               ;; We need this in txout
                               txn-hash (:txn/hash txn)]
                           {:db/id txn-tempid
                            :txn/hash txn-hash
                            :txn/ver (:txn/ver txn)
                            :txn/lockTime (:txn/lockTime txn)
                            :txn/idx txn-idx
                            :txn/txOuts (map-indexed
                                         (fn [txout-idx txout]
                                           (let [txout-tempid (db/tempid)]
                                             ;; Add this txout to txout-tempid
                                             ;; lookup so txin's constructor can
                                             ;; link :txIn/prevTxOut to it.
                                             (swap! txout->tempid
                                                    conj
                                                    ;; Gotta remember to seq the
                                                    ;; bytes for comparison.
                                                    [{:txn/hash (seq txn-hash)
                                                      :txOut/idx txout-idx}
                                                     txout-tempid])
                                             ;; Construct txOut
                                             {:db/id txout-tempid
                                              :txOut/idx txout-idx
                                              :txOut/value (long (:txOut/value txout))
                                              :txOut/script (:txOut/script txout)}))
                                         (:txn/txOuts txn))
                            :txn/txIns (map-indexed
                                        (fn [txin-idx txin]
                                          (let [txin-tempid (db/tempid)
                                                prev-txnhash (-> txin
                                                                 :prevTxOut
                                                                 :txn/hash)
                                                prev-txoutidx (-> txin
                                                                  :prevTxOut
                                                                  :txOut/idx)]
                                            (let [txin-dtx (merge
                                                            {:db/id txin-tempid
                                                             :txIn/idx txin-idx
                                                             :txIn/sequence (:txIn/sequence txin)
                                                             :txIn/script (:txIn/script txin)}
                                                            (when-let [prev-id
                                                                       (or
                                                                        ;; First lookup in db
                                                                        (:db/id (db/find-txout-by-hash-and-idx
                                                                                 db prev-txnhash prev-txoutidx))
                                                                        ;; Then lookup in txout->tempid
                                                                        (@txout->tempid
                                                                         ;; l0l, gotta remember to seq the
                                                                         ;; bytes.
                                                                         {:txn/hash (seq prev-txnhash)
                                                                          :txOut/idx prev-txoutidx}))]
                                                              {:txIn/prevTxOut prev-id}))]
                                              txin-dtx)))
                                        (:txn/txIns txn))}))
                       (:txns blk))})))))

;; TODO: Decode frames ahead of consumption in dtx con so that it's not
;; each loop doesn't need to wait for
(defn import-dat []
  (println "Recreating database...")
  (db/create-database)
  (println "Creating coinbase txn...")
  (db/create-coinbase-txn)
  (let [blk-count (db/get-block-count)
        per-batch 100]
    (println "Blocks in database:" blk-count)
    (println "Transacting...")
    (let [counter (atom 0)]
      (reduce (fn [db blk-frame-batch]
                (let [dtx-batch (blk-dtxs db blk-frame-batch)]
                  ;; Output every time we're actually saving
                  ;; blocks to the database.
                  (println "Transacting"
                           per-batch
                           (str " (Total: "
                                (-> (swap! counter inc)
                                    (* per-batch)
                                    (+ blk-count))
                                ")"))
                  ;; Accrete the database in each reduction.
                  (->> (d/transact-async (db/get-conn) dtx-batch)
                       (deref)
                       :db-after)))
              ;; Start off with the persisted database
              ;; as it is.
              (db/get-db)
              ;; blk00000.dat contains 119,965 blocks which
              ;; takes quite a while to parse and import.
              ;; This breaks it up into 100 paritions with
              ;; a simple mechanism for picking back up where
              ;; it left off.
              (->> (lazy-blkdat-frames "blk00000.dat")
                   (map :block)
                   (drop (- blk-count (mod blk-count per-batch)))
                   (partition per-batch)))))
  (println "Blocks in database:" (db/get-block-count)))

;; Genesis ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(require '[clojure.java.io :as io])

(def genesis-block
  "Ship with the genesis block, the one block we can trust
   without verification. Every other block chains back
   to this block through :block/prevBlock."
  (->> (io/resource "genesis.dat")
       (slurp)
       (str/trim-newline)
       (hex->bytes)))

(def genesis-hash
  "Won't even boot if we can't get this right."
  (let [hash (->> genesis-block
                  (decode BlockCodec)
                  (calc-block-hash2))]
    (assert (java.util.Arrays/equals
             hash
             (hex->bytes "000000000019d6689c085ae165831e934ff763ae46a2a6c172b3f1b60a8ce26f")))
    hash))
