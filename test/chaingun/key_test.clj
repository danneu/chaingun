(ns chaingun.key-test
  (:require [expectations :refer :all]
            [chaingun.key :refer :all]
            [chaingun.curve :as curve]
            [chaingun.util :refer :all]
            [chaingun.crypto :as crypto]))

;; ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; ;; Priv transcoding ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; ;; Uncompressed
;; (let [priv-hex "c4bbcb1fbec99d65bf59d85c8cb62ee2db963f0fe106f483d9afa73bd4e39a8a"]
;;   ;; :wif
;;   (expect "5KJvsngHeMpm884wtkJNzQGaCErckhHJBGFsvd3VyK5qMZXj3hS"
;;     (:value (transcode-priv priv-hex :wif)))
;;   ;; :hex -> :wif -> :hex roundtrip
;;   (expect priv-hex
;;     (-> priv-hex
;;         (transcode-priv :wif)
;;         :value
;;         (transcode-priv :hex)
;;         :value)))

;; (expect #{:uncompressed :hex}
;;   (set (vals (meta (determine-priv "0c28fca386c7a227600b2fe50b7cae11ec86d3bf1fbe471be89827e19d72aa1d")))))

;; (expect #{:compressed :wif}
;;   (set (vals (meta (determine-priv "L3p8oAcQTtuokSCRHQ7i4MhjWc9zornvpJLfmg62sYpLRJF9woSu")))))

;; (expect #{:uncompressed :wif}
;;   (set (vals (meta (determine-priv "5KJvsngHeMpm884wtkJNzQGaCErckhHJBGFsvd3VyK5qMZXj3hS")))))

;; (expect #{:uncompressed :der}
;;   (set (vals (meta (determine-priv (hex->bytes "308201130201010420b1a802a83ad5e0d691c63de343dde334b49fce29d7e9de36cf29c53843e499e4a081a53081a2020101302c06072a8648ce3d0101022100fffffffffffffffffffffffffffffffffffffffffffffffffffffffefffffc2f300604010004010704410479be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798483ada7726a3c4655da4fbfc0e1108a8fd17b448a68554199c47d08ffb10d4b8022100fffffffffffffffffffffffffffffffebaaedce6af48a03bbfd25e8cd0364141020101a144034200040a6140fa7c984ba9410414a35f21144d547ada5ec797fb587833462dff1b8655e09f59d9ac3ff8c87b51e1da7284d075f88847d41d708a77b1721486ac9434d7"))))))

;; (expect #{:compressed :der}
;;   (set (vals (meta (determine-priv (hex->bytes "3081d30201010420b1a802a83ad5e0d691c63de343dde334b49fce29d7e9de36cf29c53843e499e4a08185308182020101302c06072a8648ce3d0101022100fffffffffffffffffffffffffffffffffffffffffffffffffffffffefffffc2f300604010004010704210279be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798022100fffffffffffffffffffffffffffffffebaaedce6af48a03bbfd25e8cd0364141020101a124032200030a6140fa7c984ba9410414a35f21144d547ada5ec797fb587833462dff1b8655"))))))

;; (expect #{:compressed :biginteger}
;;   (set (vals (meta (determine-priv (:priv (generate-pair)))))))

;; ;; ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; ;; ;; BitcoinJ test: testSignatures() ;;;;;;;;;;;;;;;;;;;;;;;;;
;; ;; ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; ;; Test that we can sign and verify with priv-key.
;; (expect true
;;   (let [priv (-> "180cb41c7c600be951b5d3d0a7334acc7506173875834f7a6c4c786a28fcbb19"
;;                  (transcode-priv :biginteger)
;;                  :value)
;;         zero-hash (byte-array 32)
;;         sig-bytes (-> (sign zero-hash priv) (sigmap->der))]
;;     (verify zero-hash sig-bytes (-> (priv->pub priv)
;;                                     (transcode-pub :bin)
;;                                     :value))))

;; ;; Test interop with sig from elsewhere.
;; (expect true
;;   (let [priv (-> "180cb41c7c600be951b5d3d0a7334acc7506173875834f7a6c4c786a28fcbb19"
;;                  (transcode-priv :biginteger)
;;                  :value)
;;         sig (hex->bytes "3046022100dffbc26774fc841bbe1c1362fd643609c6e42dcb274763476d87af2c0597e89e022100c59e3c13b96b316cae9fa0ab0260612c7a133a6fe2b3445b6bf80b3123bf274d")
;;         zero-hash (byte-array 32)]
;;     (verify zero-hash sig (-> (priv->pub priv)
;;                               (transcode-pub :bin)
;;                               :value))))

