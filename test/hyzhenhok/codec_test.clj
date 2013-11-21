(ns hyzhenhok.codec-test
  (:require [expectations :refer :all]
            [clojure.string :as str]
            [hyzhenhok.codec :refer :all]
            [hyzhenhok.util :refer :all])
  (:import [java.util Date]))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Var-int ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(given [input output] (expect output
                        (buf->hex (encode var-int-codec input)))
  ;; When < 0xfd
  0 "00"
  3 "03"
  (dec 0xfd) "fc"

  ;; When <= 0xffff
  0xfd "fdfd00"
  0xffff "fdffff"

  ;; When <= 0xffffffff
  0xffffffff "feffffffff"

  ;; When > 0xffffffff
  (inc 0xffffffff) "ff0000000001000000")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Var-str ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(given [input output] (expect output
                        (buf->hex (encode var-str-codec input)))
  ;; < 0xfd
  "" "00"
  "a" "0161"
  "aa" "026161"
  "aaa" "03616161"

  (str/join (repeat (dec 0xfd) \a))
  (str "fc" (str/join (repeat (dec 0xfd) "61")))

  ;; <= 0xffff
  (str/join (repeat 0xfd \a))
  (str "fdfd00" (str/join (repeat 0xfd "61")))

  ;; TODO: Test rest of the cases
  )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Services-codec ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(expect "0100000000000000"
  (buf->hex
   (encode services-codec :node-network)))

;; Hash-codec ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(expect "0000000000000000000000000000000000000000000000000000000000000001"
  (buf->hex
   (encode hash-codec "0100000000000000000000000000000000000000000000000000000000000000")))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Magic-codec ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(expect :testnet3
  (decode magic-codec (hex->bytes "0b110907")))

(expect "f9beb4d9"
  (buf->hex
   (encode magic-codec :mainnet)))

