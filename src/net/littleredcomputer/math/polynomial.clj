;; Copyright (C) 2015 Colin Smith.
;; This work is based on the Scmutils system of MIT/GNU Scheme.
;;
;; This is free software;  you can redistribute it and/or modify
;; it under the terms of the GNU General Public License as published by
;; the Free Software Foundation; either version 3 of the License, or (at
;; your option) any later version.

;; This software is distributed in the hope that it will be useful, but
;; WITHOUT ANY WARRANTY; without even the implied warranty of
;; MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
;; General Public License for more details.

;; You should have received a copy of the GNU General Public License
;; along with this code; if not, see <http://www.gnu.org/licenses/>.

(ns net.littleredcomputer.math.polynomial
  (:import (clojure.lang PersistentTreeMap))
  (:require [clojure.set :as set]
            [net.littleredcomputer.math
             [value :as v]
             [euclid :as euclid]
             [generic :as g]
             [numsymb :as sym]
             [expression :as x]]))

(declare operator-table operators-known)
(def coefficient second)
(def exponents first)

(defrecord Polynomial [^long arity ^PersistentTreeMap xs->c]
  v/Value
  (nullity? [_] (empty? xs->c))
  (numerical? [_] false)
  (zero-like [_] (Polynomial. arity {}))
  (unity? [p] (and (= (count (:xs->c p)) 1)
                   (let [[xs c] (first (:xs->c p))]
                     (and (every? zero? xs)
                          (g/one? c))))))

(defn make
  "When called with two arguments, the first is the arity
  (number of indeterminates) of the polynomial followed by a sequence
  of exponent-coefficient pairs. Each exponent should be a vector with
  length equal to the arity, with integer exponent values. To
  make 4 x^2 y + 5 x y^2, an arity 2 polynomial (since it has two
  variables, x and y), we could write the following for xc-pairs:
  [[[2 1] 4] [[1 2] 5]]

  When called with one argument, the sequence is interpreted as a
  dense sequence of coefficients of an arity-1 (univariate)
  polynomial. The coefficients begin with the constant term and
  proceed to each higher power of the indeterminate. For example, x^2
  - 1 can be constructed by (make -1 0 1)."
  ([arity xc-pairs]
   (->> xc-pairs
        (filter (fn [[_ c]] (not (g/zero? c))))
        (into (sorted-map))
        (Polynomial. arity)))
  ([dense-coefficients]
   (make 1 (zipmap (map vector (iterate inc 0)) dense-coefficients))))