;; ;; ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; ;; ;; BitcoinJ: testASN1Roundtrip() ;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; ;; ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; ;; Test priv->asn1->priv roundtrip.
;; (expect true
;;     (let [decoded-priv (-> "3082011302010104205c0b98e524ad188ddef35dc6abba13c34a351a05409e5d285403718b93336a4aa081a53081a2020101302c06072a8648ce3d0101022100fffffffffffffffffffffffffffffffffffffffffffffffffffffffefffffc2f300604010004010704410479be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798483ada7726a3c4655da4fbfc0e1108a8fd17b448a68554199c47d08ffb10d4b8022100fffffffffffffffffffffffffffffffebaaedce6af48a03bbfd25e8cd0364141020101a144034200042af7a2aafe8dafd7dc7f9cfb58ce09bda7dce28653ab229b98d1d3d759660c672dd0db18c8c2d76aa470448e876fc2089ab1354c01a6e72cefc50915f4a963ee"
;;                            (hex->bytes)
;;                            (transcode-priv :biginteger))
;;           roundtrip-priv (-> decoded-priv
;;                              (transcode-priv :der)
;;                              (transcode-priv :biginteger))]
;;       (= decoded-priv roundtrip-priv)))

;; ;; Test that decoded-priv can sign a msg that can be verified
;; ;; by its derived pub.
;; (expect true
;;   (let [decoded-priv (-> "3082011302010104205c0b98e524ad188ddef35dc6abba13c34a351a05409e5d285403718b93336a4aa081a53081a2020101302c06072a8648ce3d0101022100fffffffffffffffffffffffffffffffffffffffffffffffffffffffefffffc2f300604010004010704410479be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798483ada7726a3c4655da4fbfc0e1108a8fd17b448a68554199c47d08ffb10d4b8022100fffffffffffffffffffffffffffffffebaaedce6af48a03bbfd25e8cd0364141020101a144034200042af7a2aafe8dafd7dc7f9cfb58ce09bda7dce28653ab229b98d1d3d759660c672dd0db18c8c2d76aa470448e876fc2089ab1354c01a6e72cefc50915f4a963ee"
;;                         (hex->bytes)
;;                         (transcode-priv :biginteger)
;;                         :value)]
;;     (let [msg (reverse-bytes (hex->bytes "11da3761e86431e4a54c176789e41f1651b324d240d599a7067bee23d328ec2a"))
;;           sigDER (-> (sign msg decoded-priv) (sigmap->der))]
;;       (verify msg sigDER (-> (priv->pub decoded-priv)
;;                              (transcode-pub :bin)
;;                              :value)))))

;; ;; Test that roundtrip-priv can sign a msg that can be verified
;; ;; by its derived pub.
;; (expect true
;;     (let [decoded-priv (-> "3082011302010104205c0b98e524ad188ddef35dc6abba13c34a351a05409e5d285403718b93336a4aa081a53081a2020101302c06072a8648ce3d0101022100fffffffffffffffffffffffffffffffffffffffffffffffffffffffefffffc2f300604010004010704410479be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798483ada7726a3c4655da4fbfc0e1108a8fd17b448a68554199c47d08ffb10d4b8022100fffffffffffffffffffffffffffffffebaaedce6af48a03bbfd25e8cd0364141020101a144034200042af7a2aafe8dafd7dc7f9cfb58ce09bda7dce28653ab229b98d1d3d759660c672dd0db18c8c2d76aa470448e876fc2089ab1354c01a6e72cefc50915f4a963ee"
;;                            (hex->bytes)
;;                            (transcode-priv :biginteger)
;;                            :value)
;;           roundtrip-priv (-> decoded-priv
;;                              (transcode-priv :der)
;;                              (transcode-priv :biginteger))]
;;       (let [msg (reverse-bytes (hex->bytes "11da3761e86431e4a54c176789e41f1651b324d240d599a7067bee23d328ec2a"))
;;             sigDER (-> (sign msg roundtrip-priv) (sigmap->der))]
;;         (verify msg sigDER (-> (priv->pub decoded-priv)
;;                                (transcode-pub :bin)
;;                                :value)))))

