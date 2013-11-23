(ns hyzhenhok.db
  (:require
   [clojure.java.io :as io]
   [clojure.core.typed :as t]
   [clojure.string :as str]
   [datomic.api :as d]
   [gloss.io]
   [hyzhenhok.util :refer :all])
  (:import
   [datomic Util]
   [java.util Date]))

;; Import settings
(System/setProperty "datomic.peerConnectionTTLMsec" "90000")
(System/setProperty "datomic.objectCacheMax" "128m")

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

(defn create-database
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

(defn delete-database
  []
  (d/delete-database uri))

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

(defn find-block-by-hash
  ([hash] (find-block-by-hash (get-db) hash))
  ([db hash]
     (let [hash-bytes (if (string? hash)
                        (hex->bytes hash)
                        hash)]
       (qe '[:find ?e
             :in $ ?hash-bytes
             :where
             [?e :block/hash ?hash]
             [(java.util.Arrays/equals
               ^bytes ?hash ^bytes ?hash-bytes)]]
           db hash-bytes))))

(defn find-by
  "Returns the unique entity identified by attr and val.
   Ex: (find-by :user/uid 42)"
  [db attr val]
  (qe '[:find ?e
        :in $ ?attr ?val
        :where [?e ?attr ?val]]
      db attr val))

;; Created in hyzhenhok.codec, but to avoid
;; circular dependency, hard-code it here for now...
;; Need to extract part of hyzhenhok.codec into a common ns.
(def genesis-hash
  (hex->bytes "000000000019d6689c085ae165831e934ff763ae46a2a6c172b3f1b60a8ce26f"))

(defn genesis-block? [block]
  (java.util.Arrays/equals genesis-hash (:block/hash block)))

(defn find-genesis-block []
  (find-block-by-hash genesis-hash))

(defn get-block-count
  "Returns count of blocks in the db."
  ([]
     (get-block-count (get-db)))
  ([db]
     (or (only (d/q '[:find (count ?e)
                      :where [?e :block/hash]]
                    db))
         0)))

(defn find-txn-by-hash
  "Find txn by hash (String or ByteArray)."
  [hash]
  (let [hash (if (string? hash)
               (hex->bytes hash)
               hash)]
    (qe '[:find ?e
          :in $ ?given-hash
          :where
          [?e :txn/hash ?hash]
          [(java.util.Arrays/equals
            ^bytes ?hash ^bytes ?given-hash)]]
        (get-db) hash)))

(defn find-txout-by-hash-and-idx [db hash idx]
  (let [hash (if (string? hash)
               (hex->bytes hash)
               hash)]
    (qe '[:find ?txout
          :in $ ?given-hash ?idx
          :where
          [?txn :txn/hash ?hash]
          [(java.util.Arrays/equals ^bytes ?hash
                                    ^bytes ?given-hash)]
          [?txn :txn/txOuts ?txout]
          [?txout :txOut/idx ?idx]]
        db hash idx)))

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

