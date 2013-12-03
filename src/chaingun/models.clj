(ns chaingun.models
  (:require [chaingun.util :refer :all]
            [chaingun.toy :as toy]
            [chaingun.script :as script]
            ))

;;;; This namespace contains business functions
;;;; on datomic db entities.
;;;;
;;;; Should prob be named 'entities', not models.
;;;;
;;;; I really like this namespace - the idea of
;;;; reducing func i/o to entities makes it easy
;;;; to avoid circular deps.

;; TODO: Move entity-type stuff here.

;; FIXME: Stolen from db namespace.
;;        Entity funcs should exist here.
(defn parent-tx
  "Find parent-tx for given txIn or txOut entity."
  [txIO]
  (case (some #{:txIn/idx :txOut/idx} (keys txIO))
    :txIn/idx  (first (:txn/_txIns txIO))
    :txOut/idx (first (:txn/_txOuts txIO))))

(defn coinbase-tx? [tx]
  (java.util.Arrays/equals (:txn/hash tx) (byte-array 32)))

(defn coinbase-txout? [txout]
  (coinbase-tx? (parent-tx txout)))

(defn prev-txout [txin]
  (first (:txIn/_prevTxOut txin)))

(defn genesis-block? [blk]
  (zero? (:block/idx blk)))

(defn parent-block [tx]
  (first (:block/_txns tx)))

;; txout

(defn spent?
  "TxOut has been spent. The coinbase singleton txout is
   never considered spent. Every coinbase TxIn points to it."
  [txout]
  (or (not (coinbase-txout? txout))
      (boolean (:txIn/_prevTxOut txout))))

(def unspent? (complement spent?))

;; addr

(defn get-balance
  "Spendable BTC balance available at an address."
  [addr]
  (->> (:addr/txOuts addr)
       (filter unspent?)
       (map :txOut/value)
       (reduce +)))

;; tx

(defn get-volume
  "Total BTC moved in this transaction."
  [tx]
  (->> (:txn/txOuts tx)
       (map :txOut/value)
       (reduce +)))

;; Validation (pseudocodey) ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn valid-txin?
  [txin]
  (and ;; Check: PrevTxOut should be unspent
       ;; I can't map this over the db's blockchain as I develop
       ;; these validations since this check would prevent
       ;; my blockchain from validating as txouts, of course,
       ;; are spent by future blocks. FIXME.
       ;; (unspent? (:txIn/prevTxOut txin))

       ;; Check: (concat InScript OutScript) must eval to valid
       ;; Perhaps some logic can be reloc to script
       (let [in-script (:txIn/script txin)
             out-script (-> (:txIn/prevTxOut txin)
                            :txOut/script)
             world {:txin txin}]
         ;; IIRC I decided to evaluate scripts separately so that
         ;;      I can escape early should in-script force
         (as-> (script/execute (script/parse in-script) world) _
               (script/execute (script/parse out-script) _ world)
               (do (println _) _)
               (first _)
               (script/stack-true? _)))))

(defn valid-tx? [tx]
  (and ;; Check: All TxIns must be valid
       (every? identity (map valid-txin? (:txn/txIns tx)))

       ;; Check: Sum outputs value must >= Inputs value
       true))

(defn valid-difficulty? [bits]
  true)

(defn valid-block? [blk]
  (and ;; Check: Block hash must be valid
       true

       ;; Check: Difficulty must be valid
       (valid-difficulty? (:block/bits blk))

       ;; Check: All Txs must be valid
       (every? identity (map valid-tx? (:block/txns blk)))))

;; (valid-block? (toy/blk))
