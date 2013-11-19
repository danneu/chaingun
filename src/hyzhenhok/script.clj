(ns hyzhenhok.script
  (:require
   [hyzhenhok.util :refer :all]
   [hyzhenhok.crypto :as crypto]
   [clojure.core.typed :refer :all])
  (:import
   [clojure.lang
    IPersistentList
    IPersistentMap
    IPersistentVector
    Keyword]))

(set! *warn-on-reflection* false)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def-alias ScriptItem
  (U Keyword ByteArray))

(def-alias Script
  (Seqable ScriptItem))

(def-alias ScriptStack
  (IPersistentList ScriptItem))

(def-alias ScriptState
  '[ScriptStack ScriptStack ScriptStack])

#_(defn op->ubyte [op]
  (merge (map-invert ubyte->op)
         ;; Synonyms
         {:op-0 0}))

(ann stack-true? [ScriptStack -> Boolean])
(defn stack-true? [stack]
  (not= 0 (peek stack)))

(ann ubyte->opname (IPersistentMap AnyInt Keyword))
(def ubyte->opname
  {0 :op-false
   79 :op-1negate
   81 :op-true
   82 :op-2
   83 :op-3
   84 :op-4
   85 :op-5
   86 :op-6
   87 :op-7
   88 :op-8
   89 :op-9
   90 :op-10
   91 :op-11
   92 :op-12
   93 :op-13
   94 :op-14
   95 :op-15
   96 :op-16
   ;; Flow control
   97 :op-nop
   99 :op-if
   100 :op-notif
   103 :op-else
   104 :op-endif
   105 :op-verify
   106 :op-return
   ;; Stack
   107 :op-toaltstack
   108 :op-fromaltstack
   115 :op-ifdup
   116 :op-depth
   117 :op-drop
   109 :op-2drop
   118 :op-dup
   110 :op-2dup
   111 :op-3dup
   119 :op-nip
   120 :op-over
   112 :op-2over
   121 :op-pick
   122 :op-roll
   123 :op-rot
   113 :op-2rot
   124 :op-swap;
   114 :op-2swap
   125 :op-tuck
   ;; Slice
   126 :op-cat     ; Disabled
   127 :op-substr  ; Disabled
   128 :op-left    ; Disabled
   129 :op-right   ; Disabled
   130 :op-size;
   ;; Bitwise logic
   131 :op-invert  ; Disabled
   132 :op-and     ; Disabled
   133 :op-or      ; Disabled
   134 :op-xor     ; Disabled
   135 :op-equal;
   136 :op-equalverify;
   ;; Arithmetic
   139 :op-1add;
   140 :op-1sub;
   141 :op-2mul
   142 :op-2div
   143 :op-negate;
   144 :op-abs;
   145 :op-not;
   146 :op-0notequal;
   147 :op-add;
   148 :op-sub;
   149 :op-mul        ; Disabled
   150 :op-div        ; Disabled
   151 :op-mod        ; Disabled
   152 :op-lshift     ; Disabled
   153 :op-rshift     ; Disabled
   154 :op-booland;
   155 :op-boolor;
   156 :op-numequal;
   157 :op-numequalverify;
   158 :op-numnotequal;
   159 :op-lt;
   160 :op-gt;
   161 :op-lte;
   162 :op-gte;
   163 :op-min;
   164 :op-max;
   165 :op-within;
   ;; Crypto
   166 :op-rmd160;
   167 :op-sha1
   168 :op-sha256;
   169 :op-hash160;
   170 :op-hash256
   171 :op-codeseparator         ; TODO
   172 :op-checksig              ; TODO
   173 :op-checksigverify        ; TODO
   174 :op-checkmultisig         ; TODO
   175 :op-checkmultisigverify   ; TODO
   ;; Pseudo words
   253 :op-pubkeyhash     ; Invalid
   254 :op-pubkey         ; Invalid
   255 :op-invalidopcode  ; Invalid
   ;; Reserved words
   80 :op-reserved
   98 :op-ver
   101 :op-verif
   102 :op-vernotif
   137 :op-reserved1
   138 :op-reserved2
   ;; :op-not1 to :op-nop10
   })

