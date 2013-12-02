(ns hyzhenhok.seed
  (:require [datomic.api :as d]
            [hyzhenhok.db :as db]
            [gloss.io]
            [clojure.string :as str]
            [hyzhenhok.util :refer :all]
            [hyzhenhok.codec2 :as codec]
            [clojure.java.io :as io]
            [hyzhenhok.script :as script]))

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
  (->> (resource-bytes filename)
       (gloss.io/lazy-decode-all codec/BlkDatCodec)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn construct-addrs [blk]
  (let [txns (:block/txns blk)]
    (concat
     (for [tx txns
           txin (:txn/txIns tx)
           :let [txin-eid (:db/id txin)]
           addr (script/extract-addrs (:txIn/script txin))]
       {:db/id (db/gen-tempid)
        :addr/b58 addr
        :addr/txIns txin-eid})
     (for [tx txns
           txout (:txn/txOuts tx)
           :let [txout-eid (:db/id txout)]
           addr (script/extract-addrs (:txOut/script txout))]
       {:db/id (db/gen-tempid)
        :addr/b58 addr
        :addr/txOuts txout-eid}))))

(defn construct-blks
  [db start-idx blks]
  (let [blk->tempid (atom {})
        txout->tempid (atom {})]
    (for [[blk-idx blk] (map vector (iterate inc start-idx) blks)]
      (let [blk-tempid (db/gen-tempid)]
        ;; Add this blk's tempid to blk lookup.
        (swap! blk->tempid
               conj
               [(seq (:block/hash blk)) blk-tempid])
        ;; Construct blk
        (merge
         ;; Lookup prevBlock in db or in ->tempid map.
         ;; TODO: nil should fail during import.
         (when-let [prev-id
                    (or (:db/id (db/find-blk-by-hash2
                                 db (:prevBlockHash blk)))
                        (@blk->tempid (seq (:prevBlockHash blk))))]
           {:block/prevBlock prev-id})
         {:db/id blk-tempid
          :block/idx blk-idx
          :block/hash (:block/hash blk)
          :block/ver (:block/ver blk)
          :block/merkleRoot (:block/merkleRoot blk)
          :block/time (:block/time blk)
          :block/bits (:block/bits blk)
          :block/nonce (:block/nonce blk)
          ;; Construct txns
          :block/txns (map-indexed
                       (fn [txn-idx txn]
                         (let [txn-tempid (db/gen-tempid)
                               ;; We need this in txout
                               txn-hash (:txn/hash txn)]
                           {:db/id txn-tempid
                            :txn/hash txn-hash
                            :txn/ver (:txn/ver txn)
                            :txn/lockTime (:txn/lockTime txn)
                            :txn/idx txn-idx
                            :txn/txOuts (map-indexed
                                         (fn [txout-idx txout]
                                           (let [txout-tempid (db/gen-tempid)]
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
                                          (let [txin-tempid (db/gen-tempid)
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
                                                                        (:db/id (db/find-txout-by-hash-and-idx2
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
                       (:block/txns blk))})))))

(defn round-down [interval idx]
  (- idx (mod idx interval)))

(defn import-dat []
  (println "Recreating database...")
  (db/create-database)
  (println "Creating coinbase txn...")
  (db/create-coinbase-txn)
  (let [blk-count (db/get-block-count)
        per-batch 100
        start-idx (round-down per-batch blk-count)
        counter (atom 0)]
    (println "Blocks in database:" blk-count)
    (println "Transacting...")
    (reduce (fn [db blk-frame-batch]
              (let [curr-count (-> @counter
                                   (* per-batch)
                                   (+ blk-count))
                    dtx-batch (construct-blks db
                                              curr-count
                                              blk-frame-batch)
                    full-dtx (concat dtx-batch
                                     (mapcat construct-addrs
                                             dtx-batch))]
                (print "  " curr-count "\r") (flush)
                (swap! counter inc)
                (->> @(d/transact-async (db/get-conn) full-dtx)
                     :db-after)))
            (db/get-db)
            (->> (lazy-blkdat-frames "blk00000.dat")
                 ;(take 10000)
                 (map :block)
                 (drop start-idx)
                 (partition-all per-batch)
                 (pmap doall))))
  (println "\nBlocks in database:" (db/get-block-count)))

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
