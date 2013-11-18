(ns hyzhenhok.curve
  (:require
   [hyzhenhok.util :refer :all]
   [clojure.core.typed :refer :all])
  (:import
   [org.bouncycastle.math.ec
    ECCurve
    ECCurve$Fp
    ECFieldElement
    ECFieldElement$Fp
    ECPoint
    ECPoint$Fp]
   [org.bouncycastle.crypto.params
    AsymmetricKeyParameter
    ECDomainParameters
    ECKeyGenerationParameters
    ECPrivateKeyParameters
    ECPublicKeyParameters]
   [org.bouncycastle.asn1.x9
    X9ECParameters]
   [org.bouncycastle.asn1.sec
    SECNamedCurves]
   [org.bouncycastle.jce.spec
    ECParameterSpec
    ECPrivateKeySpec
    ECPublicKeySpec]
   ))

(non-nil-returns
 [SECNamedCurves getByName]
 [X9ECParameters getCurve getG getH getN]
 [ECPoint multiply getX getY]
 [ECDomainParameters getG getH getN getCurve])

;; Set up secp256k1 curve ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; - Mostly so that I don't have to remember which instance
;;   generates which curve elements every step of the way.

(ann x9ec-params X9ECParameters)
(def ^X9ECParameters x9ec-params
  (SECNamedCurves/getByName "secp256k1"))

(ann ec-params ECDomainParameters)
(def ^ECDomainParameters ec-params
  (ECDomainParameters.
   (^ECCurve .getCurve ^X9ECParameters x9ec-params)
   (^ECPoint .getG ^X9ECParameters x9ec-params)
   (^BigInteger .getN ^X9ECParameters x9ec-params)
   (^BigInteger .getH ^X9ECParameters x9ec-params)))

(ann G ECPoint)
(def G (^BigInteger .getG ^ECDomainParameters ec-params))

(ann N BigInteger)
(def N (^BigInteger .getN ^ECDomainParameters ec-params))

(ann H BigInteger)
(def H (^BigInteger .getH ^ECDomainParameters ec-params))

(ann ec-curve ECCurve)
(def ^ECCurve  ec-curve
  (^ECCurve .getCurve ^ECDomainParameters ec-params))

(ann Q BigInteger)
(def ^BigInteger Q (.getQ ec-curve))

(ann B ECFieldElement$Fp)
(def ^ECFieldElement$Fp A (.getA ec-curve))

(ann B ECFieldElement$Fp)
(def ^ECFieldElement$Fp B (.getB ec-curve))

(ann ec-param-spec ECParameterSpec)
(def ec-param-spec (ECParameterSpec. ec-curve G N H))
