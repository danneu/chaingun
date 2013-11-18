(ns hyzhenhok.db
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [datomic.api :as d]
   [gloss.io]
   [hyzhenhok.util :refer :all]
   [hyzhenhok.codec :as codec])
  (:import
   [datomic Util]
   [java.util Date]))

;; Import settings
;; (System/setProperty "datomic.peerConnectionTTLMsec" "30000")
;; (System/setProperty "datomic.objectCacheMax" "256m")

;; Datomic utils ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn read-all
  "Read all forms in f, where f is any resource that can
   be opened by io/reader"
  [f]
  (Util/readAll (io/reader f)))

(defn transact-all
  "Load and run all transactions from f, where f is any
   resource that can be opened by io/reader."
  [conn f]
  (doseq [txd (read-all f)]
    @(d/transact conn txd))
  :done)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def uri "datomic:free://localhost:4334/hyzhenhok")

(defn create-db
  "Recreates db with schema, returns db connection."
  []
  (d/delete-database uri)
  (d/create-database uri)
  (let [conn (d/connect uri)]
    (transact-all conn "resources/schema.edn")
    ;(transact-all conn "resources/data-functions.edn")
    conn))

(defn get-conn [] (d/connect uri))

(defn get-db [] (d/db (get-conn)))

(defn tempid []
  (d/tempid :db.part/user))

(defn only
  "Returns the only item from a query result"
  [query-result]
  (ffirst query-result))

(defn qe
  "Returns the single entity returned from a query"
  [query db & args]
  (let [result (apply d/q query db args)]
    (d/entity db (only result))))

(defn find-by
  "Returns the unique entity identified by attr and val.
   Ex: (find-by :user/uid 42)"
  [db attr val]
  (qe '[:find ?e
        :in $ ?attr ?val
        :where [?e ?attr ?val]]
      db attr val))

(defn qes
  "Returns the entities returned by a query, assuming that
   all :find results are entity ids."
  [query db & args]
  (->> (apply d/q query db args)
       (mapv (fn [items]
               (mapv (partial d/entity db) items)))))

(defn find-all-by
  "Returns all entities possessing attr."
  [db attr]
  (qes '[:find ?e
         :in $ ?attr
         :where [?e ?attr]]
       db attr))

(defn qfs
  "Returns the first of each query result"
  [query db & args]
  (->> (apply d/q query db args)
       (mapv first)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Finders ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn find-by-eid [eid]
  (find-by (d/db (get-conn)) :db/id eid))

(defn find-block-by-hash [hash]
  (find-by (d/db (get-conn)) :block/hash hash))

(defn genesis-block? [block]
  (= codec/genesis-hash (:block/hash block)))

(defn find-genesis-block []
  (find-block-by-hash codec/genesis-hash))

(defn get-block-count
  "Returns count of blocks in the db."
  []
  (or (only (d/q '[:find (count ?e)
                   :where [?e :block/hash]]
                 (d/db (get-conn))))
      0))

(defn find-txn-by-hash [hash]
  (find-by (d/db (get-conn)) :txn/hash hash))

(defn find-txout-by-hash-and-idx [db hash idx]
  (qe '[:find ?txout
        :in $ ?hash ?idx
        :where
        [?txn :txn/hash ?hash]
        [?txn :txn/txOuts ?txout]
        [?txout :txOut/idx ?idx]]
      db hash idx))

(defn get-block-idx
  "Slow ad-hoc way to get the idx of a block
   within the blockchain."
  [block]
  (if (genesis-block? block)
    0
    (only (d/q '[:find (count ?e)
                 :in $ ?time
                 :where
                 [?e :block/time ?v]
                 [(< ?v ?time)]]
               (get-db) (:block/time block)))))

;; FIXME: ad-hoc function
(defn coinbase?
  "Is this raw-txin part of a coinbase txn?

   A coinbase transaction's txin looks like:
     {...
      :prev-output {:hash '000000000000000000...',
                    :idx 4294967295}}

   :prev-output points to a txn/hash and a txOut/idx
   which are used to set the :txIn/prevTxOut reference.
   Even though the hash and idx won't exist, this
   function lets us explicitly handle coinbase outputs."
  [{:keys [hash]}]
  (= hash (str/join (repeat 32 \0))))


(defn construct-txns
  "Run for each txn in :block/txns. Each :txIn/prevTxOut is
   matched with a txOut that either exists in the database or
   within a txn constructed earlier in the loop."
  [db raw-txns]
  (loop [db db
         raw-txns raw-txns
         constructed-txns []
         idx 0]
    (let [raw-txn (first raw-txns)]
      (if-not raw-txn
        ;; Done
        constructed-txns
        ;; If there's another raw-txn, then we construct
        ;; another txn.
        (let [constructed-txn
              {:db/id (tempid)
               :txn/idx idx
               :txn/hash (:txn-hash raw-txn)
               :txn/ver (:txn-ver raw-txn)
               :txn/lockTime (:lock-time raw-txn)
               :txn/txOuts (map-indexed
                            (fn [idx txout]
                              {:db/id (tempid)
                               :txOut/idx idx
                               :txOut/value (long (:value txout))
                               :txOut/script (:script txout)})
                            (:txouts raw-txn))
               :txn/txIns (map-indexed
                           (fn [idx txin]
                             (merge
                              {:db/id (tempid)
                               :txIn/idx idx
                               :txIn/script (:script txin)
                               :txIn/sequence (:sequence txin)}
                              ;; :prev-output {:hash "...", :idx 0}
                              ;; We don't care about coinbase outs.
                              (when-not (coinbase? (:prev-output txin))
                                ;; And we ignore
                                (when-let [txout
                                           (find-txout-by-hash-and-idx
                                            db
                                            (:hash (:prev-output txin))
                                            (:idx (:prev-output txin)))]
                                  {:txIn/prevTxOut (:db/id txout)}))))
                           (:txins raw-txn))}]
          (recur
           ;; Extend the db so the rest of the txns can see
           ;; this txn with the find-txout-by-hash-and-idx lookup.
           (:db-after (d/with db [constructed-txn]))
           (next raw-txns)
           (conj constructed-txns constructed-txn)
           (inc idx)))))))