;; ;; Test that decoded-priv can verify external sig.
;; (expect true
;;   (let [decoded-priv (-> "3082011302010104205c0b98e524ad188ddef35dc6abba13c34a351a05409e5d285403718b93336a4aa081a53081a2020101302c06072a8648ce3d0101022100fffffffffffffffffffffffffffffffffffffffffffffffffffffffefffffc2f300604010004010704410479be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798483ada7726a3c4655da4fbfc0e1108a8fd17b448a68554199c47d08ffb10d4b8022100fffffffffffffffffffffffffffffffebaaedce6af48a03bbfd25e8cd0364141020101a144034200042af7a2aafe8dafd7dc7f9cfb58ce09bda7dce28653ab229b98d1d3d759660c672dd0db18c8c2d76aa470448e876fc2089ab1354c01a6e72cefc50915f4a963ee"
;;                          (hex->bytes)
;;                          (transcode-priv :biginteger)
;;                          :value)]
;;     (let [msg (reverse-bytes (hex->bytes "11da3761e86431e4a54c176789e41f1651b324d240d599a7067bee23d328ec2a"))
;;           external-sigDER (hex->bytes "304502206faa2ebc614bf4a0b31f0ce4ed9012eb193302ec2bcaccc7ae8bb40577f47549022100c73a1a1acc209f3f860bf9b9f5e13e9433db6f8b7bd527a088a0e0cd0a4c83e9")]
;;       (verify msg
;;               (der->sigmap external-sigDER)
;;               (.getEncoded (priv->pub decoded-priv))))))

;; ;; Sign with decoded-priv, verify with roundtrip-priv.
;; (expect true
;;   (let [privkeyASN1 (hex->bytes "3082011302010104205c0b98e524ad188ddef35dc6abba13c34a351a05409e5d285403718b93336a4aa081a53081a2020101302c06072a8648ce3d0101022100fffffffffffffffffffffffffffffffffffffffffffffffffffffffefffffc2f300604010004010704410479be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798483ada7726a3c4655da4fbfc0e1108a8fd17b448a68554199c47d08ffb10d4b8022100fffffffffffffffffffffffffffffffebaaedce6af48a03bbfd25e8cd0364141020101a144034200042af7a2aafe8dafd7dc7f9cfb58ce09bda7dce28653ab229b98d1d3d759660c672dd0db18c8c2d76aa470448e876fc2089ab1354c01a6e72cefc50915f4a963ee")
;;         decoded-priv (asn1->priv privkeyASN1)
;;         roundtrip-priv (asn1->priv (priv->asn1 decoded-priv))]
;;     (let [msg (reverse-bytes (hex->bytes "11da3761e86431e4a54c176789e41f1651b324d240d599a7067bee23d328ec2a"))
;;           sigDER (-> (sign msg decoded-priv) (sigmap->der))]
;;       (verify msg sigDER (.getEncoded (priv->pub roundtrip-priv))))))

;; (expect true
;;     (let [decoded-priv (-> "3082011302010104205c0b98e524ad188ddef35dc6abba13c34a351a05409e5d285403718b93336a4aa081a53081a2020101302c06072a8648ce3d0101022100fffffffffffffffffffffffffffffffffffffffffffffffffffffffefffffc2f300604010004010704410479be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798483ada7726a3c4655da4fbfc0e1108a8fd17b448a68554199c47d08ffb10d4b8022100fffffffffffffffffffffffffffffffebaaedce6af48a03bbfd25e8cd0364141020101a144034200042af7a2aafe8dafd7dc7f9cfb58ce09bda7dce28653ab229b98d1d3d759660c672dd0db18c8c2d76aa470448e876fc2089ab1354c01a6e72cefc50915f4a963ee"
;;                            (hex->bytes)
;;                            (transcode-priv :biginteger))
;;           roundtrip-priv (-> decoded-priv
;;                              (transcode-priv :der)
;;                              (transcode-priv :biginteger))]
;;       (let [msg (reverse-bytes (hex->bytes "11da3761e86431e4a54c176789e41f1651b324d240d599a7067bee23d328ec2a"))
;;             sigDER (-> (sign msg decoded-priv) (sigmap->der))]
;;         (verify msg
;;                 sigDER
;;                 (.getEncoded (priv->pub (:value roundtrip-priv)))))))


