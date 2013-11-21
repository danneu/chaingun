(ns hyzhenhok.sandbox
  (:require [hyzhenhok.util :refer :all]
            [hyzhenhok.script :refer :all]
            [hyzhenhok.codec :as codec]
            [hyzhenhok.crypto :as crypto]))

;; ;; 1. Execute :txIn/script (current txn) to get sig and pubkey
;; ;;    onto main stack.

;; ;; (let [;txin-script [...]
;; ;;       txin-evald ['((byte-array 5) (byte-array 5)) '() '()]]
;; ;;   (execute prev-txout-script (execute txin-script)))

;; ;; pubkey 00aabbcc00 [0 170 187 204 0]
;; ;; sig    1122334455 [12 34 51 68 85]

;; ;; (let [txin-state (execute (map into-byte-array (parse (hex->bytes (str "05" "00aabbcc00" "05" "1122334455")))))]
;; ;;   (execute [:op-dup :op-hash160] txin-state))

;; ;; (let [txin-state (execute (parse (hex->bytes (str "05" "00aabbcc00" "05" "1122334455"))))]
;; ;;   (execute [:op-dup :op-hash160] txin-state))



;; ;; [(:op-hash160 (17 34 51 68 85) (17 34 51 68 85) (0 170 187 204 0))]

;; ;; [(list (hex->bytes "aabbcc")) '() '()]

;; ;; [:op-hash160]

;; ;; (crypto/hash160 (hex->bytes "00aabbcc00"))

;; ;; (bytes->hex (crypto/hash160 (hex->bytes "aabbcc")))



;; (comment
;;   (prettify-output2
;;    (execute
;;     ;; txOut/script
;;     [:op-dup
;;      :op-hash160
;;      (hex->bytes "0bfbcadae145d870428db173412d2d860b9acf5e")
;;      :op-equalverify
;;                                         ;:op-checksig
;;      ]
;;     ;; initial state after txIn/script
;;     [(list (hex->bytes "aabbcc") ; pubkey
;;            (hex->bytes "001122") ; sig
;;            ) '() '()])))



;; (defn extract-hash-type [sig]
;;   (case (ubyte (last sig))
;;     0x01 :sighash-all
;;     0x02 :sighash-none
;;     0x03 :sighash-single
;;     0x80 :sighash-anyonecanpay
;;     :sighash-all  ; by default
;;     ))

;; ;(extract-hash-type (into-byte-array [1 2 1]))  ; :sighash-all


;; (comment
;;   (parse
;;    (hex->bytes "4104ae1a62fe09c5f51b13905f07f06b99a2f7159b2225f374cd378d71302fa28414e7aab37397f554a7df5f142c21c1b7303b8a0626f1baded5c72a704f7e6cd84cac"))

;;   (let [txin-script (-> "47304402204e45e16932b8af514961a1d3a1a25fdf3f4f7732e9d624c6c61548ab5fb8cd410220181522ec8eca07de4860a4acdd12909d831cc56cbbac4622082221a8768d1d0901"
;;                         hex->bytes
;;                         parse)
;;         txin-state (execute txin-script)]
;;     (count (first txin-script))  ; 71  (sig is 70 chars of that)
;;     txin-state))

;; (comment
;;   (extract-hash-type txin-script))


;; ;; op-checksig

;; (def txn-sample (-> "0100000001c997a5e56e104102fa209c6a852dd90660a20b2d9c352423edce25857fcd3704000000004847304402204e45e16932b8af514961a1d3a1a25fdf3f4f7732e9d624c6c61548ab5fb8cd410220181522ec8eca07de4860a4acdd12909d831cc56cbbac4622082221a8768d1d0901ffffffff0200ca9a3b00000000434104ae1a62fe09c5f51b13905f07f06b99a2f7159b2225f374cd378d71302fa28414e7aab37397f554a7df5f142c21c1b7303b8a0626f1baded5c72a704f7e6cd84cac00286bee0000000043410411db93e1dcdb8a016b49840f8c53bc1eb68a382e97b1482ecad7b148a6909a5cb2e0eaddfb84ccf9744464f82e160bfa9b8b64f9d4c03f999b8643f656b412a3ac00000000"
;;                      (hex->bytes)))

;; (defn clear-txin-script [txin]
;;   (update-in txin [:script] (constantly (byte-array 1))))

;; (defn clear-txin-scripts [txn]
;;   (update-in txn [:txins] (partial map clear-txin-script)))

;; (comment
;;   ;; (defn checksig [txin-idx state] ...)

;;   ;; return serialized txncopy hash to be checked
;;   ;; with pubkey and sig.
;;   (fn [txin-idx state]  ; map-indexed
;;     (let [world {:txOut/script {}
;;                  :txIn/script {}
;;                  :txn {}}
;;           subscript (:txout-script world)
;;           hash-type (extract-hash-type sig)]
;;       (let [txncopy
;;             {:txn (-> (:txn world)
;;                       ;; Set all txin scripts to 0x00
;;                       (clear-txin-scripts)
;;                       ;; Set curr txin script to subscript
;;                       (assoc-in [:txins txin-idx :script]
;;                                 subscript))
;;              :hash-type hash-type}]
;;         (let [txncopy-bytes (encode txncopy-codec txncopy)
;;               txncopy-hash (crypto/double-sha256
;;                             txncopy-bytes)]
;;           txncopy-hash)))))

;; (gloss.core/defcodec txncopy
;;   (gloss.core/ordered-map
;;    :txn codec/txn-codec
;;    :hash-type :uint32-le))


;; (clear-txin-scripts
;;  (codec/decode codec/txn-codec txn-sample))


;; (assoc-in
;;   {:a [{:b 2} {:c 3}]}
;;   [:a 0 :b]
;;   1)

;; ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; ;; First, we db/construct the block which will match all of
;; ;; the txInps with prevTxOuts. (If this fails, block is
;; ;; immediately rejected)

;; (comment
;;   (defn valid-block? [block]
;;     (and (valid-difficulty? (:block/bits block))
;;          (valid-txns? (:block/txns block))))

;;   (defn valid-txns? [txns]
;;     (map-indexed valid-txn? txns))

;;   (defn valid-txn? [txn-idx txn]
;;     (and (map valid-txin?
;;               (repeat txn)
;;               (:txn/txIns txn))))

;;   ;; just pass in txIn and derive everything else.
;;   (defn valid-txIn? [txn txIn]
;;     (let [txIn-script (:txIn/script txIn)
;;           prevTxOut-script (:txIn/prevTxOut txIn)
;;           world {:txn txn
;;                  :txIn txIn}]
;;       (as-> (execute txIn-script) _
;;            (execute prevTxOut-script _ ))))

;;   (defn valid-txIn? [txIn]
;;     (let [txIn-script (:txIn/script txIn)
;;           prevTxOut-script (:txIn/prevTxOut txIn)

;;       (as-> (execute txIn-script) _
;;            (execute prevTxOut-script _ ))))))

;; ;; World {:txIn-script, :prevTxOut-script, :txn}

















;; (let [txin-state (execute txin-script)]
;;   (execute prev-txout-script :init-state txin-state))

;; ['(:txin-pubkey :txin-sig) '() '()]
;; :op-checksig
;; ;1. Pop them from stack ['() '() '()]
;; ; txin/pubkey = ...
;; ; txin/sig = ...
;; ;2. create `subscript` of :prevTxOut/script from
;; ;  its last :op-codeseparator to the end (or the full script
;; ;  if there's no :op-codeseparator.

;; (.lastIndexOf [:a :b :c "lol" :d :e] :c)  ; 2
;; (.lastIndexOf [:a :b :c "lol" :d :e] :xxx)  ; -1

;; (reverse
;;  (take-while (partial not= :op-codeseparator)
;;              (reverse [:a :b :c :op-codeseparator :d :e :op-codeseparator :f :g])))

;; (reverse
;;  (take-while (partial not= :op-codeseparator)
;;              (reverse [:a :b :c :d :e :f :g])))

;; ;               V       _________________________________
;; (as-> _
;;      (take-while (partial not= :op-checksig) _) )

;; (def script [:a :b :op-codesep :c :d :op-checksig :op-codesep :f])
;; ;(def script [:a :b :c :d :op-checksig :f])

;; (def a (let [script-until-checksig (take-while
;;                                     (partial not= :op-checksig) script)
;;              last-codesep-idx (.lastIndexOf
;;                                script-until-checksig :op-codesep)]
;;          (if (< -1 last-codesep-idx)
;;            (drop (inc last-codesep-idx) script)
;;            ;; Else there are no :codeseps in script
;;            script)))


;; ;; remove all op-codeseps form subscript
;; (def b (remove (partial = :op-codesep) a))

;; b

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; Down here, I'm experimenting with mapping codecs to
;; my Datomic schema.
;;
;; Use cases:
;;
;; - Lemme serialize txncopy in script/op-checksig

(comment
(defcodec block-kodec
  (compile-frame
   (ordered-map
    :block/ver        :uint32-le
    :block/prevBlock  hash-codec
    :block/merkleRoot hash-codec
    :block/time       :uint32-le
    :block/bits       bits-codec
    :block/nonce      :uint32-le
    :block/txns       (repeated txn-codec :prefix var-int-codec))
   ;; Pre-encode
   identity
   ;; Post-decode
   (fn [block]
     (assoc block :block/hash (calc-block-hash block)))))


(defcodec txIn-kodec
  (compile-frame
   (ordered-map
    :prevTxOut    {:txOut/idx :uint32-le
                   :txn/hash hash-codec}
    :txIn/script   script-codec
    :txIn/sequence :uint32-le)
   ;; Pre-encode
   ;; Note: It's pretty bad to bring db in on this, and
   ;; lose symmetry with post-decode, but it'll do for now.
   (fn [txIn]
     ;; Convert :txIn/prevTxOut into {idx, hash} pair.
     (let [txOut (:txIn/prevTxOut txIn)]
       (assoc (into {} txIn)
         :prevTxOut
         {:txOut/idx (:txOut/idx txOut)
          :txn/hash (:txn/hash (db/parent-txn txOut))})))
   ;; Post-decode
   identity))

;; Txn output
(defcodec txOut-kodec
  (ordered-map
   ;; Satoshis (BTC/10^8)
   :txOut/value  :uint64-le
   :txOut/script script-codec))

(defcodec script-kodec
  (compile-frame
   (repeated :ubyte :prefix var-int-codec)
   ;; Pre-encode
   (fn [hex]
     (as-> (hex->bytes hex) _
           (map (partial bit-and 0xff) _)))
   ;; Post-decode
   (fn [byte-seq]
     (bytes->hex
      (byte-array (map unchecked-byte byte-seq))))))

(defcodec txn-kodec
  (compile-frame
   (ordered-map
    :txn/ver      :uint32-le
    :txn/txIns    (repeated txIn-kodec :prefix var-int-codec)
    :txn/txOuts   (repeated txOut-kodec :prefix var-int-codec)
    :txn/lockTime :uint32-le)
   ;; Pre-encode
   (fn [txn]
     (as-> (db/map-all txn) _
           ;; Sort txIns and txOuts by idx and make seqable.
           (update-in _ [:txn/txIns] #(sort-by :txIn/idx %))
           (update-in _ [:txn/txOuts] #(sort-by :txOut/idx %))))
   ;; Post-decode
   (fn [txn]
     (assoc txn :txn/hash (calc-txn-hash txn-kodec)))))
)
