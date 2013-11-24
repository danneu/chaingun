(ns hyzhenhok.keyx-test
  (:require [hyzhenhok.keyx :refer :all]
            [expectations :refer :all]
            [hyzhenhok.util :refer :all]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; get-pubkey-format
;;
;; - Should correctly determine pubkey format.
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(expect :hex-compressed
  (get-pubkey-format "02cfde38ea95953b07bf92eb12d95f85c9ab5e471a23ee3b8ee060eda6ebfff40f"))

(expect :bin-compressed
  (-> "02cfde38ea95953b07bf92eb12d95f85c9ab5e471a23ee3b8ee060eda6ebfff40f"
      hex->bytes
      get-pubkey-format))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; ->pubkey
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; hex-compressed -> ECPoint

(expect {:x 94021392527292805460624339020319634935466418653831098364395079150496827569167
         :y 83909795693933244970719890159850247224779701487933065303611579887229122694032}
  (-> "02cfde38ea95953b07bf92eb12d95f85c9ab5e471a23ee3b8ee060eda6ebfff40f"
      ->pubkey
      (select-keys [:x :y])))

;; privkey -> pubkey

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; pubkey?
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Point format
(expect true
  (-> "02cfde38ea95953b07bf92eb12d95f85c9ab5e471a23ee3b8ee060eda6ebfff40f"
      ->pubkey
      pubkey?))

;; :bin-compressed format
(expect true
  (-> "02cfde38ea95953b07bf92eb12d95f85c9ab5e471a23ee3b8ee060eda6ebfff40f"
      hex->bytes
      pubkey?))

;; :hex-compressed format
(expect true
  (-> "02cfde38ea95953b07bf92eb12d95f85c9ab5e471a23ee3b8ee060eda6ebfff40f"
      pubkey?))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; ->address
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(expect "1C7zdTfnkzmr13HfA2vNm5SJYRK6nEKyq8"
  (-> "0378d430274f8c5ec1321338151e9f27f4c676a008bdf8638d07c0b6be9ab35c71"
      ->pubkey
      ->address))

;; On testnet3 ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(expect "mmPEvbJiY999qdDXCMfTefJmHuspzzVN2w"
  (-> "02cfde38ea95953b07bf92eb12d95f85c9ab5e471a23ee3b8ee060eda6ebfff40f"
      ->pubkey
      (->address :testnet3)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; ->priv
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(let [canonical-privkey 88985120633792790105905686761572077713049967498756747774697023364147812997770]
  ;; from hex-compressed
  (expect canonical-privkey
    (->privkey "c4bbcb1fbec99d65bf59d85c8cb62ee2db963f0fe106f483d9afa73bd4e39a8a"))
  ;; from der-hex-compressed
  (expect canonical-privkey
    (->privkey
     "3081d30201010420c4bbcb1fbec99d65bf59d85c8cb62ee2db963f0fe106f483d9afa73bd4e39a8aa08185308182020101302c06072a8648ce3d0101022100fffffffffffffffffffffffffffffffffffffffffffffffffffffffefffffc2f300604010004010704210279be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798022100fffffffffffffffffffffffffffffffebaaedce6af48a03bbfd25e8cd0364141020101a1240322000378d430274f8c5ec1321338151e9f27f4c676a008bdf8638d07c0b6be9ab35c71")))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; get-privkey-format
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(expect :hex-compressed
  (-> "0378d430274f8c5ec1321338151e9f27f4c676a008bdf8638d07c0b6be9ab35c71"
      get-privkey-format))

(expect :bin-compressed
  (-> "0378d430274f8c5ec1321338151e9f27f4c676a008bdf8638d07c0b6be9ab35c71"
      hex->bytes
      get-privkey-format))

(expect :hex-uncompressed
  (-> "c4bbcb1fbec99d65bf59d85c8cb62ee2db963f0fe106f483d9afa73bd4e39a8a"
      get-privkey-format))

(expect :bin-uncompressed
  (-> "c4bbcb1fbec99d65bf59d85c8cb62ee2db963f0fe106f483d9afa73bd4e39a8a"
      hex->bytes
      get-privkey-format))

(expect :wif-str-compressed
  (get-privkey-format "L3p8oAcQTtuokSCRHQ7i4MhjWc9zornvpJLfmg62sYpLRJF9woSu"))

(expect :wif-bin-compressed
  (-> "L3p8oAcQTtuokSCRHQ7i4MhjWc9zornvpJLfmg62sYpLRJF9woSu"
      str->bytes
      get-privkey-format))

(expect :wif-str-uncompressed
  (get-privkey-format "5KJvsngHeMpm884wtkJNzQGaCErckhHJBGFsvd3VyK5qMZXj3hS"))

(expect :wif-bin-uncompressed
  (-> "5KJvsngHeMpm884wtkJNzQGaCErckhHJBGFsvd3VyK5qMZXj3hS"
      str->bytes
      get-privkey-format))

(expect :der-hex-uncompressed
  (-> "308201130201010420c4bbcb1fbec99d65bf59d85c8cb62ee2db963f0fe106f483d9afa73bd4e39a8aa081a53081a2020101302c06072a8648ce3d0101022100fffffffffffffffffffffffffffffffffffffffffffffffffffffffefffffc2f300604010004010704410479be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798483ada7726a3c4655da4fbfc0e1108a8fd17b448a68554199c47d08ffb10d4b8022100fffffffffffffffffffffffffffffffebaaedce6af48a03bbfd25e8cd0364141020101a1440342000478d430274f8c5ec1321338151e9f27f4c676a008bdf8638d07c0b6be9ab35c71a1518063243acd4dfe96b66e3f2ec8013c8e072cd09b3834a19f81f659cc3455"
      get-privkey-format))

(expect :der-bin-uncompressed
  (-> "308201130201010420c4bbcb1fbec99d65bf59d85c8cb62ee2db963f0fe106f483d9afa73bd4e39a8aa081a53081a2020101302c06072a8648ce3d0101022100fffffffffffffffffffffffffffffffffffffffffffffffffffffffefffffc2f300604010004010704410479be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798483ada7726a3c4655da4fbfc0e1108a8fd17b448a68554199c47d08ffb10d4b8022100fffffffffffffffffffffffffffffffebaaedce6af48a03bbfd25e8cd0364141020101a1440342000478d430274f8c5ec1321338151e9f27f4c676a008bdf8638d07c0b6be9ab35c71a1518063243acd4dfe96b66e3f2ec8013c8e072cd09b3834a19f81f659cc3455"
      hex->bytes
      get-privkey-format))

(expect :der-hex-compressed
  (get-privkey-format "3081d30201010420c4bbcb1fbec99d65bf59d85c8cb62ee2db963f0fe106f483d9afa73bd4e39a8aa08185308182020101302c06072a8648ce3d0101022100fffffffffffffffffffffffffffffffffffffffffffffffffffffffefffffc2f300604010004010704210279be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798022100fffffffffffffffffffffffffffffffebaaedce6af48a03bbfd25e8cd0364141020101a1240322000378d430274f8c5ec1321338151e9f27f4c676a008bdf8638d07c0b6be9ab35c71"))

(expect :der-bin-compressed
  (-> "3081d30201010420c4bbcb1fbec99d65bf59d85c8cb62ee2db963f0fe106f483d9afa73bd4e39a8aa08185308182020101302c06072a8648ce3d0101022100fffffffffffffffffffffffffffffffffffffffffffffffffffffffefffffc2f300604010004010704210279be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798022100fffffffffffffffffffffffffffffffebaaedce6af48a03bbfd25e8cd0364141020101a1240322000378d430274f8c5ec1321338151e9f27f4c676a008bdf8638d07c0b6be9ab35c71"
      hex->bytes
      get-privkey-format))

;; Preserve compression ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(expect "mmPEvbJiY999qdDXCMfTefJmHuspzzVN2w"
  (-> "3081d302010104207b1e8009bd645d64b3624de44dde071b3d05af9969c7c858ceb06f3a1260e78da08185308182020101302c06072a8648ce3d0101022100fffffffffffffffffffffffffffffffffffffffffffffffffffffffefffffc2f300604010004010704210279be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798022100fffffffffffffffffffffffffffffffebaaedce6af48a03bbfd25e8cd0364141020101a12403220002cfde38ea95953b07bf92eb12d95f85c9ab5e471a23ee3b8ee060eda6ebfff40f"
      (->address :testnet3)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; generate-pair
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(let [test-pair (generate-pair)]
  ;; privkey and pubkey convert into same address
  (expect (->address (:privkey test-pair))
    (->address (:pubkey test-pair)))

  ;; pubkey compressed by default
  (expect true (compressed? (:pubkey test-pair))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Random tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; http://blockexplorer.com/tx/f4184fc596403b9d638783cf57adfe4c75c605f6356fbc91338530e9831e9e16
;; - Should generate correct address from a blk170 txout script.
(expect "1Q2TWHE3GMdB6BZKafqwxXtWAWgFt5Jvm3"
  (->address "04ae1a62fe09c5f51b13905f07f06b99a2f7159b2225f374cd378d71302fa28414e7aab37397f554a7df5f142c21c1b7303b8a0626f1baded5c72a704f7e6cd84c"))
