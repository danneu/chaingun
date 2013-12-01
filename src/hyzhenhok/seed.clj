(ns hyzhenhok.seed
  (:require [datomic.api :as d]
            [hyzhenhok.db :as db]
            [gloss.io]
            [clojure.string :as str]
            [hyzhenhok.util :refer :all]
            [hyzhenhok.codec2 :as codec]
            [clojure.java.io :as io]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; (def genesis-block
;;   "Ship with the genesis block, the one block we can trust
;;    without verification. Every other block chains back
;;    to this block through :block/prevBlock."
;;   (->> (io/resource "genesis.dat")
;;        (slurp)
;;        (str/trim-newline)
;;        (hex->bytes)
;;        (codec/decode-block)
;;        (#(assoc % :block/idx 0))))

;; (def genesis-hash
;;   (hex->bytes "000000000019d6689c085ae165831e934ff763ae46a2a6c172b3f1b60a8ce26f"))

;; ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; (defn resource-bytes
;;   "Returns file contents as a byte-array.
;;    `filename` is relative to the resources dir.
;;    Ex: (resource-bytes \"blk00000.dat\") => [B"
;;   [filename]
;;   (let [stream (io/input-stream (io/resource filename))
;;         available (.available stream)
;;         bytes-out (byte-array available)]
;;     (.read stream bytes-out 0 available)
;;     bytes-out))

;; (defn lazy-blkdat-frames [filename]
;;   (->> (resource-bytes filename)
;;        (gloss.io/lazy-decode-all codec/BlkDatCodec)))

;; (first (map :block (lazy-blkdat-frames "blk00000.dat")))


;; ;; Blockchain seeds ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; ;; COINBASE TXN
;; ;; - :txn/hash (byte-array 32)
;; ;; - :txn/txOuts #{{:txOut/idx 4294967295}}
;; (defn create-coinbase-txn
;;   "To make txOut serialize/deserialize simpler, all coinbase
;;    txns with refer to this txn."
;;   []
;;   @(d/transact (db/get-conn)
;;      [{:db/id (db/gen-tempid)
;;        :txn/hash (byte-array 32)
;;        :txn/txOuts [{:db/id (db/gen-tempid)
;;                      :txOut/idx 4294967295}]}]))

;; (defn create-genesis-block []
;;   (let [eid (db/gen-tempid)]
;;     @(d/transact (db/get-conn)
;;        [(db/construct-block (db/get-db) eid  genesis-block)
;;         [:db/add eid :block/idx 0]])))

;; ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; (defn import-block [constructed-block]
;;   (when-not (db/find-block-by-hash (:block/hash constructed-block))
;;     @(d/transact-async (db/get-conn) [constructed-block])))

;; (defn seed []
;;   (println "Recreating database...")
;;   (db/create-database)
;;   (println "Creating coinbase txn...")
;;   (create-coinbase-txn)
;;   (println "Creating genesis block...")
;;   (create-genesis-block)
;;   (println "Blocks in database:" (db/get-block-count))
;;   (println "Txns in database:" (db/get-txn-count))
;;   (let [counter (atom 0)]
;;     (doseq [blk (->> (lazy-blkdat-frames "blk00000.dat")
;;                      (map :block)
;;                      (take 10000)
;;                      (map codec/construct-block))]
;;       (import-block blk)
;;       (when (zero? (mod (swap! counter inc) 100))
;;         (println @counter)))))
