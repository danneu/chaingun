(ns hyzhenhok.explorer
  (:use [hiccup core def element form page util])
  (:require [hyzhenhok.db :as db]
            [hyzhenhok.util :refer :all]
            [hyzhenhok.script :as script]
            [hyzhenhok.codec2 :as codec]
            [hyzhenhok.models :as models]
            [compojure.core :refer :all]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [compojure.handler :as handler]
            [compojure.route :as route]
            [clj-time.format]
            [clj-time.coerce]
            [ring.adapter.jetty :refer [run-jetty]]))

;; This is the embedded blockchain-explorer that queries
;; the contents of the db.

;; Helpers ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn render-script [script]
  (->> (for [item (script/parse script)]
         (if (keyword? item)
           [:span.label.label-info (str item)]
           [:span.label.label-default (ubytes->hex item)]))
       (interpose " ")))

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

;; Layout ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn layout [template]
  (html5
   [:head
    [:title "Blockwizard"]
    (include-css "//netdna.bootstrapcdn.com/bootstrap/3.0.2/css/bootstrap.min.css")]
   [:body
    [:div.container

     ;; Navbar

     [:nav.navbar.navbar-default
      [:div.navbar-header
       (link-to {:class "navbar-brand"} "/" "Blockwizard")]
      [:form.navbar-form {:role "search"
                          :action "/search"
                          :method "post"}
       [:div.form-group {:style "width: 350px"}
        [:input {:type "text"
                 :name "term"
                 :class "form-control"
                 :placeholder "Block height, block hash, tx hash, or address"}]]
       [:button.btn.btn-default {:type "submit"} "Search"]]]

     [:div template]


     ]]))

;; Templates ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; While it's not quite ready, I can show scripts in parsed form.

(defn show-txn [txn]
  (html

   ;; Breadcrumbs ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

   [:ol.breadcrumb
    [:li (link-to "/" "Main chain")]
    [:li
     (let [block (models/parent-block txn)]
       (link-to (url "/blocks/" (-> (:block/hash block)
                                    bytes->hex))
                (str "Block " (:block/idx block))))]
    [:li.active "Tx " (:txn/idx txn)]]

   ;; Page header ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

   [:h1 "Tx "
    [:small (-> (:txn/hash txn) bytes->hex)]
    ;[:small (bytes->hex (:block/hash blk))]
    ]


   [:table.table.table-condensed
    [:tr
     [:td "&uarr; Block"]
     [:td (if-let [block-hash (-> (:block/_txns txn)
                                  first
                                  :block/hash)]
            (let [block-hex (bytes->hex block-hash)]
              (link-to (url "/blocks/" block-hex) block-hex))
            "--")]]
    [:tr
     [:td "Version"]
     [:td (:txn/ver txn)]]
    [:tr
     [:td "Lock time"]
     [:td (:txn/lockTime txn)]]
    ]

   [:h2 "Inputs " [:small (count (:txn/txIns txn))]]

   (for [txin (sort-by :txIn/idx (:txn/txIns txn))]
     [:div.panel.panel-default
      [:div.panel-body {:style "overflow: scroll"}
       [:table.table.table-condensed
        [:tr
         [:td "&larr; Prev output"]
         [:td
          (let [prev-out (:txIn/prevTxOut txin)]
            (if (models/coinbase-txout? prev-out)
              "(Coinbase)"
              (let [prev-tx (models/parent-tx prev-out)
                    prev-tx-hex (-> (:txn/hash prev-tx)
                                    bytes->hex)]
                (link-to (url "/txs/" prev-tx-hex)
                         prev-tx-hex))))]]
        [:tr
         [:td "Script"]
         [:td (render-script (:txIn/script txin))]]]]])

   [:h2 "Outputs " [:small (count (:txn/txOuts txn))]]

   (for [txout (sort-by :txOut/idx (:txn/txOuts txn))]
     [:div.panel.panel-default
      [:div.panel-body {:style "overflow: scroll"}
       [:table.table.table-condensed
        [:tr
         [:td "Value"]
         [:td (satoshi->btc (:txOut/value txout))]]
        [:tr
         [:td "Script"]
         [:td (render-script (:txOut/script txout))]]]]])))

