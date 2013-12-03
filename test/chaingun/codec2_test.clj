(ns chaingun.codec2-test
  (:require [expectations :refer :all]
            [clojure.string :as str]
            [chaingun.db :as db]
            [chaingun.codec2 :refer :all]
            [chaingun.util :refer :all])
  (:import [java.util Date]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Codec utils
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(let [seconds (-> (Date.) (.getTime) (quot 1000))
      instant (seconds->instant seconds)]
  (expect true (= seconds (instant->seconds instant))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Test-helper toys
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn test-txn
  "Txn idx 1 in block 728.
   The first mainnet block with a standard transaction (2 inputs)."
  []
  (-> "6f7cf9580f1c2dfb3c4d5d043cdbb128c640e3f20161245aa7372e9666168516"
      db/find-txn-by-hash))

(defn test-txin []
  (->> (test-txn)
       :txn/txIns
       (filter #(= 1 (:txIn/idx %)))
       first))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Roundtrip with txIn
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(expect
  {:txIn/sequence 4294967295,
   :txIn/script "493046022100a2ab7cdc5b67aca032899ea1b262f6e8181060f5a34ee667a82dac9c7b7db4c3022100911bc945c4b435df8227466433e56899fbb65833e4853683ecaa12ee840d16bf01",
   :prevTxOut {:txOut/idx 0,
               :txn/hash "2db69558056d0132d9848851fd20329be9cd590fa5ae2b3c55f58931f42e27f7"}}
  (->> (test-txin)
       touch-all
       (encode-entity :txin)
       (decode-entity :txin)
       hexifying-walk))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Roundtrip with txn
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(expect {:txn/hash "6f7cf9580f1c2dfb3c4d5d043cdbb128c640e3f20161245aa7372e9666168516"
         :txn/loclTime 0
         :txn/txOuts [{:txOut/script
                       :txOut/value}]
         :txn/txIns []})
(->> (test-txn)
     touch-all
     (encode-entity :txn)
     (decode-entity :txn)
     hexifying-walk)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Roundtrip with block
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; (bytes->hex (:txn/hash (datomic.api/touch (test-txn))))

;; {:txn/hash "6f7cf9580f1c2dfb3c4d5d043cdbb128c640e3f20161245aa7372e9666168516",
;;  :txn/lockTime 0,
;;  :txn/txOuts ({:txOut/script "76a91412ab8dc588ca9d5787dde7eb29569da63c3a238c88ac",
;;                :txOut/value 10000000000N}),
;;  :txn/txIns ({:txIn/sequence 4294967295,
;;               :txIn/script "493046022100e26d9ff76a07d68369e5782be3f8532d25ecc8add58ee256da6c550b52e8006b022100b4431f5a9a4dcb51cbdcaae935218c0ae4cfc8aa903fe4e5bac4c208290b7d5d01",
;;               :prevTxOut {:txOut/idx 0,
;;                           :txn/hash "ff3dc8b461305acc5900d31602f2dafebfc406e5b050b14a352294f0965e0bf6"}}
;;              {:txIn/sequence 4294967295,
;;               :txIn/script "493046022100a2ab7cdc5b67aca032899ea1b262f6e8181060f5a34ee667a82dac9c7b7db4c3022100911bc945c4b435df8227466433e56899fbb65833e4853683ecaa12ee840d16bf01",
;;               :prevTxOut {:txOut/idx 0,
;;                           :txn/hash "2db69558056d0132d9848851fd20329be9cd590fa5ae2b3c55f58931f42e27f7"}}),
;;  :txn/ver 1}

;; (decode :txn (encode (touch-all (get-toy-txn))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Block-header and calc-block-hash ;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Block hashing example from Bitcoin wiki:
;; - https://en.bitcoin.it/wiki/Block_hashing_algorithm
(expect "00000000000000001e8d6829a8a21adc5d38d0a473b144b6765798e61f98bd1d"
  (bytes->hex
   (:block/hash
    (decode-entity :block-header
                   (hex->bytes (str
                                "0100000081cd02ab7e569e8bcd9317e2fe99f2de44d49ab2b8851ba4a308000000000000e320b6c2fffc8d750423db8b1eb942ae710e951ed797f7affc8892b0f1fc122bc7f5d74df2b9441a42a14695"
                                ;; Note: Adding a final byte for :txn-count so it satisfies
                                ;;       the codec
                                "00"))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; HashCodec
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(expect "0000000000000000000000000000000000000000000000000000000000000001"
  (buf->hex
   (encode-entity :hash (hex->bytes "0100000000000000000000000000000000000000000000000000000000000000"))))

(bytes->hex
 (decode-entity :hash
         (hex->bytes "0000000000000000000000000000000000000000000000000000000000000000")))

(expect "0000000000000000000000000000000000000000000000000000000000000001"
  (buf->hex
   (decode-entity :hash (hex->bytes "0100000000000000000000000000000000000000000000000000000000000000"))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; VarIntCodec
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(given [input output] (expect output
                        (buf->hex (encode-entity :var-int input)))
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
;; VarStrCodec
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(given [input output] (expect output
                        (-> (encode-entity :var-str input)
                            buf->hex))
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
;; MagicCodec
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(expect :testnet3
  (decode-entity :magic (hex->bytes "0b110907")))

(expect "f9beb4d9"
  (buf->hex
   (encode-entity :magic :mainnet)))

(expect "0b110907"
  (buf->hex
   (encode-entity :magic :testnet3)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; BlockHeaderCodec
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(expect "00000000000000001e8d6829a8a21adc5d38d0a473b144b6765798e61f98bd1d"
  (bytes->hex (:block/hash
              (decode-entity :block-header (hex->bytes (str
                                                    "0100000081cd02ab7e569e8bcd9317e2fe99f2de44d49ab2b8851ba4a308000000000000e320b6c2fffc8d750423db8b1eb942ae710e951ed797f7affc8892b0f1fc122bc7f5d74df2b9441a42a14695" (comment "txnCount byte -->") "00"))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(expect  {:block/hash "000000000019d6689c085ae165831e934ff763ae46a2a6c172b3f1b60a8ce26f",
          :block/txns [
                 ;; Coinbase txn (txn 0)
                 {:txn/hash "4a5e1e4baab89f3a32518a88c31bc87f618f76673e2cc77ab2127b7afdeda33b"
                  :txn/lockTime 0,
                  :txn/txOuts [
                           ;; txout 0
                           {:txOut/idx 0
                            :txOut/script "4104678afdb0fe5548271967f1a67130b7105cd6a828e03909a67962e0ea1f61deb649f6bc3f4cef38c4f35504e51ec112de5c384df7ba0b8d578a4c702b6bf11d5fac",
                            :txOut/value 5000000000N}],
                  :txn/txIns [
                          ;; txin 0
                          {:txIn/idx 0
                           :txIn/sequence 4294967295,
                           :txIn/script "04ffff001d0104455468652054696d65732030332f4a616e2f32303039204368616e63656c6c6f72206f6e206272696e6b206f66207365636f6e64206261696c6f757420666f722062616e6b73",
                           :prevTxOut {:txOut/idx 4294967295,
                                       :txn/hash "0000000000000000000000000000000000000000000000000000000000000000"}}
                          ],
                  :txn/ver 1
                  :txn/idx 0}
                 ],
          :block/nonce 2083236893,
          :block/bits "1d00ffff",
          :block/time (seconds->instant 1231006505,)
          :block/merkleRoot "4a5e1e4baab89f3a32518a88c31bc87f618f76673e2cc77ab2127b7afdeda33b",
          :prevBlockHash "0000000000000000000000000000000000000000000000000000000000000000",
          :block/ver 1},
  (hexifying-walk (decode-entity :block (hex->bytes "0100000000000000000000000000000000000000000000000000000000000000000000003ba3edfd7a7b12b27ac72c3e67768f617fc81bc3888a51323a9fb8aa4b1e5e4a29ab5f49ffff001d1dac2b7c0101000000010000000000000000000000000000000000000000000000000000000000000000ffffffff4d04ffff001d0104455468652054696d65732030332f4a616e2f32303039204368616e63656c6c6f72206f6e206272696e6b206f66207365636f6e64206261696c6f757420666f722062616e6b73ffffffff0100f2052a01000000434104678afdb0fe5548271967f1a67130b7105cd6a828e03909a67962e0ea1f61deb649f6bc3f4cef38c4f35504e51ec112de5c384df7ba0b8d578a4c702b6bf11d5fac00000000"))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn test-blk
  "Block 728."
  []
  (db/find-block-by-hash "00000000d14f2e97678951ad004d6699babd27e07ca722c46b30dc24c67eed7a"))

;; TODO: Also do deep compare on :block/txns
(expect
    {:block/ver 1
     :prevBlockHash "000000001c7eb6ab129cf14659aea1f77f6e116ea8da2193182b08eae6ecf5f7"
     :block/merkleRoot "1f7fd770697c167ca75e3d742f3b1b81244165e0fee87310cd20b15f6975b961"
     :block/time 1232133515
     :block/bits "1d00ffff"
     :block/nonce 95106676}
  (-> (test-blk)
      touch-all
      encode-block
      decode-block
      (select-keys [:block/ver :prevBlockHash :block/merkleRoot
                    :block/time :block/bits :block/nonce])
      (update-in [:block/time] (partial instant->seconds))
      (hexifying-walk)))
