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

;; Validation (pseudocode) ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; (defn valid-txin? [tx txin]
;;   (let [txin-script (:txIn/script txin)
;;         prev-txout-script (-> (:txIn/prevTxOut txin)
;;                               :txOut/script)
;;         world {:txin txin}]
;;     (as-> (script/execute txin-script world) _
;;           (script/execute prev-txout-script _ world))))

;; (defn valid-tx? [tx])

;; (defn valid-block? [blk]
;;   (and (valid-difficulty? (:block/bits block))
;;        (every? identity (map valid-txn? (:block/txns block)))))