(defn show-block [blk]
  (html

   ;; Breadcrumbs
   [:ol.breadcrumb
    [:li (link-to "/" "Main chain")]
    [:li.active "Block " (:block/idx blk)]]

   ;; Blockchain pagination ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

   [:ul.pager

    ;; Prev block
    (if-let [prev-hash (-> (:block/prevBlock blk)
                           :block/hash)]
      (let [prev-hex (bytes->hex prev-hash)]
        [:li.previous
         (link-to (url "/blocks/" prev-hex) "&larr; Prev block")])
      [:li.previous.disabled
       (link-to "/" "&larr; Prev block")])

    ;; Next block
    (if-let [next-hash (-> (:block/_prevBlock blk)
                           first
                           :block/hash)]
      (let [next-hex (bytes->hex next-hash)]
        [:li.next
         (link-to (url "/blocks/" next-hex)
                  "Next block &rarr;")])
      [:li.next.disabled (link-to "#" "Next block")])]

   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

   ;; I only want to show this on the "homepage"
   (when (models/genesis-block? blk)
     [:div.links-of-interest.well
      [:h4 "Links of interest"]
      [:ul
       [:li
        "Genesis block: "
        (link-to (url "/blocks/by-idx/0")
                 "Block 0")]
       [:li
        "Latest block: "
        (let [latest-blk-idx (dec (db/get-block-count))]
          (link-to (url "/blocks/by-idx/" latest-blk-idx)
                   "Block " latest-blk-idx))]
       [:li
        "First non-coinbase tx: "
        (link-to (url "/blocks/by-idx/170")
                 "Block 170, Tx 1")]
       [:li
        "First pay-to-pubkey160: "
        (link-to (url "/txs/6f7cf9580f1c2dfb3c4d5d043cdbb128c640e3f20161245aa7372e9666168516")
                 "Block 728, Tx 1, Output 0")]]])

   ;; Page header ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

   [:h1 "Block " (:block/idx blk) " "
    [:small (bytes->hex (:block/hash blk))]]


   [:table.table.table-condensed
    [:tr
     [:td "&larr; Prev block"]
     [:td (if-let [prev-hash (:block/hash
                               (:block/prevBlock blk))]
            (link-to (url "/blocks/"
                          (bytes->hex prev-hash))
                     (bytes->hex prev-hash))
            "--")]]
    [:tr
     [:td "Next block &rarr;"]
     [:td (if-let [next-hash (:block/hash
                               (first
                                (:block/_prevBlock blk)))]
            (link-to (url "/blocks/"
                          (bytes->hex next-hash))
                     (bytes->hex next-hash))
            "--")]]
    [:tr
     [:td "Time"]
     [:td (str (format-instant (:block/time blk))
               ;; " ("
               ;; (-> (:block/time blk) .getTime (/ 1000))
               ;; " seconds)"
               )]]
    [:tr
     [:td "Merkle root"]
     [:td (bytes->hex (:block/merkleRoot blk))]]

    [:tr
     [:td "Bits"]
     [:td (bytes->hex (:block/bits blk))]]

    [:tr
     [:td "Nonce"]
     [:td (:block/nonce blk)]]

    ]

   ;; Transactions ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

   [:h2 "Transactions "
    [:small (count (:block/txns blk))]]

   (for [tx (sort-by :txn/idx (:block/txns blk))
         :let [tx-hash (-> (:txn/hash tx) bytes->hex)]]
     [:div.panel.panel-default
      [:div.panel-heading
       (:txn/idx tx) ". "
       (link-to (url "/txs/" tx-hash) tx-hash)]
      [:div.panel-body
       [:div.row
        [:div.col-xs-6
         (if (zero? (:txn/idx tx))
           "(Coinbase transaction)"
           [:ul
            (for [txin (:txn/txIns tx)
                  :let [addrs (->> (:txIn/prevTxOut txin)
                                   :addr/_txOuts
                                   (map :addr/b58))]
                  :when (not-empty addrs)
                  addr addrs]
              [:li (link-to (url "/addrs/" addr) addr)])])]
        [:div.col-xs-6
         [:ul
          (for [txout (:txn/txOuts tx)
                :let [btc (-> (:txOut/value txout)
                              satoshi->btc)
                      addrs (->> (:addr/_txOuts txout)
                                 (map :addr/b58))]
                :when (not-empty addrs)
                addr addrs]
            [:li
             (link-to (url "/addrs/" addr) addr)
             " (" btc " BTC)"])]
         ]]]])

   ;; [:hr]
   ;; [:ul
   ;;  [:li (link-to-blockexplorer-com
   ;;        (bytes->hex (:block/hash blk)))]
   ;;  [:li (link-to-blockchain-info
   ;;        (bytes->hex (:block/hash blk)))]]
   ;; [:hr]
   ;; (str "Total blocks: " (db/get-block-count))
   ))

;; Routes ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn hex-string? [s]
  (boolean (and (even? (count s))
                (re-find #"^[0-9A-Fa-f]+$" s))))

(defn omnimatch [term]
  (cond
   ;; :block/idx
   (number? term) (db/find-block-by-idx term)
   ;; :block/hash or :txn/hash
   (hex-string? term) (or (db/find-block-by-hash term)
                          (db/find-txn-by-hash term))
   ;; :addr/b58
   :else (db/find-addr term)))

(defroutes app-routes
  (GET "/" []
    (layout (show-block (db/find-genesis-block))))
  (POST "/search" [term]
    (let [term (edn/read-string term)]
      (when-let [entity (omnimatch term)]
        (case (codec/get-entity-type entity)
          :block (layout (show-block entity))))))
  (context "/blocks" []
    (GET "/by-idx/:idx" [idx]
      (-> (Integer/parseInt idx)
          db/find-block-by-idx
          show-block
          layout))
    (GET "/:hash" [hash]
      (-> (db/find-block-by-hash hash)
          show-block
          layout)))
  (GET "/txs/:hash" [hash]
    (layout (show-txn (db/find-txn-by-hash hash))))
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
