(ns hyzhenhok.models
  (:require [hyzhenhok.util :refer :all]
            [hyzhenhok.toy :as toy]
            ;[hyzhenhok.script :as script]
            ))

;;;; This namespace contains business functions on entities.

;; txout

(defn spent?
  "TxOut has been spent."
  [txout]
  (boolean (:txIn/_prevTxOut txout)))

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
  "Executing the Input script and then the Output script must
   result in a stack where the top value in non-zero (true)."
  [txin]
  (let [in-script (:txIn/script txin)
        out-script (-> (:txIn/prevTxOut txin)
                       :txOut/script)
        world {:txin txin}]
    ;; IIRC I decided to evaluate scripts separately so that
    ;;      I can escape early should in-script force
    (as-> (script/execute (script/parse in-script) world) _
          (script/execute (script/parse out-script) _ world)
          (first _)
          (script/stack-true? _))))

(defn valid-tx? [tx]
  ;; All TxIns must be valid
  (every? identity (map valid-txin? (:txn/txIns tx))))

(defn valid-difficulty? [bits]
  true)

(defn valid-block? [blk]
  (and ;; Difficulty must be valid
       (valid-difficulty? (:block/bits blk))

       ;; All Txs must be valid
       (every? identity (map valid-tx? (:block/txns blk)))))


;; (valid-block? (toy/blk))
