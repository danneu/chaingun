(ns hyzhenhok.script-test
  (:require [expectations :refer :all]
            [hyzhenhok.script :refer :all]
            [hyzhenhok.util :refer :all]
            [clojure.string :as str]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Script parsing ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(given [input pretty-output] (expect pretty-output
                               (prettify-output
                                (parse
                                 (hex->bytes
                                  (str/replace input #"\s+" "")))))
  ;; === op-datapush1 (is 0x4c 76) ===
  "4c 01 0a" ["0a"]
  "4c 02 0a0b" ["0a0b"]

  ;; === op-datapush2 (is 0x4d 77) ===
  ;; - take 1 byte
  "4d 0001 0a" ["0a"]
  ;; - take 2 bytes
  "4d 0002 0a0b" ["0a0b"]

  ;; === op-datapush4 (is 0x4e 78) ===
  "4e 00000001 0a" ["0a"]
  "4e 00000002 0a0b" ["0a0b"]


  ;; === 1 thru 75 ===
  ;; Push that many follow bytes
  "01 0a" ["0a"]
  "02 0a0b" ["0a0b"]
  ;; 75 bytes
  "4b 01020304050607080900 01020304050607080900
      01020304050607080900 01020304050607080900
      01020304050607080900 01020304050607080900
      01020304050607080900 0102030405"
  ["010203040506070809000102030405060708090001020304050607080900010203040506070809000102030405060708090001020304050607080900010203040506070809000102030405"]

  ;; === ops ===

  "00" [:op-false]
  "0000" [:op-false :op-false]

  "525354" [:op-2 :op-3 :op-4]
  "030a0b0c 63 67 68" ["0a0b0c" :op-if :op-else :op-endif]

  ;; 171 :op-codeseparator
  "00ab00ab"
  [:op-false :op-codeseparator :op-false :op-codeseparator]

  )

(num->hex 171)


(num->hex 75)



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Script execution ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(given [pre-state script post-state]
    (expect (prettify-output2 post-state)
      ;; TODO: Make a better error-handling system during
      ;;       script parse/execute.
      (try (prettify-output2
            (execute script pre-state))
           (catch clojure.lang.ExceptionInfo _
           :threw)))

  ;; default
  ['() '() '()] ["someconstant"] ['("someconstant") '() '()]

  ;; 0 :op-false
  ['() '() '()] [:op-false] ['(0) '() '()]
  ;; 79 :op-1negate
  ['() '() '()] [:op-1negate] ['(-1) '() '()]
  ;; 81 :op-true
  ['() '() '()] [:op-true] ['(1) '() '()]

  ['() '() '()] [:op-true :op-1negate] ['(-1 1) '() '()]
  ['() '() '()] [:op-true :op-1negate :op-add] ['(0) '() '()]

  ;; 82 :op-2
  ['() '() '()] [:op-2] ['(2) '() '()]
  ;; .
  ;; .
  ;; .

  ;; === Flow control ===

  ;; 97 :op-nop
  ['() '() '()] [:op-nop] ['() '() '()]
  ;; 99 :op-if
  ['() '() '()] [:op-if] ['() '() '(:true)]
  ;; 100 :op-notif
  ['() '() '()] [:op-notif] ['() '() '(:false)]
  ;; 103 :op-else
  ['() '() '(:true)]    [:op-else] ['() '() '(:drained)]
  ['() '() '(:false)]   [:op-else] ['() '() '(:true)]
  ['() '() '(:drained)] [:op-else] ['() '() '(:drained)]
  ['() '() '()]         [:op-else] :threw
  ;; 104 :op-endif
  ['() '() '(:true)]    [:op-endif] ['() '() '()]
  ['() '() '(:false)]   [:op-endif] ['() '() '()]
  ['() '() '(:drained)] [:op-endif] ['() '() '()]
  ['() '() '()]         [:op-endif] :threw
  ;; 105 :op-verify
  ['() '() '()]  [:op-verify] ['() '() '()]
  ['(1) '() '()] [:op-verify] ['() '() '()]
  ['(0) '() '()] [:op-verify] [:invalid '() '()]
  ;; 106 :op-return
  ['(1) '() '()] [:op-return] [:invalid '() '()]
  ['(0) '() '()] [:op-return] [:invalid '() '()]
  ['() '() '()]  [:op-return] [:invalid '() '()]

  ;; === Stack op ===

  ;; 107 :op-toaltstack
  ['(42) '() '()] [:op-toaltstack] ['() '(42) '()]
  ;; 108 :op-fromaltstack
  ['() '(42) '()] [:op-fromaltstack] ['(42) '() '()]
  ;; 109 :op-2drop
  ['(:a :b :c) '() '()] [:op-2drop] ['(:c) '() '()]
  ;; 110 :op-2dup
  ['(1 2 3) '() '()] [:op-2dup] ['(1 2 1 2 3) '() '()]
  ;; 111 :op-3dup
  ['(1 2 3 4) '() '()] [:op-3dup] ['(1 2 3 1 2 3 4) '() '()]
  ;; 112 :op-2over
  ['(:c :d :a :b :c) '() '()] [:op-2over] ['(:a :b :c) '() '()]
  ;; 113 :op-2rot
  ['(:a :b :c :d :e :f :g) '() '()]
  [:op-2rot]
  ['(:e :f :a :b :c :d :g) '() '()]
  ;; 114 :op-2swap
  ['(:a :b :c :d :e) '() '()]
  [:op-2swap]
  ['(:c :d :a :b :e) '() '()]
  ;; 115 :op-ifdup
  ['(1) '() '()] [:op-ifdup] ['(1 1) '() '()]
  ['(0) '() '()] [:op-ifdup] ['(0) '() '()]
  ;; 116 :op-depth
  ['(:a :b :c) '() '()] [:op-depth] ['(3 :a :b :c) '() '()]
  ;; 117 :op-drop
  ['(:a :b :c) '() '()] [:op-drop] ['(:b :c) '() '()]
  ;; 118 :op-dup
  ['(42) '() '()] [:op-dup] ['(42 42) '() '()]
  ;; 119 :op-nip
  ['(:a :b :c) '() '()] [:op-nip] ['(:a :c) '() '()]
  ;; 120 :op-over
  ['(:a :b :c) '() '()] [:op-over] ['(:b :a :b :c) '() '()]
  ;; 121 :op-pick
  ['(1 :a :b :c) '() '()] [:op-pick] ['(:b :a :b :c) '() '()]
  ;; 122 :op-roll
  ['(1 :a :b :c) '() '()] [:op-roll] ['(:b :a :c) '() '()]
  ;; 124 :op-swap
  ['(:a :b :c) '() '()] [:op-swap] ['(:b :a :c) '() '()]
  ;; 123 :op-rot
  ['(:a :b :c) '() '()] [:op-rot] ['(:b :c :a) '() '()]
  ;; 125 :op-tuck
  ['(:a :b :c) '() '()] [:op-tuck] ['(:a :b :a :c) '() '()]

  ;; 135 :op-equal
  ['(:a :a) '() '()] [:op-equal] ['(1) '() '()]
  ['(:a :b) '() '()] [:op-equal] ['(0) '() '()]
  ;; 136 :op-equalverify
  ['(:a :a) '() '()] [:op-equalverify] ['() '() '()]
  ['(:a :b) '() '()] [:op-equalverify] [:invalid '() '()]

  ;; 169 :op-hash160
  [(list (hex->bytes "aabbcc")) '() '()]
  [:op-hash160]
  [(list "0bfbcadae145d870428db173412d2d860b9acf5e") '() '()]

  ;; TODO: Move compound tests to its own test block.
  ;; ===== Conventional txn =====

  ;:op-dup :op-hash160 "pubkeyhash" :op-equalverify :op-checksig
  [(list (hex->bytes "aabbcc")) '() '()]
  [:op-dup :op-hash160]
  [(list "0bfbcadae145d870428db173412d2d860b9acf5e" "aabbcc")
   '() '()]

  ;; Pre-state
  [(list (hex->bytes "aabbcc")) '() '()]
  ;; Script
  [:op-dup :op-hash160
   "0bfbcadae145d870428db173412d2d860b9acf5e" ]
  ;; Post-state
  [(list "0bfbcadae145d870428db173412d2d860b9acf5e"
         "0bfbcadae145d870428db173412d2d860b9acf5e"
         "aabbcc") '() '()]

  ;; Pre-state
  [(list (hex->bytes "aabbcc")) '() '()]
  ;; Script
  [:op-dup :op-hash160
   (hex->bytes "0bfbcadae145d870428db173412d2d860b9acf5e")
   :op-equalverify]
  ;; Post-state
  [(list "aabbcc") '() '()]

  )

  ['() '() '()]
