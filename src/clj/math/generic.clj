(ns math.generic
  (:refer-clojure :rename {+ core-+
                           - core--
                           / core-div
                           * core-*
                           zero? core-zero?})
  (:require [math.expression]
            [math.value :as v]
            [math.expression :as x])
  (:import [math.expression Expression]))

;;; classifiers
(defn internal-zero?
  [x]
  (cond (number? x) (core-zero? x)
        (vector? x) (every? internal-zero? x)
        (satisfies? v/Value x) (v/nullity? x)
        :else false))

(defn zero? [x]
  (let [z (internal-zero? x)]
    z))

(defn one?
  [x]
  (or (and (number? x) (== x 1))
      (and (satisfies? v/Value x) (v/unity? x))))

(defn exact?
  [x]
  (or (integer? x)
      (ratio? x)
      (and (satisfies? v/Value x) (v/exact? x))))

(defn numerical?
  [x]
  (and (satisfies? v/Value x) (v/numerical? x)))

(defn zero-like
  [x]
  (if (satisfies? v/Value x)
    (v/zero-like x)
    0))

(defn literal-number?
  [x]
  (and (instance? Expression x) (v/numerical? x)))

(defn abstract-number?
  [x]
  (or (symbol? x) (literal-number? x)))

(defn abstract-quantity?
  [x]
  (and (instance? Expression x)
       (x/abstract? x)))

(defn numerical-quantity?
  [x]
  (or (number? x)
      (abstract-number? x)
      (numerical? x)))

(defn scalar? [s]
  (or (numerical-quantity? s)
      (not (or (v/compound? s)
               (ifn? s)))))

;; or how about something like
;; (assoc (assoc-in dtree (mapcat (fn [x] [:step x]) p))

(def empty-dtree {:steps {} :stop nil})
(def ^:private the-operator-table (atom {}))

(defn dtree-insert [{:keys [steps stop] :as dtree} op [predicate & predicates]]
  (if predicate
    ;; if there is another predicate, the next dtree is either the one
    ;; that governs this predicate at this stage, or a new empty one.
    (let [next-dtree (or (steps predicate) empty-dtree)]
      ;; augment the binding at this level
      (assoc dtree :steps
             (assoc steps predicate (dtree-insert next-dtree op predicates))))
    ;; no more predicates? store the current stop function.
    (do
      (if stop (prn "overwriting a binding!!" stop op dtree))
      (assoc dtree :stop op))))

(defn dtree-lookup [{:keys [steps stop]} [argument & arguments]]
  (if argument
    ;; take a step: that means finding a predicate that matches at
    ;; this step and seeing if the subordinate dtree also matches. The
    ;; first step that matches this pair of conditions is chosen.
    (some identity
          (map (fn [[step dtree]]
                 (and (step argument)
                      (dtree-lookup dtree arguments))) steps))
    ;; otherwise we stop here.
    stop))

(defn defhandler [operator predicates f]
  (swap! the-operator-table
         (fn [operator-table]
           (let [dtree (get operator-table operator empty-dtree)]
             (assoc operator-table operator
                    (dtree-insert dtree f predicates))))))

(defn findhandler [operator arguments]
  (if-let [dtree (@the-operator-table operator)]
    (dtree-lookup dtree arguments)))

(defn make-operation [operator arity]
  (with-meta (fn [& args]
               (if-let [h (findhandler operator args)]
                 (apply h args)
                 (throw (IllegalArgumentException.
                         (str "no variant of " operator
                              " will work for " args "\n" (count args) "\n" (apply list (map type args)) "\n" )))))
    {:arity arity}))

(def ^:private mul (make-operation :* 2))
(def ^:private add (make-operation :+ 2))
(def ^:private sub (make-operation :- 2))
(def ^:private div (make-operation :div 2))

(def expt (make-operation :** 2))
(def negate (make-operation :negate 1))
(def invert (make-operation :invert 1))
(def sin (make-operation :sin 1))
(def cos (make-operation :cos 1))
(def tan (make-operation :tan 1))
(def square (make-operation :square 1))
(def cube (make-operation :cube 1))
(def abs (make-operation :abs 1))
(def sqrt (make-operation :sqrt 1))
(def exp (make-operation :exp 1 ))
(def log (make-operation :log 1))
(def partial-derivative (make-operation :∂ 2))
(def simplify (make-operation :simplify 1))

(defn- sort-key
  [x]
  ;; WARNING: the second term of the seq is a temporary idea
  ;; that we aren't sure we want
  (cond (symbol? x) [90 0]
        (number? x) [10 0]
        (satisfies? v/Value x) [(v/sort-key x) 0]
        :else [99 0]))

(defn canonical-order [args]
  ;; NB: we are relying on the fact that this sort is stable, although
  ;; the Clojure documentation does not explicity guarantee this
  (sort-by sort-key args))

(defn- bin+ [a b]
  (cond (and (number? a) (number? b)) (core-+ a b)
        (zero? a) b
        (zero? b) a
        :else (add a b))
  )

(defn + [& args]
  (reduce bin+ 0 (canonical-order args)))

(defn- bin- [a b]
  (cond (and (number? a) (number? b)) (core-- a b)
        (zero? b) a
        (zero? a) (negate b)
        :else (sub a b)))

(defn - [& args]
  (cond (empty? args) 0
        (= (count args) 1) (negate (first args))
        :else (bin- (first args) (apply + (next args)))))

(defn- bin* [a b]
  (cond (and (number? a) (number? b)) (core-* a b)
        (and (number? a) (zero? a)) (zero-like b)
        (and (number? b) (zero? b)) (zero-like a)
        (one? a) b
        (one? b) a
        :else (mul a b)))

;;; In bin* we test for exact (numerical) zero
;;; because it is possible to produce a wrong-type
;;; zero here, as follows:
;;;
;;;               |0|             |0|
;;;       |a b c| |0|   |0|       |0|
;;;       |d e f| |0| = |0|, not  |0|
;;;
;;; We are less worried about the zero? below,
;;; because any invertible matrix is square.

(defn * [& args]
  (reduce bin* 1 (canonical-order args)))

(defn- bin-div [a b]
  (cond (and (number? a) (number? b)) (core-div a b)
        (one? b) a
        :else (div a b)))

(defn / [& args]
  (cond (nil? args) 1
        (nil? (next args)) (invert (first args))
        :else (bin-div (first args) (apply * (next args)))))

(def divide /)

;; XXX move these to expression?


(println "generic initialized")
