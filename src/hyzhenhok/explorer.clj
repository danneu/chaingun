(ns hyzhenhok.explorer
  (:use [hiccup core def element form page util])
  (:require [hyzhenhok.db :as db]
            [compojure.core :refer :all]
            [compojure.handler :as handler]
            [compojure.route :as route]
            [clj-time.format]
            [clj-time.coerce]
            [ring.adapter.jetty :refer [run-jetty]]))

;; This is the embedded blockchain-explorer that queries
;; the contents of the db.

;; Helpers ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn satoshi->btc [satoshi-val]
  (-> (float satoshi-val)
      (/ 1e8)))

(defn format-instant
  "java.util.Date -> \"YYYY-MM-DD HH:MM:SS\" (UTC)"
  [time]
  (clj-time.format/unparse
   (clj-time.format/formatters :mysql)
   (clj-time.coerce/from-date time)))

(defn link-to-blockexplorer-com [hash]
  (link-to
   (url "http://blockexplorer.com/block/" hash)
   "http://blockexplorer.com/block/" hash))

(defn link-to-blockchain-info [hash]
 (link-to
   (url "https://blockchain.info/block/" hash)
   "https://blockchain.info/block/" hash))

;; Templates ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn show-txn [txn]
  (html
   [:h1 "Txn"]
   [:table
    [:tr [:td ":txn/hash"] [:td (:txn/hash txn)]]
    [:tr [:td ":txn/idx"] [:td (:txn/idx txn)]]
    [:tr [:td ":txn/ver"] [:td (:txn/ver txn)]]
    [:tr [:td ":txn/lockTime"] [:td (:txn/lockTime txn)]]
    [:tr
     [:td "block"]
     [:td (let [block-hash (:block/hash
                            (first (:block/_txns txn)))]
            (link-to (url "/blocks/" block-hash)
                     block-hash))]]]
   [:hr]
   [:h2 ":txn/txIns"]
   [:table
    (for [txin (sort-by :txIn/idx (:txn/txIns txn))]
      (list
       [:tr [:td ":txIn/idx"] [:td (:txIn/idx txin)]]
       [:tr [:td ":txIn/script"] [:td (:txIn/script txin)]]
       [:tr
        [:td ":txIn/sequence"]
        [:td (:txIn/sequence txin)]]
       [:tr
        [:td ":txIn/prevTxOut"]
        [:td (if-let [prev-txout (:txIn/prevTxOut txin)]
               (let [txn (first (:txn/_txOuts prev-txout))]
                 [:ul
                  [:li ":txn/hash: " (link-to (url "/txns/"
                                                   (:txn/hash txn))
                                             (:txn/hash txn))]
                  [:li ":txOut/idx: " (:txOut/idx prev-txout)]])
               "N/A (Coinbase)")]]))]
   [:hr]
   [:h2 ":txn/txOuts"]
   [:table
    (for [txout (sort-by :txOut/idx (:txn/txOuts txn))]
      (list
       [:tr [:td ":txOut/idx"] [:td (:txOut/idx txout)]]
       [:tr
        [:td ":txOut/value"]
        [:td
         (:txOut/value txout)
         (str " ("
              (satoshi->btc (:txOut/value txout))
              " BTC)")]]
       [:tr [:td ":txOut/script"] [:td (:txOut/script txout)]]))]))


(defn show-block [blk]
  (html
   [:h1 "Block " (db/get-block-idx blk)]
   [:table
    [:tr
     [:td ":block/hash"]
     [:td (:block/hash blk)]]
    [:tr
     [:td ":block/prevBlock"]
     [:td (if-let [prev-hash (:block/hash (:block/prevBlock blk))]
            (link-to (url "/blocks/" prev-hash) prev-hash)
            "--")]]
    [:tr
     [:td "nextBlock"]
     [:td (if-let [next-hash (:block/hash
                              (first
                               (:block/_prevBlock blk)))]
            (link-to (url "/blocks/" next-hash) next-hash)
            "--")]]
    [:tr
     [:td ":block/merkleRoot"]
     [:td (:block/merkleRoot blk)]]
    [:tr
     [:td ":block/time"]
     [:td (str (format-instant (:block/time blk))
               " ("
               (-> (:block/time blk) .getTime (/ 1000))
               " seconds)")]]
    [:tr
     [:td ":block/bits"]
     [:td (:block/bits blk)]]
    [:tr
     [:td ":block/nonce"]
     [:td (:block/nonce blk)]]]
   [:hr]
   [:h2 ":block/txns"]
   [:style "ol > li:first-child:after { content:\" (Coinbase)\"; } "]
   [:ol {:start "0"}
    (for [txn (sort-by :txn/idx (:block/txns blk))]
      [:li (link-to (url "/txns/" (:txn/hash txn))
                    (:txn/hash txn))])]
   [:hr]
   [:ul
    [:li (link-to-blockexplorer-com (:block/hash blk))]
    [:li (link-to-blockchain-info (:block/hash blk))]]))

;; Routes ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defroutes app-routes
  (GET "/" []
    (show-block (db/find-genesis-block)))
  (GET "/blocks/:hash" [hash]
    (show-block (db/find-block-by-hash hash)))
  (GET "/txns/:hash" [hash]
    (show-txn (db/find-txn-by-hash hash)))
  (route/resources "/")
  (route/not-found "Not Found"))

;; Entry point ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def app
  (handler/site app-routes))

(defn start-server [port]
  (run-jetty app {:port port}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn toy []
  (db/find-genesis-block))
