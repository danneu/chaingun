(ns hyzhenhok.models
  (:require [hyzhenhok.util :refer :all]
            [hyzhenhok.toy :as toy]
            [hyzhenhok.script :as script]))

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