(defn coinbase?
  "Non-robust and not sure I use this anymore."
  [txIn]
  (java.util.Arrays/equals
   (-> txIn :prevTxOut :txn/hash) (byte-array 32)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn touch-all
  "Non-robust."
  [entity]
  (->> (d/touch entity)
       (map (fn [[k v]]
              (if (coll? v)
                [k (into #{} (map d/touch v))]
                [k v])))
       (into {})))

;; FIXME: I need to write a function that turns (nested)
;;        entity maps into maps ready for codec pre-encode.
(defn map-all
  "Sort of like a recursive touch. I needed it in a few
   experimental use-cases where I needed map ops on entity-maps.

   Superbly non-robust."
  [entity]
  (->> (d/touch entity)
       (map (fn [[k v]]
              (if (coll? v)
                [k (into #{} (map (comp #(into {} %)
                                        d/touch)
                                  v))]
                [k v])))
       (into {})))

;; (def toy-txn
;;   (find-txn-by-hash "828ef3b079f9c23829c56fe86e85b4a69d9e06e5b54ea597eef5fb3ffef509fe"))

;; (def toy-txIn
;;   (-> (find-txn-by-hash "828ef3b079f9c23829c56fe86e85b4a69d9e06e5b54ea597eef5fb3ffef509fe")
;;       (get-in [:txn/txIns])
;;       (last)
;;       (d/touch)))

(defn parent-txn
  "Find parent-txn for given txIn or txOut entity."
  [txIO]
  (case (some #{:txIn/idx :txOut/idx} (keys txIO))
    :txIn/idx  (first (:txn/_txIns txIO))
    :txOut/idx (first (:txn/_txOuts txIO))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Mass-import constructors ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; - Unfortunately, can't just reduce d/with across cstors
;;   for tempid-lookup since we can't lean on d/with
;;   creating the same :db/ids that the real d/transact
;;   will create, so we need specific cstors that'll maintain
;;   a tempid lookup table. :(

;; (defn imp-construct-txout [idx [tid txOut]]
;;   [{:db/id tid
;;     :txOut/idx idx
;;     :txOut/value (:txOut/value txOut)
;;     :txOut/script (:txOut/script txOut)}])

;; (defn imp-construct-txin [idx [tid txOut]]
;;   [{:db/id eid
;;     :txOut/idx idx
;;     :txOut/value (:txOut/value txOut)
;;     :txOut/script (:txOut/script txOut)}])

;; (defn imp-construct-txn [idx [tid txn]]
;;   {:db/id eid
;;    :txn/hash (:txn/hash txn)
;;    :txn/txOuts (map-indexed imp-construct-txout (:txn/txOuts txn))
;;    :txn/txIns (map-indexed imp-construct-txin (:txn/txIns txn))})

;; (defn imp-construct-block [tid blk]
;;   {:db/id eid
;;    :block/hash (:block/hash blk)
;;    ;; Have to lookup in db and then blockhash->tempid if not there
;;    :block/prevBlock
;;    :block/txns (map-indexed imp-construct-txn (:txns blk))})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Deprecate this function in favor of the more-hilarious
;; constructor hyzhenhok.codec/blk-dtxs.
;;
;; This constructor makes the mistake of using `d/with` in
;; an effort to lookup txouts within the datomic-transaction
;; which I've found out to be unreliable.
;;
;; But it works for the demo for now.
(defn construct-txns
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
               :txn/hash (:txn/hash raw-txn)
               :txn/ver (:txn/ver raw-txn)
               :txn/lockTime (:txn/lockTime raw-txn)
               :txn/txOuts (map-indexed
                            (fn [idx txout]
                              {:db/id (tempid)
                               :txOut/idx idx
                               :txOut/value (long (:txOut/value txout))
                               :txOut/script (:txOut/script txout)})
                            (:txn/txOuts raw-txn))
               :txn/txIns (map-indexed
                           (fn [idx txin]
                             (merge
                              {:db/id (tempid)
                               :txIn/idx idx
                               :txIn/script (:txIn/script txin)
                               :txIn/sequence (:txIn/sequence txin)}
                              ;; :prev-output {:hash "...", :idx 0}
                              ;; We don't care about coinbase outs.
                              (when-not (coinbase? (:prevTxOut txin))
                                ;; And we ignore
                                (when-let [txout
                                           (find-txout-by-hash-and-idx
                                            db
                                            (:txn/hash (:prevTxOut txin))
                                            (:txOut/idx (:prevTxOut txin)))]
                                  {:txIn/prevTxOut (:db/id txout)}))))
                           (:txn/txIns raw-txn))}]
          (recur
           ;; Extend the db so the rest of the txns can see
           ;; this txn with the find-txout-by-hash-and-idx lookup.
           (:db-after (d/with db [constructed-txn]))
           (next raw-txns)
           (conj constructed-txns constructed-txn)
           (inc idx)))))))

(defn construct-block
  ([db block] (construct-block db (tempid) block))
  ([db eid block]
     (merge
      {:db/id eid
       :block/hash (:block/hash block)
       :block/ver (:block/ver block)
       :block/merkleRoot (:block/merkleRoot block)
       :block/time (:block/time block)
       :block/bits (:block/bits block)
       :block/nonce (:block/nonce block)
       :block/txns (construct-txns db (:txns block))}
      (when-let [prev-block (find-block-by-hash
                             db
                             (:prevBlockHash block))]
        {:block/prevBlock (:db/id prev-block)}))))

(defn create-block
  "Returns the created block entity-map or nil if
   a block with this hash already exists."
  [raw-block]
  (when-not (find-block-by-hash (:block/hash raw-block))
    (let [temp-block-eid (tempid)]
      (let [result (->> [(construct-block (get-db)
                                          temp-block-eid
                                          raw-block)]
                        (d/transact (get-conn))
                        (deref))]
        (let [real-block-eid (d/resolve-tempid (:db-after result)
                                               (:tempids result)
                                               temp-block-eid)]
          ;;(println (class (:db-after result)))
          (d/entity (:db-after result) real-block-eid))))))