;; ;; Sign with roundtrip-priv, verify with decoded-priv.
;; (expect true
;;     (let [decoded-priv (-> "3082011302010104205c0b98e524ad188ddef35dc6abba13c34a351a05409e5d285403718b93336a4aa081a53081a2020101302c06072a8648ce3d0101022100fffffffffffffffffffffffffffffffffffffffffffffffffffffffefffffc2f300604010004010704410479be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798483ada7726a3c4655da4fbfc0e1108a8fd17b448a68554199c47d08ffb10d4b8022100fffffffffffffffffffffffffffffffebaaedce6af48a03bbfd25e8cd0364141020101a144034200042af7a2aafe8dafd7dc7f9cfb58ce09bda7dce28653ab229b98d1d3d759660c672dd0db18c8c2d76aa470448e876fc2089ab1354c01a6e72cefc50915f4a963ee"
;;                            (hex->bytes)
;;                            (transcode-priv :biginteger))
;;           roundtrip-priv (-> decoded-priv
;;                              (transcode-priv :der)
;;                              (transcode-priv :biginteger))]
;;       (let [msg (reverse-bytes (hex->bytes "11da3761e86431e4a54c176789e41f1651b324d240d599a7067bee23d328ec2a"))
;;             sigDER (-> (sign msg (:value roundtrip-priv)) (sigmap->der))]
;;         (verify msg
;;                 sigDER
;;                 (.getEncoded (priv->pub (:value decoded-priv)))))))


;; ;; ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; ;; ;; BitcoinJ test: sValue() ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; ;; ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; ;; Ensure we never generate a sig with an `s` value that's
;; ;; larger than half the order (N) of the curve.
;; ;; - i.e. (canonical? sig) should always be true.
;; (expect true
;;     (every? true?
;;             (let [priv (:value (:priv (generate-pair)))]
;;               (for [b (range 10)]
;;                 (canonical? (sign (byte-array b) priv))))))


;; ;; ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; ;; ;; Chaingun tests ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; ;; ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; ;; Test: canonical?
;; (expect false (canonical? {:s (inc (/ curve/N 2)) :r 42}))
;; (expect true (canonical? {:s (/ curve/N 2) :r 42}))
;; (expect true (canonical? {:s (dec (/ curve/N 2)) :r 42}))

;; ;; ;; Satoshi-client tests

;; ;; ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; ;; ;; bitcoin-ruby suite bitcoin_spec.rb
;; ;; ; https://github.com/lian/bitcoin-ruby/blob/master/spec/bitcoin/bitcoin_spec.rb

;; (expect "62e907b15cbf27d5425399ebf6f0fb50ebb88f18"
;;   (-> "04678afdb0fe5548271967f1a67130b7105cd6a828e03909a67962e0ea1f61deb649f6bc3f4cef38c4f35504e51ec112de5c384df7ba0b8d578a4c702b6bf11d5f"
;;       (hex->bytes) (crypto/hash160) (bytes->hex)))

;; ;; extract private key from uncompressed DER format
;; (expect "a29fe0f28b2936dbc89f889f74cd1f0662d18a873ac15d6cd417b808db1ccd0a"
;;   (-> "308201130201010420a29fe0f28b2936dbc89f889f74cd1f0662d18a873ac15d6cd417b808db1ccd0aa081a53081a2020101302c06072a8648ce3d0101022100fffffffffffffffffffffffffffffffffffffffffffffffffffffffefffffc2f300604010004010704410479be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798483ada7726a3c4655da4fbfc0e1108a8fd17b448a68554199c47d08ffb10d4b8022100fffffffffffffffffffffffffffffffebaaedce6af48a03bbfd25e8cd0364141020101a14403420004768cfc6c44b927b0e69e9dd343e96132f7cd1d360d8cb8d65c83d89d7beaceadfd19918e076606a099344156acdb026b1065a958e39f098cfd0a34dd976291d6"
;;       (hex->bytes)
;;       (transcode-priv :hex)
;;       :value))

;; ;; ;; bitcoin-ruby key_spec.rb

;; ;; should create key from only priv
;; (expect "045fcb2fb2802b024f371cc22bc392268cc579e47e7936e0d1f05064e6e1103b8a81954eb6d3d33b8b6e73e9269013e843e83919f7ce4039bb046517a0cad5a3b1"
;;   (-> "2ebd3738f59ae4fd408d717bf325b4cb979a409b0153f6d3b4b91cdfe046fb1e"
;;       (transcode-priv :biginteger) :value
;;       (priv->pub)
;;       (transcode-pub :hex) :value))