(ann split-head-next
  [(Seqable (Not nil)) -> '[Any (Option (Seqable Any))]])

(defn split-head-next
  "Quick way to destructure a coll's first & next. "
  [coll]
  [(first coll) (next coll)])

(ann split-head-rest
  [(Seqable Any) -> '[Any (Option (Seqable Any))]])

(defn split-head-rest
  "Quick way to destructure a coll's first & rest. "
  [coll]
  [(first coll) (rest coll)])


(ann parse [ByteArray ->
            (IPersistentVector (U Keyword ByteArray))])
(defn parse [bytes]
  (loop> [ubytes :- (Seqable Integer), (bytes->ubytes bytes)
          output :- (IPersistentVector (U Keyword HexString)), []]
    ;; 0a 0b 0c 0d 0e
    ;; this-ubyte = 0a
    (if-let [this-ubyte (first ubytes)]
      ;; rest-ubytes = 0b 0c 0d 0e
      (let [rest-ubytes (next ubytes)]
        ;; If this-ubyte is an op-code function
        (if-let [opname (ubyte->opname this-ubyte)]
          (recur rest-ubytes (conj output opname))
          ;; If this-ubyte is one of op-1 thru op-75
          (if (<= 1 this-ubyte 75)
            (let [[data rest-ubytes] (split-at this-ubyte
                                               rest-ubytes)]
              (recur rest-ubytes (conj output data)))
            ;; If this-ubyte is one of op-push1/2/4.
            (case this-ubyte
              ;; op-pushdata1. next-int: 0b, rest-ubytes: 0c0d0e
              76 (let [[read-length rest-ubytes] (split-head-next
                                                  rest-ubytes)
                       [data rest-ubytes] (split-at read-length
                                                    rest-ubytes)]
                   (recur rest-ubytes (conj output data)))
              ;; :uint16-be (2 bytes)
              77 (let [[read-length-ubytes
                        rest-ubytes] (split-at 2 rest-ubytes)
                        read-length (ubytes->unum read-length-ubytes)
                        [data rest-ubytes] (split-at read-length
                                                     rest-ubytes)]
                   (recur rest-ubytes (conj output data)))
              ;; :uint32-be (4 bytes)
              78 (let [[read-length-ubytes
                        rest-ubytes] (split-at 4 rest-ubytes)
                        read-length (ubytes->unum read-length-ubytes)
                        [data rest-ubytes] (split-at read-length
                                                     rest-ubytes)]
                   (recur rest-ubytes (conj output data)))))))
      ;; Return output if there's nothing left to take
      output)))

(defn prettify-output [output]
  (map #(if (keyword? %) % (ubytes->hex %)) output))

;; I should turn this into canonicalize fn so i can use hex
;; in and hex out in tests to represent bytes.
(defn prettify-output2
  "Gotta turn bytearrays to hex to compare them with expect."
  [state]
  (if-not (coll? state)
    state
    (mapv (fn [stack]
           (if-not (coll? stack)
             stack
             (map #(cond
                    (byte-array? %) (bytes->hex %)
                    :else %)
                  stack)))
          state)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(ann ctrl-item? [ScriptItem -> (Nilable Keyword)])
(defn ctrl-item?
  "Returns true if this item can manipulate the ctrl stack.
   - This is how we distingush functions that should always be
     executed regardless of the state of ctrl because they are
     the only ones that can update it.
   - Any items that aren't ctrl-items are not executed if
     (execute-item? ctrl) returns false."
  [item]
  (some #{:op-if :op-notif :op-else :op-endif} [item]))

(ann execute-item? [ScriptStack -> Boolean])
(defn execute-item?
  "Returns true if non-ctrl-items should be executed.
   - If false, then only items that satisfy
     (ctrl-item? item) should be executed."
  [ctrl]
  (or
   ;; :true means that we're in some if/else clause and our
   ;; clause is being executed.
   (= :true (peek ctrl))

   ;; nil means that we're not within an if at all, so
   ;; all items should be executed.
   (nil? (peek ctrl))))

(ann execute-item [ScriptItem ScriptState -> ScriptState])
(defmulti execute-item (fn [item state] item))

;; default
(defmethod ^{:doc "It's data rather than an opcode, so push the
                   bytes onto stack."}
  execute-item :default [bytes [main alt ctrl]]
  [(conj main bytes) alt ctrl])

;; 0
(defmethod execute-item :op-false [_ [main alt ctrl]]
  [(conj main 0) alt ctrl])

;; 79
(defmethod execute-item :op-1negate [_ [main alt ctrl]]
  [(conj main -1) alt ctrl])

;; 81
(defmethod execute-item :op-true [_ [main alt ctrl]]
  [(conj main 1) alt ctrl])

;; 81
(defmethod execute-item :op-2 [_ [main alt ctrl]]
  [(conj main 2) alt ctrl])

;; Flow control ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; 97
(defmethod execute-item :op-nop [_ state]
  state)

;; 99
(defmethod execute-item :op-if [_ [main alt ctrl]]
  (let [phase (if (stack-true? main) :true :false)]
    [(rest main) alt (conj ctrl phase)]))

;; 100
(defmethod execute-item :op-notif [_ [main alt ctrl]]
  (let [phrase (if-not (stack-true? main) :true :false)]
    [(rest main) alt (conj ctrl phrase)]))

;; 103
(defmethod execute-item :op-else [_ [main alt ctrl]]
  (let [phase (case (peek ctrl)
                :true :drained
                :false :true
                :drained :drained
                (throw (ex-info "Executing :op-else with nothing in on ctrl stack" {:ctrl ctrl})))]
    [main alt (conj (rest ctrl) phase)]))

;; 104
(defmethod execute-item :op-endif [_ [main alt ctrl]]
  (if (peek ctrl)
    [main alt (pop ctrl)]
    ;; TODO: Test this case.
    (throw (ex-info "Executing :op-endif with nothing on ctrl stack" {:ctrl ctrl}))))

;; 105
;; TODO: For now I'll just return :invalid
(defmethod execute-item :op-verify [_ [main alt ctrl]]
  (if (stack-true? main)
    [(rest main) alt ctrl]
    [:invalid alt ctrl]))

;; 106
(defmethod execute-item :op-return [_ [main alt ctrl]]
  [:invalid alt ctrl])

;; Bitwise logic ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; 135
(defmethod execute-item :op-equal [_ [[a b & main] alt ctrl]]
  (let [val (cond
             ;; Short-circuit into true if they're =.
             (= a b) 1
             ;; If they're both [B, then we can't compare with =.
             (every? byte-array? [a b]) (if (= (seq a) (seq b))
                                          1
                                          0)
             :else 0)]
    [(conj main val) alt ctrl]))

;; 136
(defmethod execute-item :op-equalverify
  [_ [main alt ctrl :as state]]
  (execute-item :op-verify (execute-item :op-equal state)))

;; Stack ops ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; 107
(defmethod execute-item :op-toaltstack [_ [main alt ctrl]]
  [(rest main) (conj alt (peek main)) ctrl])

;; 108
(defmethod execute-item :op-fromaltstack [_ [main alt ctrl]]
  [(conj main (peek alt)) (rest alt) ctrl])

;; 109
(defmethod ^{:doc "Removes top two stack values."}
  execute-item
  :op-2drop
  [_ [main alt ctrl :as state]]
  [(drop 2 main) alt ctrl])

;; 110
(defmethod execute-item :op-2dup
  [_ [[a b & _ :as main] alt ctrl]]
  [(concat (list a b) main) alt ctrl])

;; 111
(defmethod execute-item :op-3dup
  [_ [[a b c & _ :as main] alt ctrl]]
  [(concat (list a b c) main) alt ctrl])

;; 112
(defmethod ^{:doc "Copies 3rd and 4th items to top of stack."}
  execute-item
  :op-2over
  [_ [[_ _ c d & main] alt ctrl]]
  [(concat (list c d) main) alt ctrl])

;; 113
(defmethod ^{:doc "5th and 6th values are moved to top."}
  execute-item
  :op-2rot
  [_ [[a b c d e f & main] alt ctrl]]
  [(concat (list e f a b c d) main) alt ctrl])

;; 114
(defmethod ^{:doc "Swaps the first pair with the second pair."}
  execute-item
  :op-2swap
  [_ [[a b c d & main] alt ctrl]]
  [(concat (list c d a b) main) alt ctrl])

;; 115
(defmethod ^{:doc "If top stack value is true, duplicate it."}
  execute-item
  :op-ifdup
  [_ [main alt ctrl :as state]]
  (if (stack-true? main)
    (execute-item :op-dup state)
    state))

;; 116
(defmethod ^{:doc "Pushes stack-size onto stack."}
  execute-item
  :op-depth
  [_ [main alt ctrl]]
  [(conj main (count main)) alt ctrl])

;; 117
(defmethod ^{:doc "Removes top stack value."}
  execute-item
  :op-drop
  [_ [main alt ctrl :as state]]
  [(drop 1 main) alt ctrl])

;; 118
(defmethod execute-item :op-dup [_ [main alt ctrl]]
  [(conj main (peek main)) alt ctrl])

;; 119
(defmethod ^{:doc "Removes second-to-top stack value."}
  execute-item
  :op-nip
  [_ [[a & main] alt ctrl]]
  [(conj (rest main) a) alt ctrl])

;; 120
(defmethod ^{:doc "Copies second-to-top stack value to the top."}
  execute-item
  :op-over
  [_ [[_ b & _ :as main] alt ctrl]]
  [(conj main b) alt ctrl])

;; 121
(defmethod
  ^{:doc "Value at idx `n` is copied to the top."}
  execute-item
  :op-pick
  [_ [[n & rest] alt ctrl]]
  [(conj rest (nth rest n)) alt ctrl])

;; 122
(defmethod
  ^{:doc "Value at idx `n` is moved to the top."}
  execute-item
  :op-roll
  [_ [[n & main] alt ctrl]]
  (let [main' (concat (take n main)
                      (drop (inc n) main))]
    [(conj main' (nth main n)) alt ctrl]))

;; 123
(defmethod ^{:doc "Top three values are rotated to the left."}
  execute-item
  :op-rot
  [_ [[a b c & main] alt ctrl]]
  [(concat (list b c a) main) alt ctrl])

;; 124
(defmethod ^{:doc "Top two values are swapped."}
  execute-item
  :op-swap
  [_ [[a b & main] alt ctrl]]
  [(concat (list b a) main) alt ctrl])

;; 125
(defmethod ^{:doc "Top stack value copied behind second-to-top."}
  execute-item
  :op-tuck
  [_ [[a b & main] alt ctrl]]
  [(concat (list a b a) main) alt ctrl])

;; Crypto ops ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; 169
(defmethod execute-item :op-hash160
  [_ [[a & main] alt ctrl]]
  [(conj main (crypto/hash160 a)) alt ctrl])

;; 172 :op-checksig
;; - For normal txins, if creator of current txin can
;;   successfully create a :txin/script that uses the right
;;   pubkey for the prev :txout/script they're trying to spend,
;;   that txin is considered valid.
#_(defmethod execute-item :op-checksig
  [_ [[pub-hash sig & rest] alt ctrl world]]
  (let [script-part ; either full thing or :op-copdesep to end.
        hash (hash-stuff txouts txins script-part)]
    (if (valid-sig? sig pubkey hash)
      1
      0)))

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
(defmethod execute-item :op-x [_ [main alt ctrl]]
  [main alt ctrl])
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defmethod execute-item :op-add [_ [[a b & rest] alt ctrl]]
  [(conj rest (+ a b)) alt ctrl])

(ann execute (Fn [Script -> ScriptState]
                 [Script ScriptState -> ScriptState]))
(defn execute
  ;; - `state` starts as [(list) (list) (list)]
  ;; - if `item` is ctrl-item? (i.e. it can modify ctrl-stack),
  ;;     - then always execute it.
  ;;     - elseif ctrl-stack is :true or nil
  ;;         - execute `item`
  ;;         - else skip item.
  ([script] (execute script [(list) (list) (list)]))
  ;; Can specify an init-state for testing.
  ;; Or maybe I can even use it to pass in eval'd
  ;; txin/script before txout/script is eval'd in this loop.
  ([script init-state]
      (loop>
          [items :- Script, script
           [main alt ctrl :as state] :- ScriptState, init-state]
        (let [[this-item next-items] (split-head-next items)]
          (if this-item
            (if (ctrl-item? this-item)
              (recur next-items (execute-item this-item state))
              (if (execute-item? ctrl)
                (recur next-items (execute-item this-item state))
                ;; Move on to next item.
                (recur next-items state)))
            ;; If no more items, return state.
            state)))))
