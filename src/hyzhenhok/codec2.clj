(ns hyzhenhok.codec2
  (:require [hyzhenhok.util :refer :all
             :exclude [contiguous byte-count]]
            [hyzhenhok.crypto :as crypto]
            [clojure.java.io :as io]
            [gloss.io :refer :all]
            [gloss.core :refer :all]
            [clojure.string :as str]
            ;; Ideally these wouldn't be here.
            [hyzhenhok.db :as db]
            [datomic.api :as d]
            ;[clojure.core.typed :refer :all]
            [clojure.test :refer :all]
            )
  (:import [clojure.lang IPersistentMap PersistentArrayMap]
           [java.util Date]
           [java.nio ByteBuffer ByteBuffer]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Codec utils;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Used to convert Long<->Instant (:block/time)
;; - Need to ensure roundtrip parity between these two
;;   functions or else de/serialization roundtrips
;;   will be slightly different for :time attributes!

(defn seconds->instant [secs]
  (Date. (* 1000 secs)))

(defn instant->seconds [inst]
  (-> (.getTime inst) (quot 1000)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Kinda dumb.
(defn get-entity-type [m]
  (cond
   (:txIn/idx m)            :txin
   (:txOut/idx m)           :txout
   (:txn/ver m)             :txn
   (:block/txns m)          :block
   (and
    (:block/ver m)
    (nil? (:block/txns m))) :block-header))

(declare TxInCodec TxOutCodec TxnCodec BlockCodec BlockHeaderCodec
         HashCodec VarIntCodec VarStrCodec MagicCodec)

(defn codec-lookup [ent-type]
  (case ent-type
    :txin         TxInCodec
    :txout        TxOutCodec
    :hash         HashCodec
    :magic        MagicCodec
    :var-int      VarIntCodec
    :var-str      VarStrCodec
    :txn          TxnCodec
    :block        BlockCodec
    :block-header BlockHeaderCodec))

(defn encode-entity [ent-type m]
  (-> (codec-lookup ent-type)
      (gloss.io/encode m)))

(defn decode-entity [ent-type m]
  (-> (codec-lookup ent-type)
      (gloss.io/decode m)))

(defn encode-block [m] (encode-entity :block m))
(defn decode-block [m] (decode-entity :block m))
(defn encode-txn [m] (encode-entity :txn m))
(defn decode-txn [m] (decode-entity :txn m))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(declare TxnCodec BlockHeaderCodec)

(defn calc-txn-hash [txn]
  (as-> (encode-entity :txn txn) _
        (buf->bytes _)
        (crypto/hash256 _)
        (reverse-bytes _)))

;; Even though :txn-count isn't hashed, we set it so that
;; the codec can actually encode it even though we end up
;; only taking the first 80 bytes which doesn't even
;; include the :txn-count.
(defn calc-block-hash [blk]
  (let [blk-header (merge (select-keys
                           blk [:block/ver
                                :prevBlockHash
                                :block/merkleRoot
                                :block/time
                                :block/bits
                                :block/nonce])
                          {:txnCount 0})]
    (as-> (encode-entity :block-header blk-header) _
          (contiguous _)
          (.array ^ByteBuffer _)
          (take-bytes 80 _)
          (crypto/hash256 _)
          (reverse-bytes _))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defcodec ProtocolVerCodec :uint32-le)

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
    :block/ver         ProtocolVerCodec    ; 4
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
           (assoc _ :block/hash (calc-block-hash _))))))

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
     (assoc txn-map :txn/hash (calc-txn-hash txn-map)))))

(defcodec BlockCodec
  (compile-frame
   (ordered-map
    :block/ver         :uint32-le
    ;; Converted to :block/prevBlock (ref) when put into db.
    :prevBlockHash     HashCodec
    :block/merkleRoot  HashCodec
    :block/time        :uint32-le
    :block/bits        BitsCodec
    :block/nonce       :uint32-le
    :block/txns        (repeated TxnCodec :prefix VarIntCodec))
   ;; Pre-encode
   (fn [block]
     ;; To make this work on both entity-maps (from db) and
     ;; regular maps, if this is an entity-map, we get the
     ;; parent block-hash from its prev-block ref pointer.
     ;; If this block map isn't from the db, then it should
     ;; already have :prevBlockHash.
     (-> (if (:prevBlockHash block)
           block
           (assoc (into {} block)
                  :prevBlockHash
                  (-> block :block/prevBlock :block/hash)))
         (update-in [:block/time] (partial instant->seconds))))
   ;; Post-decode
   (fn [block]
     (as-> block _
           (update-in _ [:block/time] (partial seconds->instant))
           (assoc _ :block/hash (calc-block-hash _))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defcodec BlkDatCodec
  (ordered-map
   :magic MagicCodec
   :size :uint32-le
   :block BlockCodec))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Touch-all ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; The intent of this touch-all function is to expand
;; entity-maps into real persistent maps so that
;; entity-maps can be used with anything that expects
;; a map.
;;
;; In particular, I want to be able to do a
;; [Gloss serialize]<->[DB entity-map] round-trip as easily
;; as possible for now.

;; TODO: Generalize it. Make it do a walk to look for
;;       EntityMaps and then d/touch them.

(defmulti touch-all get-entity-type)

(defmethod touch-all :txin [m]
  (-> (into {} m)
      ;; Expand :txIn/prevTxOut
      (update-in [:txIn/prevTxOut] (partial (comp d/touch)))))

(defmethod touch-all :txn [m]
  (-> (into {} m)
      ;; Expand :txn/txIns
      (update-in [:txn/txIns]
                 (partial map touch-all))
      ;; Expand :txn/txOuts
      (update-in [:txn/txOuts]
                 (partial map (comp (partial into {}))))))

(defmethod touch-all :block [m]
  (-> (into {} m)
      ;; Assoc :prevBlockHash
      ;;(assoc :prevBlockHash (db/get-prev-block-hash m))
      ;; Expand :block/prevBlock
      (update-in [:block/prevBlock]
                 (partial (comp (partial into {}) d/touch)))
      ;; Expand :block/txns
      (update-in [:block/txns]
                 (partial map (comp touch-all d/touch)))))

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
  (->> (resource-bytes filename)
       (gloss.io/lazy-decode-all BlkDatCodec)))

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

;; Gotta differentiate the lingo:
;; - dtx: Datomic transaction
;; - txn: Bitcoin transaction
;; - txin: Bitcoin transaction input
;; - txout: Bitcoin transaction output

;; #1: "Elapsed time: 97106.002 msecs"  naive datalog queries
;; #2: "Elapsed time: 17032.147 msecs"  by raw-idx
;; #3: no "....Transacting _" println "Elapsed time: 15230.914 msecs"
;; (above is 100 per batch)
;; #4: 1000 per batch "Elapsed time: 15596.059 msecs"
;; #5: 500 per batch "Elapsed time: 15178.628 msecs"
;;                   "Elapsed time: 15786.408 msecs"

(defn construct-blks
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
      (let [blk-tempid (db/gen-tempid)]
        ;(print ".") (flush)
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
                (let [dtx-batch (construct-blks db
                                                blk-frame-batch)]
                  ;; Output every time we're actually saving
                  ;; blocks to the database.
                  (let [curr-count (-> (swap! counter inc)
                                       (* per-batch)
                                       (+ blk-count))]
                    (print "\r" curr-count " ") (flush))
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
                   ;(take 10000)
                   (map :block)
                   (drop (- blk-count (mod blk-count per-batch)))
                   (partition per-batch)
                   (pmap doall)))))
  (println "\nBlocks in database:" (db/get-block-count)))