(defn degree
  [p]
  (if (v/nullity? p) -1
      (reduce max 0 (map #(reduce + 0 %) (keys (:xs->c p))))))

(defn constant?
  "If p is a constant polynomial, return that constant, else nil"
  [^Polynomial p]
  (let [xs->c (:xs->c p)]
    (cond (empty? xs->c) 0
          (and (= (count xs->c) 1)
               (every? zero? (exponents (first xs->c)))) (coefficient (first xs->c)))))

(defn check-same-arity [p q]
  (let [ap (:arity p)
        aq (:arity q)]
    (cond (= ap aq) ap
          :else (throw (ArithmeticException. "mismatched polynomial arity")))))

(defn- poly-map
  "Map the function f over the coefficients of p, returning a new Polynomial."
  [f {:keys [arity xs->c]}]
  (make arity (for [[xs c] xs->c] [xs (f c)])))

(defn- poly-merge
  "Merge the polynomials together, combining corresponding coefficients with f.
  The result is not a polynomial object, but rather a sequence
  of [exponent, coefficient] pairs, suitable for further processing or
  canonicalization. Merged monomials with zero coefficient are
  dropped."
  [f p q]
  (loop [P (:xs->c p)
         Q (:xs->c q)
         R (sorted-map)]
    (cond
      (empty? P) (into R (for [[xs c] Q
                               :let [c1 (f 0 c)]
                               :when (not (g/zero? c1))] [xs c1]))
      (empty? Q) (into R (for [[xs c] P
                               :let [c1 (f c 0)]
                               :when (not (g/zero? c1))] [xs c1]))
      :else (let [[xp cp] (first P)
                  [xq cq] (first Q)
                  order (compare xp xq)]
              (cond
                (zero? order) (let [v (f cp cq)]
                                (recur (rest P) (rest Q)
                                       (if-not (g/zero? v)
                                         (assoc R xp v)
                                         R)))
                (< order 0) (recur (rest P) Q (assoc R xp (f cp 0)))
                :else (recur P (rest Q) (assoc R xq (f 0 cq))))))))

(defn new-variables
  "Creates a sequence of identity (i.e., x) polynomials, one for each
  of arity indeterminates."
  [arity]
  (for [a (range arity)]
    (make arity [[(vec (map #(if (= % a) 1 0) (range arity))) 1]])))

(def negate (partial poly-map g/negate))

(defn make-constant
  "Return a constant polynomial of the given arity."
  [arity c]
  (make arity [[(vec (repeat arity 0)) c]]))

(defn add
  "Adds the polynomials p and q (either or both of which might just be
  constants in the base ring)."
  [p q]
  {:pre [(instance? Polynomial p)
         (instance? Polynomial q)]}
  (cond (g/zero? p) q
        (g/zero? q) p
        :else (let [a (check-same-arity p q)
                    sum (poly-merge g/+ p q)]
                (make a sum))))

(defn- add-denormal
  "Add-denormal adds the (order, coefficient) pair to the polynomial p,
  expecting that p is currently in sparse form (i.e., not a primitive number)
  and without normalizing the result (e.g., to see if the polynomial has
  become constant or a term has dropped out). Useful in intermediate steps
  of polynomial computations."
  [xs->c [xs c]]
  (assoc xs->c xs (g/+ (get xs->c xs 0) c)))

(defn sub
  "Subtract the polynomial q from the polynomial p."
  [p q]
  {:pre [(instance? Polynomial p)
         (instance? Polynomial q)]}
  (cond (g/zero? p) (negate q)
        (g/zero? q) p
        :else (let [a (check-same-arity p q)
                    diff (poly-merge g/- p q)]
                (make a diff))))

(defn mul
  "Multiply polynomials p and q, and return the product."
  [p q]
  {:pre [(instance? Polynomial p)
         (instance? Polynomial q)]}
  (let [arity (check-same-arity p q)]
    (cond (g/zero? p) (make-constant arity 0)
          (g/zero? q) (make-constant arity 0)
          (g/one? p) q
          (g/one? q) p
          :else (let [a (check-same-arity p q)]
                  (make a (reduce add-denormal
                                  (sorted-map)
                                  (for [[xp cp] (:xs->c p)
                                        [xq cq] (:xs->c q)]
                                    [(vec (map + xp xq)) (g/* cp cq)])))))))

(defn coefficients
  "Return the coefficients of p. These will themselves be polynomials,
  of arity one less than that given. The coefficients of a zero-arity
  polynomial do not exist; an exception is thrown in that case."
  [{:keys [arity xs->c]}]
  (when (< arity 1)
    (throw (IllegalArgumentException. "To have coefficients a polynomial must have arity > 0")))
  (let [arity-1 (dec arity)]
       (->> xs->c
            (group-by (comp first first))
            (map (fn [[_ cs]]
                   (make arity-1 (for [[xs c] cs]
                                   [(vec (rest xs)) c])))))))

(declare gcd)



;; Where we left off: write content1 in terms of coefficients.
;;
;;  |
;;  |
;;  V
;;

(defn content
  [p]
  (prn "content" p)
  (prn "coefs" (coefficients p))
  (reduce gcd (coefficients p)))

(defn content1
  [{:keys [arity xs->c] :as p}] ;; XXX
  (if (empty? xs->c) (Polynomial. arity {})
      (let [c (->> xs->c vals (reduce euclid/gcd 0))
            xs (->> xs->c keys (reduce #(mapv min %1 %2)))]
        ;(prn "content" p "is" xs c)
        (make arity [[xs c]]))))

(defn content2
  "The content of a polynomial p is the greatest common divisor of its
  coefficients. The polynomial supplied should draw its components from
  a Euclidean domain."
  [{:keys [arity xs->c] :as p}]
  (cond (zero? arity) (throw (IllegalArgumentException. "Zero arity polynomial cannot have content"))
        (= arity 1) (if (v/nullity? p) (make-constant 1 [0])  ;; XXX needed?
                        (->> xs->c vals (reduce (comp first euclid/gcd)) (make-constant 1)))
        :else (reduce gcd (coefficients p))))

(defn content4
  [p]
  ;(prn "content" p (:arity p))
  (let [c (content1 p)]
    ;(prn "yields" c)
    c))

(defn attach-content
  "Return the polynomial formed by multiplying the first polynomial
  argument by the monomial second argument."
  [p c]
  ;(prn "attach content" p c)
                                        ;[{:keys [arity xs->c]} [[cxs cc]]]
                                        ;(make (+ arity (count cxs)))
                                        ;{:pre [(instance? Polynomial p)
                                        ;       (instance? Polynomial c)
                                        ;       (= (count (:xs->c c)) 1)]}
                                        ;
  )

(defn- lead-term
  "Return the leading (i.e., highest degree) term of the polynomial
  p. The return value is [exponents coefficient]."
  [p]
  (-> p :xs->c rseq first))

(defn divide
  "Divide polynomial u by v, and return the pair of [quotient, remainder]
  polynomials. This assumes that the coefficients are drawn from a field,
  and so support division. If you want pseudo-division instead, you can
  set {:pseudo true} in the options. In this case fractions won't appear;
  instead the divisor is multiplied by the leading coefficient of the
  dividend before quotient terms are generated so that division will not
  result in fractions. In that event, a third term is returned containing
  the power of the leading coefficient needed to relate the pseudo-quotient
  and pseudo-remainder returned in the first two terms. Similar in spirit
  to Knuth's algorithm 4.6.1R, except we don't multiply the remainder
  through during gaps in the remainder. Since you don't know up front
  how many times the integerizing multiplication will be done, we also return
  the number m for which m * u = q * v + r."
  [u v & [{:keys [pseudo]}]]
  (let [[q r m] (let [arity (check-same-arity u v)
                      [vn-exponents vn-coefficient] (lead-term v)
                      *vn (fn [p] (poly-map #(g/* % vn-coefficient) p))]
                  (if (zero? arity)
                    ;; XXX: we're sort of breaking the pseudo-division promise
                    ;; in this case, but both of the polynomials were constant,
                    ;; so what do they expect?
                    [(make 0 [[[] (g/divide (coefficient (lead-term u)) vn-coefficient)]])
                     (make 0 [[[] 0]])
                     1]
                    (loop [quotient (make arity [])
                           remainder u
                           multiplier (v/one-like vn-coefficient)]
                      ;; find a term in the remainder into which the
                      ;; lead term of the divisor can be divided.
                      (let [remainder' (if pseudo (*vn remainder)
                                           remainder)
                            good-terms (->> remainder'
                                            :xs->c rseq
                                            (map (fn [[xs c]]
                                                   [(map - xs vn-exponents) c]))
                                            (filter (fn [[residues _]]
                                                      (and (not-empty residues)
                                                           (every? (complement neg?) residues)))))]
                        (if-let [[residues c] (first good-terms)]
                          (let [new-coefficient (g/divide c vn-coefficient)
                                new-term (make arity [[(vec residues) new-coefficient]])]
                            (recur (add (if pseudo (*vn quotient) quotient) new-term)
                                   (sub remainder' (mul new-term v))
                                   (if pseudo (* multiplier vn-coefficient) multiplier)))
                          [quotient remainder multiplier])))))]
    (if pseudo [q r m] [q r])))

(defn- primitive-part
  [p]
  (divide p (content p)))

(defn gcd
  "Knuth's algorithm 4.6.1E"
  [u v]
  ;(prn "gcd" u v (constant? u) (constant? v))
  (let [arity (check-same-arity u v)]
    (cond (zero? arity)  ;; XXX needed?
          (make 0 [[[] (first (euclid/gcd (constant? u) (constant? v)))]])
          ;; XXX
          true (let [d (gcd (content u) (content v))]
                 ;(prn "found d" d )
                 (if (v/nullity? d) (make-constant arity 0) ;; XXX is this needed?
                     (loop [u (primitive-part u)
                            v (primitive-part v)]
                       ;(prn "here")
                       (let [[_ r _] (divide u v {:pseudo true})]
                         ;(prn "GCD step" u v r)
                         (cond (v/nullity? r) (attach-content v d)
                               (zero? (degree r)) (make-constant arity d)
                               :else (recur v (primitive-part r))))))))))

(defn expt
  "Raise the polynomial p to the (integer) power n. Of course, n
  is a polynomial, so it must be a constant integer polynomial."
  [p n]
  (let [e (constant? n)]
    (when-not (and (integer? e) (>= e 0))
      (throw (ArithmeticException.
              (str "can't raise poly to " e))))
    (cond (g/one? p) p
          (g/zero? p) (if (zero? e)
                        (throw (ArithmeticException. "poly 0^0"))
                        p)
          (zero? e) (make-constant (:arity p) 1)
          :else (loop [x p c e a (make-constant (:arity p) 1)]
                  (if (zero? c) a
                      (if (even? c)
                        (recur (mul x x) (quot c 2) a)
                        (recur x (dec c) (mul x a))))))))

(defn graded-lex-order
  "An ordering on monomials. X < Y if X has higher total degree than
  Y. In case of ties, X < Y if Y < X lexicographically.  This is
  intended, when used as the comparator in an ascending sort, to
  produce an ordering like: x^2 + xy + y^2 + x + y + 1, when the
  monomials are sorted in ascending order."
  [xs ys]
  (let [deg #(reduce + %)
        xd (deg xs)
        yd (deg ys)]
    (cond (> xd yd) -1
          (< xd yd) 1
          :else (compare ys xs))))

(defn expression->
  "Convert an expression into Flat Polynomial canonical form. The
  expression should be an unwrapped expression, i.e., not an instance
  of the Expression type, nor should subexpressions contain type
  information. This kind of simplification proceeds purely
  symbolically over the known Flat Polynomial operations; other
  operations outside the arithmetic available in polynomials over
  commutative rings should be factored out by an expression analyzer
  before we get here. The result is a Polynomial object representing
  the polynomial structure of the input over the unknowns."
  [expr cont]
  (let [expression-vars (sort (set/difference (x/variables-in expr) operators-known))
        arity (count expression-vars)
        new-bindings (zipmap expression-vars (new-variables arity))
        environment (into operator-table new-bindings)
        transformer (x/walk-expression environment #(make-constant arity %))]
    (-> expr transformer (cont expression-vars))))

(defn ->expression
  "This is the output stage of Flat Polynomial canonical form simplification.
  The input is a Polynomial object, and the output is an expression
  representing the evaluation of that polynomial over the
  indeterminates extracted from the expression at the start of this
  process."
  [^Polynomial p vars]
  ;; odd: this (i.e., (symbol? p))only happens in the case of
  ;; something like (expt 'x 'y), where we can't treat it as a known
  ;; expression because 'y is not an integer. Handling it here is easy
  ;; enough, but it seems like an odd special case and perhaps should
  ;; be treated at the level above.
  (if (symbol? p) p
      (reduce
       sym/add 0
       (map (fn [[xs c]]
              (sym/mul c
                       (reduce sym/mul 1 (map (fn [exponent var]
                                                (sym/expt var exponent))
                                              xs vars))))
            (->> p :xs->c (sort-by first graded-lex-order))))))

;; The operator-table represents the operations that can be understood
;; from the point of view of a polynomial over a commutative ring. The
;; functions take polynomial inputs and return
;; polynomials.

(def ^:private operator-table
  {'+ #(reduce add %&)
   '- (fn [arg & args]
        (if (some? args) (sub arg (reduce add args)) (negate arg)))
   '* #(reduce mul %&)
   'negate negate
   'expt expt
   'square #(mul % %)
   'cube #(mul % (mul % %))
   ;;`'g/gcd gcd
   })

(def operators-known (set (keys operator-table)))