;; ;; should get addr
;; (expect "1JbYZRKyysprVjSSBobs8LX6QVjzsscQNU"
;;   (priv->address (hex->unum "2ebd3738f59ae4fd408d717bf325b4cb979a409b0153f6d3b4b91cdfe046fb1e")))

;; ;; should sign data
;; (expect true
;;   (let [sigmap (sign (.getBytes "foobar") (hex->unum "2ebd3738f59ae4fd408d717bf325b4cb979a409b0153f6d3b4b91cdfe046fb1e"))]
;;     (<= 69 (byte-count (sigmap->der sigmap)))))

;; ;; should verify signature
;; (expect true
;;   (let [priv (-> "2ebd3738f59ae4fd408d717bf325b4cb979a409b0153f6d3b4b91cdfe046fb1e"
;;                  (transcode-priv :biginteger)
;;                  :value)
;;         sigDER (sigmap->der (sign (.getBytes "foobar") priv))]
;;     (verify (.getBytes "foobar")
;;             sigDER
;;             (.getEncoded (priv->pub priv)))))

;; ;; should export private key in base58 format (wif)
;; (expect "5Kb8kLf9zgWQnogidDA76MzPL6TsZZY36hWXMssSzNydYXYB9KF"
;;   (-> "e9873d79c6d87dc0fb6a5778633389f4453213303da61f20bd67fc233aa33262"
;;       (transcode-priv :wif)
;;       :value))

;; ;; should import private key in base58 (wif) format
;; (let [priv-wif "5Kb8kLf9zgWQnogidDA76MzPL6TsZZY36hWXMssSzNydYXYB9KF"]
;;   (expect "e9873d79c6d87dc0fb6a5778633389f4453213303da61f20bd67fc233aa33262"
;;     (-> priv-wif (transcode-priv :hex) :value))
;;   (expect "1CC3X2gu58d6wXUWMffpuzN9JAfTUWu4Kj"
;;     (-> priv-wif
;;         (transcode-priv :biginteger)
;;         :value
;;         (priv->address))))

;; ;; ;; describe Bitcoin::OpenSSL_EC
;; ;; ;; resolves public from private key
;; (given [privhex pubhex] (expect pubhex
;;                           (-> privhex
;;                               (transcode-priv :biginteger) :value
;;                               (priv->pub)
;;                               (transcode-pub :hex) :value))
;;   ;; Priv hex -> Pub hex
;;   "56e28a425a7b588973b5db962a09b1aca7bdc4a7268cdd671d03c52a997255dc"
;;   "04324c6ebdcf079db6c9209a6b715b955622561262cde13a8a1df8ae0ef030eaa1552e31f8be90c385e27883a9d82780283d19507d7fa2e1e71a1d11bc3a52caf3"

;;   "b51386f8275d49d8d30287d7b1afa805790bdd1fe8b13d22d25928c67ea55d02"
;;   "0470305ae5278a22499980286d9c513861d89e7b7317c8b891c554d5c8fdd256b03daa0340be4104f8c84cfa98f0da8f16567fcdd3a00fd993adbbe91695671a56"

;;   "d8ebece51adc5fb99dd6994bcb8fa1221d01576fd76af9134ab36f8d4698b55c"
;;   "047503421850d3a6eecb7c9de33b367c4d3f96a34ff257ad0c34e234e29f3672525c6b4353ce6fdc9de3f885fdea798982e2252e610065dbdb62cd8cab1fe45822"

;;   "c95c79fb0cc1fe47b384751df0627be40bbe481ec94eeafeb6dc40e94c40de43"
;;   "04b746ca07e718c7ca26d4eeec037492777f48bb5c750e972621698f699f530535c0ffa96dad581102d0471add88e691af85955d1fd42f68506f8092fddfe0c47a"

;;   "5b61f807cc938b0fd3ec8f6006737d0002ceca09f296204138c4459de8a856f6"
;;   "0487357bf30c13d47d955666f42f87690cfd18be96cc74cda711da74bf76b08ebc6055aba30680e6288df14bda68c781cbf71eaad096c3639e9724c5e26f3acf54")
