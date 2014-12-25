(ns math.poly
  (:import (clojure.lang PersistentTreeMap))
  (:refer-clojure :rename {zero? core-zero?})
  (:require [clojure.set :as set]
            [math.value :as v]
            [math.numbers]
            [math.generic :as g]
            [math.numbers]
            [math.expression :as x]))

(declare operator-table operators-known)

(defrecord Poly [^long arity ^PersistentTreeMap xs->c]
  v/Value
  (nullity? [p] (empty? (:xs->c p)))
  (numerical? [_] false)
  (unity? [p] (and (= (count (:xs->c p)) 1)
                 (let [[exponents coef] (first (:xs->c p))]
                   (and (every? core-zero? exponents)
                        (g/one? coef)))))
  )

;; ultimately this should be more sensitive, and allow the use of
;; generic types. Might be nice to  have a ring-of-coefficients type
;; too, but it's not obvious at this point that this would fly with
;; the architecture of this system

(def ^:private base? number?)

(defn- make-with-arity [a & xc-pairs]
  (let [xs->c (into (sorted-map) (filter (fn [[_ c]] (not (g/zero? c))) xc-pairs))]
    (cond (empty? xs->c) 0
          (and (= (count xs->c) 1) (every? core-zero? (first (first xs->c)))) (second (first xs->c))
          :else (Poly. a xs->c))))

(defn- make-sparse
  "Create a polynomial specifying the terms in sparse form: supplying
  pairs of [order, coefficient]. For example, x^2 - 1 can be
  constructed by (make-sparse [2 1] [0 -1]). The order of the pairs
  doesn't matter."
  [& oc-pairs]
  (apply make-with-arity 1 oc-pairs))

(defn make
  "Create a polynomial specifying the terms in dense form, supplying
  the coefficients of the terms starting with the constant term and
  proceeding as far as needed. For example, x^2 - 1 can be constructed
  by (make -1 0 1). The order of the coefficients corresponds to the
  order of the terms, and zeros must be filled in to get to higher
  powers."
  [& coefs]
  (apply make-sparse (zipmap (map vector (iterate inc 0)) coefs)))

;; should we rely on the constructors and manipulators never to allow
;; a zero coefficient into the list, or should we change degree to
;; scan for nonzero coefficients? In the normal case, there would be
;; none, but in corner cases it would still be robust.

