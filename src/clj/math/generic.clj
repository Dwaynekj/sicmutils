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

(ns math.generic
  (:refer-clojure :rename {+ core-+
                           - core--
                           / core-div
                           * core-*
                           zero? core-zero?})
  (:require [math.value :as v]
            [math.expression :as x])
  (:import [math.expression Expression]))

;;; classifiers
(defn zero?
  [x]
  (cond (number? x) (core-zero? x)
        (vector? x) (every? zero? x)
        :else (v/nullity? x)))

(defn one?
  [x]
  (or (and (number? x) (== x 1))
      (v/unity? x)))

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
      (v/numerical? x)))

;; XXX do we need this?
(defn scalar? [s]
  (or (numerical-quantity? s)
      (not (or (ifn? s)
               (v/compound? s)
               ))))

(defmacro define-operations
  [& ops]
  `(do ~@(map (fn [o] `(defmulti ~o v/argument-kind)) ops)))

(define-operations
  add sub mul div invert negate square cube expt
  exp log abs sqrt
  sin cos tan
  partial-derivative
  cross-product
  simplify
  )

(defn- bin+ [a b]
  (cond (and (number? a) (number? b)) (core-+ a b)
        (zero? a) b
        (zero? b) a
        :else (add a b)))

(defn + [& args]
  (reduce bin+ 0 args))

(defn- bin- [a b]
  (cond (and (number? a) (number? b)) (core-- a b)
        (zero? b) a
        (zero? a) (negate b)
        :else (sub a b)))

(defn - [arg & args]
  (cond (nil? arg) 0
        (nil? args) (negate arg)
        :else (bin- arg (reduce bin+ args))))

(defn- bin* [a b]
  (cond (and (number? a) (number? b)) (core-* a b)
        (and (zero? a)) (v/zero-like b)
        (and (zero? b)) (v/zero-like a)
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
  (reduce bin* 1 args))

(defn- bin-div [a b]
  (cond (and (number? a) (number? b)) (core-div a b)
        (one? b) a
        :else (div a b)))

(defn / [arg & args]
  (cond (nil? arg) 1
        (nil? args) (invert arg)
        :else (bin-div arg (reduce bin* args))))

(def divide /)
