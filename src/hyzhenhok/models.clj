(ns hyzhenhok.models
  (:require [hyzhenhok.util :refer :all]
            [hyzhenhok.toy :as toy]
            [hyzhenhok.db :as db]))

;; This namespace contains business functions on entities.

(defn spent? [txout]
  (boolean (:txIn/_prevTxOut txout)))

(def unspent? (complement spent?))

(defn balance [addr]
  (->> (:addr/txOuts addr)
       (filter unspent?)
       (map :txOut/value)
       (reduce +)))