(expect "0b110907"
  (buf->hex
   (encode magic-codec :testnet3)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Command-codec ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(expect "696e76000000000000000000"
  (buf->hex (encode command-codec :inv)))

(expect :version
  (decode command-codec
          (str->bytes "version\0\0\0\0\0")))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Checksum-codec ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(expect "01020304"
  (buf->hex (encode checksum-codec "01020304")))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Protocol-ver ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(expect "9c7c0000"
  (buf->hex (encode :uint32-le 31900)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Timestamp ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(expect "e615104d00000000"
  (buf->hex (encode :int64-le 1292899814)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Start-height ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(expect 98645
  (decode :int32-le (hex->bytes "55810100")))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; User-agent ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(expect "/Satoshi:0.7.2/"
  (decode user-agent-codec
          (hex->bytes "0f2f5361746f7368693a302e372e322f")))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Message-header-codec ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Testing round-trip
(expect {:magic :mainnet
         :command :version
         :length 42
         :checksum "0a0b0c0d"}
  (decode message-header-codec
          (encode message-header-codec
                  {:magic :mainnet
                   :command :version
                   :length 42
                   :checksum "0a0b0c0d"})))

;; Should be 24 bytes.
(expect 24
  (byte-count
   (encode message-header-codec
           {:magic :testnet3
            :command :version
            :length 42
            :checksum "aabbccdd"})))

;; Decode
(expect
 {:magic :mainnet
  :command :version
  :length 100
  :checksum "3b648d5a"}
 (decode
  message-header-codec
  (hex->bytes
   "f9beb4d976657273696f6e0000000000640000003b648d5a")))

;; Ip-codec ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Encoding
(expect "00000000000000000000ffff01020304"
  (buf->hex
   (encode ip-codec "1.2.3.4")))

;; Round-trip
(expect "1.2.3.4"
  (decode ip-codec (encode ip-codec "1.2.3.4")))

;; Port-codec ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Only 2 bytes
(expect 2
  (byte-count (encode port-codec 3000)))

;; Net-addr-codec ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Round-trip
(expect {:services :node-network
         :ip "1.2.3.4"
         :port 1234}
  (decode net-addr-codec
          (encode net-addr-codec
                  {:services :node-network
                   :ip "1.2.3.4"
                   :port 1234})))

;; Relay-codec ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Encode
(expect "01"
  (buf->hex
   (encode relay-codec true)))

;; Round-trip
(expect false (decode relay-codec (encode relay-codec false)))

;; Decode
(expect false (decode relay-codec (hex->bytes "00")))
(expect true (decode relay-codec (hex->bytes "01")))
;; TODO: Handle Nil-should-be-true

;; Verack-payload-codec ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Round-trip
;; (expect
;;  {:magic :testnet3
;;   :command :verack
;;   :length 0
;;   :checksum "5df6e0e2"
;;   :payload nil}
;;  (decode message-codec (make-message :verack)))

;; (make-message-header
;;  (:command (meta (make-message :verack)))
;;  {})



;; (expect nil
;;   (encode verack-payload-codec {}))

;; Block-header and calc-block-hash ;;;;;;;;;;;;;;;;;;;;;;;;

;; Block hashing example from Bitcoin wiki:
;; - https://en.bitcoin.it/wiki/Block_hashing_algorithm
(expect "00000000000000001e8d6829a8a21adc5d38d0a473b144b6765798e61f98bd1d"
  (:block-hash
   (decode block-header-codec (hex->bytes (str
"0100000081cd02ab7e569e8bcd9317e2fe99f2de44d49ab2b8851ba4a308000000000000e320b6c2fffc8d750423db8b1eb942ae710e951ed797f7affc8892b0f1fc122bc7f5d74df2b9441a42a14695"
;; Note: Adding a final byte for :txn-count so it satisfies
;;       the codec
"00")))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; blockdat-codec ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Parses genesis block from blk00000.dat.

(expect  {:block-hash "000000000019d6689c085ae165831e934ff763ae46a2a6c172b3f1b60a8ce26f",
          :txns [
                 ;; Coinbase txn (txn 0)
                 {:txn-hash "4a5e1e4baab89f3a32518a88c31bc87f618f76673e2cc77ab2127b7afdeda33b"
                  :lock-time 0,
                  :txouts [
                           ;; txout 0
                           {:script "4104678afdb0fe5548271967f1a67130b7105cd6a828e03909a67962e0ea1f61deb649f6bc3f4cef38c4f35504e51ec112de5c384df7ba0b8d578a4c702b6bf11d5fac",
                            :value 5000000000N}],
                  :txins [
                          ;; txin 0
                          {:sequence 4294967295,
                           :script "04ffff001d0104455468652054696d65732030332f4a616e2f32303039204368616e63656c6c6f72206f6e206272696e6b206f66207365636f6e64206261696c6f757420666f722062616e6b73",
                           :prev-output {:idx 4294967295,
                                         :hash "0000000000000000000000000000000000000000000000000000000000000000"}}
                          ],
                  :txn-ver 1}
                 ],
          :nonce 2083236893,
          :bits "1d00ffff",
          :time 1231006505,
          :merkle-root "4a5e1e4baab89f3a32518a88c31bc87f618f76673e2cc77ab2127b7afdeda33b",
          :prev-block "0000000000000000000000000000000000000000000000000000000000000000",
          :block-ver 1},
  (decode block-payload-codec (hex->bytes "0100000000000000000000000000000000000000000000000000000000000000000000003ba3edfd7a7b12b27ac72c3e67768f617fc81bc3888a51323a9fb8aa4b1e5e4a29ab5f49ffff001d1dac2b7c0101000000010000000000000000000000000000000000000000000000000000000000000000ffffffff4d04ffff001d0104455468652054696d65732030332f4a616e2f32303039204368616e63656c6c6f72206f6e206272696e6b206f66207365636f6e64206261696c6f757420666f722062616e6b73ffffffff0100f2052a01000000434104678afdb0fe5548271967f1a67130b7105cd6a828e03909a67962e0ea1f61deb649f6bc3f4cef38c4f35504e51ec112de5c384df7ba0b8d578a4c702b6bf11d5fac00000000")))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Testing experimental changes
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;(decode BlockCodec (hex->bytes (genesis-hex)))


(expect "0000000000000000000000000000000000000000000000000000000000000001"
  (buf->hex
   (encode HashCodec (hex->bytes "0100000000000000000000000000000000000000000000000000000000000000"))))

(bytes->hex
 (decode HashCodec
         (hex->bytes "0000000000000000000000000000000000000000000000000000000000000000")))

(expect "0000000000000000000000000000000000000000000000000000000000000001"
  (buf->hex
   (decode HashCodec (hex->bytes "0100000000000000000000000000000000000000000000000000000000000000"))))

;; (let [seconds (-> (Date.) (.getTime) (quot 1000))
;;       instant (seconds->instant seconds)]
;;   (assert (= seconds (instant->seconds instant))))

(given [input output] (expect output
                        (buf->hex (encode VarIntCodec input)))
  ;; When < 0xfd
  0 "00"
  3 "03"
  (dec 0xfd) "fc"

  ;; When <= 0xffff
  0xfd "fdfd00"
  0xffff "fdffff"

  ;; When <= 0xffffffff
  0xffffffff "feffffffff"

  ;; When > 0xffffffff
  (inc 0xffffffff) "ff0000000001000000")

(given [input output] (expect output
                        (buf->hex (encode VarStrCodec input)))
  ;; < 0xfd
  "" "00"
  "a" "0161"
  "aa" "026161"
  "aaa" "03616161"

  (str/join (repeat (dec 0xfd) \a))
  (str "fc" (str/join (repeat (dec 0xfd) "61")))

  ;; <= 0xffff
  (str/join (repeat 0xfd \a))
  (str "fdfd00" (str/join (repeat 0xfd "61")))

  ;; TODO: Test rest of the cases
  )

(expect :testnet3
  (decode MagicCodec (hex->bytes "0b110907")))

(expect "f9beb4d9"
  (buf->hex
   (encode MagicCodec :mainnet)))

(expect "0b110907"
  (buf->hex
   (encode MagicCodec :testnet3)))


(expect "00000000000000001e8d6829a8a21adc5d38d0a473b144b6765798e61f98bd1d"
  (bytes->hex (:block/hash
              (decode BlockHeaderCodec (hex->bytes (str
                                                    "0100000081cd02ab7e569e8bcd9317e2fe99f2de44d49ab2b8851ba4a308000000000000e320b6c2fffc8d750423db8b1eb942ae710e951ed797f7affc8892b0f1fc122bc7f5d74df2b9441a42a14695" (comment "txnCount byte -->") "00"))))))

(let [seconds (-> (Date.) (.getTime) (quot 1000))
      instant (seconds->instant seconds)]
  (assert (= seconds (instant->seconds instant))))

(decode BlockCodec genesis-block)



(expect  {:block/hash "000000000019d6689c085ae165831e934ff763ae46a2a6c172b3f1b60a8ce26f",
          :txns [
                 ;; Coinbase txn (txn 0)
                 {:txn/hash "4a5e1e4baab89f3a32518a88c31bc87f618f76673e2cc77ab2127b7afdeda33b"
                  :txn/lockTime 0,
                  :txn/txOuts [
                           ;; txout 0
                           {:txOut/script "4104678afdb0fe5548271967f1a67130b7105cd6a828e03909a67962e0ea1f61deb649f6bc3f4cef38c4f35504e51ec112de5c384df7ba0b8d578a4c702b6bf11d5fac",
                            :txOut/value 5000000000N}],
                  :txn/txIns [
                          ;; txin 0
                          {:txIn/sequence 4294967295,
                           :txIn/script "04ffff001d0104455468652054696d65732030332f4a616e2f32303039204368616e63656c6c6f72206f6e206272696e6b206f66207365636f6e64206261696c6f757420666f722062616e6b73",
                           :prevTxOut {:txOut/idx 4294967295,
                                       :txn/hash "0000000000000000000000000000000000000000000000000000000000000000"}}
                          ],
                  :txn/ver 1}
                 ],
          :block/nonce 2083236893,
          :block/bits "1d00ffff",
          :block/time (seconds->instant 1231006505,)
          :block/merkleRoot "4a5e1e4baab89f3a32518a88c31bc87f618f76673e2cc77ab2127b7afdeda33b",
          :prevBlockHash "0000000000000000000000000000000000000000000000000000000000000000",
          :block/ver 1},
  (hexify-structure (decode BlockCodec (hex->bytes "0100000000000000000000000000000000000000000000000000000000000000000000003ba3edfd7a7b12b27ac72c3e67768f617fc81bc3888a51323a9fb8aa4b1e5e4a29ab5f49ffff001d1dac2b7c0101000000010000000000000000000000000000000000000000000000000000000000000000ffffffff4d04ffff001d0104455468652054696d65732030332f4a616e2f32303039204368616e63656c6c6f72206f6e206272696e6b206f66207365636f6e64206261696c6f757420666f722062616e6b73ffffffff0100f2052a01000000434104678afdb0fe5548271967f1a67130b7105cd6a828e03909a67962e0ea1f61deb649f6bc3f4cef38c4f35504e51ec112de5c384df7ba0b8d578a4c702b6bf11d5fac00000000"))))
