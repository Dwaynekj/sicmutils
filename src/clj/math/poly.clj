(ns math.poly
  (:import (clojure.lang PersistentTreeMap))
  (:refer-clojure :rename {zero? core-zero?})
  (:require [clojure.set :as set]
            [math.value :as v]
            [math.numbers]
            [math.generic :as g]
            [math.expression :as x]))

;; Hmm. I sort of think this should become a deftype. Doing so might help
;; the arithmetic become genericized in the event that were ever useful.

(declare operator-table operators-known)

(defrecord Poly [^long arity ^PersistentTreeMap oc]
  v/Value
  (nullity? [p] (empty? (:oc p)))
  (numerical? [_] false)
  (unity? [p] (and (= (count (:oc p)) 1)
                 (let [[order coef] (first (:oc p))]
                   (and (core-zero? order)
                        (g/one? coef)))))
  )

;; ultimately this should be more sensitive, and allow the use of
;; generic types. Might be nice to have a ring-of-coefficients type
;; too, but it's not obvious at this point that this would fly with
;; the architecture of this system

(def ^:private base? number?)

(defn- make-with-arity [a & oc-pairs]
  (let [ocs (into (sorted-map) (filter (fn [[_ c]] (not (g/zero? c))) oc-pairs))]
    (cond (empty? ocs) 0
          (and (= (count ocs) 1) (= (first (first ocs)) 0)) (second (first ocs))
          :else (Poly. a ocs))))

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
  (apply make-sparse (map vector (iterate inc 0) coefs)))

;; should we rely on the constructors and manipulators never to allow
;; a zero coefficient into the list, or should we change degree to
;; scan for nonzero coefficients? In the normal case, there would be
;; none, but in corner cases it would still be robust.

(defn make-identity
  "Produce the identity polynomial of the given arity."
  [arity]
  (make-with-arity arity [1 1]))

(def ^:private poly-identity
  "The univariate identity polynomial p(x) = x"
  (make-identity 1))

(defn- poly-extend
  "Interpolates a variable at position n in polynomial p."
  [_ _]
  nil)

(defn degree [p]
  (cond (g/zero? p) -1
        (base? p) 0
        :else (first (first (rseq (:oc p))))))

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
        (if-let [[order coef] (first fp)]
         (if (and (= (count p) 1) (= order 0)) coef
             (Poly. a fp))
         0))))

(defn- poly-map [f p]
  (normalize-with-arity (:arity p) (into (sorted-map) (map #(vector (first %) (f (second %))) (:oc p)))))

(defn- poly-merge [f p q]
  (loop [P (:oc p)
         Q (:oc q)
         R (sorted-map)]
    (cond
     (empty? P) (into R Q)
     (empty? Q) (into R P)
     :else (let [[op cp] (first P)
                 [oq cq] (first Q)]
             (cond
              (= op oq) (let [v (f cp cq)]
                          (recur (rest P) (rest Q)
                                 (if (not (g/zero? v))
                                   (assoc R op v)
                                   R)))
              (< op oq) (recur (rest P) Q (assoc R op (f cp)))
              :else (recur P (rest Q) (assoc R oq (f cq))))))))

(defn new-variables
  [arity]
  ; temporary hack while we vectorize the coefficient lists
  ; XXX: arity is ignored
  [(make 0 1)]
  )

(def ^:private negate (partial poly-map g/negate))

(defn- add-constant [poly c]
  (if (base? poly) (g/+ poly c)
      (normalize-with-arity (:arity poly)
                            (assoc (:oc poly) 0 (g/+ (get (:oc poly) 0 0) c)))))

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
  [ocs [o c]]
  (assoc ocs o (g/+ (get ocs o 0) c)))

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
                                                (for [[op cp] (:oc p) [oq cq] (:oc q)]
                                                  [(+ op oq) (g/* cp cq)]))))))

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

(def ^:private operator-table
  {`g/+ add
   `g/- sub
   `g/* mul
   `g/negate negate
   `g/expt expt
   `g/square square
   ;`'g/gcd gcd
   })

(def ^:private operators-known (into #{} (keys operator-table)))