(defn degree [p]
  (cond (g/zero? p) -1
        (base? p) 0
        :else (apply max (map #(reduce + 0 %) (keys (:xs->c p))))))

;; ARITY

(defn- arity [p]
  (if (base? p)
    0
    (:arity p)))

(defn- check-same-arity [p q]
  (let [ap (arity p)
        aq (arity q)]
    (cond (base? p) aq
          (base? q) ap
          (= ap aq) ap
          :else (throw (ArithmeticException. "mismatched polynomial arity")))))

(defn- normalize-with-arity [a p]
  (if (base? p) p
      (let [fp (->> p (filter #(not (g/zero? (second %)))) (into (sorted-map)))]
        (if-let [[xs coef] (first fp)]
         (if (and (= (count p) 1) (every? core-zero? xs)) coef
             (Poly. a fp))
         0))))

(defn- poly-map [f p]
  (normalize-with-arity (:arity p) (into (sorted-map) (map #(vector (first %) (f (second %))) (:xs->c p)))))

(defn- poly-merge [f p q]
  (loop [P (:xs->c p)
         Q (:xs->c q)
         R (sorted-map)]
    (cond
      (empty? P) (into R Q)
      (empty? Q) (into R P)
      :else (let [[xp cp] (first P)
                  [xq cq] (first Q)
                  order (compare xp xq)]
              (cond
                (core-zero? order) (let [v (f cp cq)]
                                     (recur (rest P) (rest Q)
                                            (if (not (g/zero? v))
                                              (assoc R xp v)
                                              R)))
                (< order 0) (recur (rest P) Q (assoc R xp (f cp)))
                :else (recur P (rest Q) (assoc R xq (f cq))))))))

(defn new-variables
  [arity]
  (for [a (range arity)]
    (make-with-arity arity [(vec (map #(if (= % a) 1 0) (range arity))) 1])))

(def ^:private negate (partial poly-map g/negate))

(defn- constant-term-of-arity
  [a]
  (vec (repeat a 0)))

(defn- add-constant [poly c]
  (if (base? poly) (g/+ poly c)
                   (let [a (:arity poly)
                         constant-term (constant-term-of-arity a)]
                     (normalize-with-arity a
                                          (assoc (:xs->c poly)
                                                 constant-term
                                                 (g/+ (get (:xs->c poly) constant-term 0) c))))))

(defn add [p q]
  (cond (and (base? p) (base? q)) (g/+ p q)
        (g/zero? p) q
        (g/zero? q) p
        (base? p) (add-constant q p)
        (base? q) (add-constant p q)
        :else (let [a (check-same-arity p q)
                    sum (poly-merge g/+ p q)]
                (normalize-with-arity a sum))))

(defn- add-denormal
  "Add-denormal adds the (order, coefficient) pair to the polynomial p,
  expecting that p is currently in sparse form (i.e., not a primitive number)
  and without normalizing the result (e.g., to see if the polynomial has
  become constant or a term has dropped out). Useful in intermediate steps
  of polynomial computations."
  [ocs [exponents coefficient]]
  (assoc ocs exponents (g/+ (get ocs exponents 0) coefficient)))

(defn sub [p q]
  (cond (and (base? p) (base? q)) (g/- p q)
        (g/zero? p) (g/negate q)
        (g/zero? q) p
        (base? p) (add-constant (negate q) p)
        (base? q) (add-constant p q)
        :else (let [a (check-same-arity p q)
                    diff (poly-merge g/- p q)]
                (normalize-with-arity a diff))))

(defn mul [p q]
  (cond (and (base? p) (base? q)) (g/* p q)
        (g/zero? p) 0
        (g/zero? q) 0
        (g/one? p) q
        (g/one? q) p
        (base? p) (poly-map #(g/* p %) q)
        (base? q) (poly-map #(g/* % q) p)
        :else (let [a (check-same-arity p q)]
                (normalize-with-arity a (reduce add-denormal (sorted-map)
                                                (for [[xp cp] (:xs->c p)
                                                      [xq cq] (:xs->c q)]
                                                  [(vec (map + xp xq)) (g/* cp cq)]))))))

(defn- square [p]
  (mul p p))

(defn expt [p n]
  (cond (base? p) (g/expt p n)
        (or
         (not (integer? n))
         (< n 0)) (throw (ArithmeticException. (str "can't raise poly to " n)))
        (g/one? p) p
        (g/zero? p) (if (core-zero? n)
                      (throw (ArithmeticException. "poly 0^0"))
                    p)
        (core-zero? n) 1
        :else (loop [x p c n a (make 1)]
                (if (core-zero? c) a
                    (if (even? c)
                      (recur (square x) (quot c 2) a)
                      (recur x (dec c) (mul x a)))))))

(defn expression->
  [expr cont]
  (let [expression-vars (set/difference (x/variables-in expr) operators-known)
        new-bindings (zipmap expression-vars (new-variables (count expression-vars)))
        environment (into operator-table new-bindings)]
    (cont (x/walk-expression environment expr) expression-vars)))

(defn ->expression
  [^Poly p vars]
  #_(prn "->expr" p vars)
  (if (base? p)
    p
    ; maybe get rid of 0/1 in reduce calls. Maybe use symb:+ instead of g/+.
    (reduce g/+ 0 (map (fn [[exponents coefficient]]
                         #_(prn "exponents" exponents "coefficient" coefficient)
                         (g/* coefficient
                              (reduce g/* 1 (map (fn [exponent var]
                                                   (g/expt var exponent))
                                                 exponents vars))))
                       (:xs->c p)))))

(def ^:private operator-table
  {`g/+ #(reduce add 0 %&)
   `g/- sub                                                 ;; TODO: make arbitrary arity
   `g/* #(reduce mul 1 %&)
   `g/negate negate
   `g/expt expt
   `g/square square
   ;`'g/gcd gcd
   })

(def ^:private operators-known (into #{} (keys operator-table)))