(defn construct-block [db eid block]
  (merge
   {:db/id eid
    :block/hash (:block-hash block)
    :block/ver (:block-ver block)
    :block/merkleRoot (:merkle-root block)
    :block/time (Date. (* 1000 (:time block)))
    :block/bits (:bits block)
    :block/nonce (:nonce block)
    :block/txns (construct-txns db (:txns block))}
   (when-let [prev-block (find-block-by-hash
                          (:prev-block block))]
     {:block/prevBlock (:db/id prev-block)})))

(defn create-block
  "Returns the created block entity-map or nil if
   a block with this hash already exists."
  [raw-block]
  (when-not (find-block-by-hash (:block-hash raw-block))
    (let [temp-block-eid (tempid)]
      (let [result (->> [(construct-block (d/db (get-conn))
                                          temp-block-eid
                                          raw-block)]
                        (d/transact (get-conn))
                        (deref))]
        (let [real-block-eid (d/resolve-tempid (:db-after result)
                                               (:tempids result)
                                               temp-block-eid)]
          ;;(println (class (:db-after result)))
          (d/entity (:db-after result) real-block-eid))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Seeding the db ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; Move some of this to hyzhenhok.codec.

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

;; (defn blk0-frames
;;   "Lazy sequence of blockdats decoded from blk00000.dat.

;;    {:magic _ (:mainnet or :testnet3)
;;     :size _ (Represents byte-count of :block and the 4 bytes
;;              that represent this :size integer)
;;     :block {...}"
;;   []
;;   (let [blk0-bytes (resource-bytes "blk00000.dat")]
;;     (gloss.io/lazy-decode-all codec/blockdat-codec
;;                               blk0-bytes)))

(defn lazy-blockdat-frames [filename]
  (gloss.io/lazy-decode-all codec/blockdat-codec (resource-bytes filename)))

(defn seed-db []
  (println "Creating database...")
  (create-db)
  (print "Creating genesis block...")
  (when (create-block
         (codec/decode codec/block-payload-codec
                       (hex->bytes (codec/genesis-hex))))
    (println "Done."))
  (print "Creating the first 99 post-genesis blocks...")
  (doseq [blkdat (->> (lazy-blockdat-frames "blocks100.dat")
                      (drop 1)
                      (take 99))]
    (when (create-block (:block blkdat))
      (print ".")))
  (println "Done. Blocks in database:" (get-block-count)))

;; Round trip to Bitcoin's blk00000.dat:
;;
;; (gloss.core/byte-count
;;   (gloss.io/encode-all codec/blockdat-codec
;;                        (take 100 (blk0-frames))))
;; => 22384
;;
;; (count
;;  (gloss.io/decode-all codec/blockdat-codec
;;                       (take-bytes 22384
;;                                   (blk0-bytes))))
;; => 100

(defn write-blocks100 []
  (with-open [stream (io/output-stream
                      (io/resource "blocks100.dat"))]
    (print "Writing to resources/blocks100.dat...")
    (flush)
    (let [blocks (take 100 (lazy-blockdat-frames "blk00000.dat"))]
      (gloss.io/encode-to-stream codec/blockdat-codec
                                 stream
                                 blocks)
      (println "Done."))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; (def progress (atom 0))

;; (defn mass-import []
;;   (doseq [blkdat-batch (->> (blk0-frames)
;;                             (map :block)
;;                             (remove (fn [blk]
;;                                       (find-block-by-hash
;;                                        (:block-hash blk))))
;;                             (map construct-block-dtx)
;;                             ;(partition-all 1000)
;;                             ;(map (partial apply concat))
;;                             (pmap #(d/transact-async conn %)))]
;;     @blkdat-batch
;;     (spit "progress.txt"
;;           (str (swap! progress inc) \newline)
;;           :append true))
;;   (println "DONE"))

;;(mass-import)

;; Debug toys parsed from blk00000.dat
;; (def toyblk170 (:block (nth (blk0-frames) 170)))
;; (def toyblk0 (:block (nth (blk0-frames) 0)))
;; (def the-hash
;;   "0437cd7f8525ceed2324359c2d0ba26006d92d856a9c20fa0241106ee5a597c9")
;; @(d/transact conn
;;              [{:db/id (tempid)
;;                :txn/hash the-hash
;;                :txn/txOuts [{:db/id (tempid)
;;                              :txOut/idx 0}]}])
;; (pprint (construct-block (d/db conn) toyblk0))
;; (find-txout-by-hash-and-idx (d/db conn) the-hash 0)
;; (pprint (construct-block (d/db conn) toyblk170))
