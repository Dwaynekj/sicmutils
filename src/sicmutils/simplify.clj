;
; Copyright (C) 2016 Colin Smith.
; This work is based on the Scmutils system of MIT/GNU Scheme.
;
; This is free software;  you can redistribute it and/or modify
; it under the terms of the GNU General Public License as published by
; the Free Software Foundation; either version 3 of the License, or (at
; your option) any later version.
;
; This software is distributed in the hope that it will be useful, but
; WITHOUT ANY WARRANTY; without even the implied warranty of
; MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
; General Public License for more details.
;
; You should have received a copy of the GNU General Public License
; along with this code; if not, see <http://www.gnu.org/licenses/>.
;

(ns sicmutils.simplify
  (:import (java.util.concurrent TimeoutException)
           (clojure.lang Sequential Var)
           (java.io StringWriter))
  (:require [clojure.walk :refer [postwalk]]
            [clojure.tools.logging :as log]
            [clojure.pprint :as pp]
            [sicmutils
             [numsymb :as sym]
             [polynomial :as poly]
             [rational-function :as rf]
             [value :as v]
             [generic :as g]
             [rules :as rules]]
            [pattern.rule :as rule]))

(defn analyzer
  [symbol-generator expr-> ->expr known-operations]
  (let [expr->var (ref {})
        var->expr (ref {})]
    (fn [expr]
      (letfn [(analyze [expr]
                (if (and (sequential? expr)
                         (not (= (first expr) 'quote)))
                  (let [analyzed-expr (map analyze expr)]
                    (if (and (known-operations (sym/operator analyzed-expr))
                             (or (not (= 'expt (sym/operator analyzed-expr)))
                                 (integer? (second (sym/operands analyzed-expr)))))
                      analyzed-expr
                      (if-let [existing-expr (@expr->var analyzed-expr)]
                        existing-expr
                        (new-kernels analyzed-expr))))
                  expr))
              (new-kernels [expr]
                ;; use doall to force the variable-binding side effects of base-simplify
                (let [simplified-expr (doall (map base-simplify expr))]
                  (if-let [v (sym/symbolic-operator (sym/operator simplified-expr))]
                    (let [w (apply v (sym/operands simplified-expr))]
                      (if (and (sequential? w)
                               (= (sym/operator w) (sym/operator simplified-expr)))
                        (add-symbols! w)
                        (analyze w)))
                    (add-symbols! simplified-expr))))
              (add-symbols! [expr]
                (->> expr (map add-symbol!) add-symbol!))
              (add-symbol! [expr]
                (if (and (sequential? expr)
                         (not (= (first expr) 'quote)))
                  (dosync ; in a transaction, probe and maybe update the expr->var->expr maps
                   (if-let [existing-expr (@expr->var expr)]
                     existing-expr
                     (let [var (symbol-generator)]
                       (alter expr->var assoc expr var)
                       (alter var->expr assoc var expr)
                       var)))
                  expr))
              (backsubstitute [expr]
                (cond (sequential? expr) (map backsubstitute expr)
                      (symbol? expr) (if-let [w (@var->expr expr)]
                                       (backsubstitute w)
                                       expr)
                      :else expr))
              (base-simplify [expr] (expr-> expr ->expr))]
        (-> expr analyze base-simplify backsubstitute)))))

(defn monotonic-symbol-generator
  "Returns a function which generates a sequence of symbols with the given
  prefix with the property that later symbols will sort after earlier symbols.
  This is important for the stability of the simplifier. (If we just used
  gensym, then a temporary symbol like G__1000 will sort earlier than G__999,
  and this will happen at unpredictable times.)"
  [prefix]
  (let [count (atom -1)]
    (fn [] (symbol (format "%s%016x" prefix (swap! count inc))))))

(defn ^:private unless-timeout
  "Returns a function that invokes f, but catches TimeoutException;
  if that exception is caught, then x is returned in lieu of (f x)."
  [f]
  (fn [x]
    (try (f x)
         (catch TimeoutException _
           (log/warn (str "simplifier timed out: must have been a complicated expression"))
           x))))

(defn ^:private poly-analyzer
  "An analyzer capable of simplifying sums and products, but unable to
  cancel across the fraction bar"
  []
  (analyzer (monotonic-symbol-generator "-s-")
            poly/expression-> poly/->expression poly/operators-known))

(defn ^:private rational-function-analyzer
  "An analyzer capable of simplifying expressions built out of rational
  functions."
  []
  (analyzer (monotonic-symbol-generator "-r-")
            rf/expression-> rf/->expression rf/operators-known))

(def ^:dynamic *rf-analyzer* (memoize (unless-timeout (rational-function-analyzer))))
(def ^:dynamic *poly-analyzer* (memoize (poly-analyzer)))

(defn hermetic-simplify-fixture
  [f]
  (log/info "Setting up hermetic simplification fixture")
  (binding [*rf-analyzer* (rational-function-analyzer)
            *poly-analyzer* (poly-analyzer)]
    (f)))

(def ^:private simplify-and-flatten #'*rf-analyzer*)

(defn- simplify-until-stable
  [rule-simplify canonicalize]
  (fn [expression]
    (let [new-expression (rule-simplify expression)]
      (if (= expression new-expression)
        expression
        (let [canonicalized-expression (canonicalize new-expression)]
          (cond (= canonicalized-expression expression) expression
                (g/zero? (*poly-analyzer* `(~'- ~expression ~canonicalized-expression))) canonicalized-expression
                :else (recur canonicalized-expression)))))))

(defn- simplify-and-canonicalize
  [rule-simplify canonicalize]
  (fn simplify [expression]
    (let [new-expression (rule-simplify expression)]
      (if (= expression new-expression)
        expression
        (canonicalize new-expression)))))

(def ^:private sin-sq->cos-sq-simplifier
  (simplify-and-canonicalize rules/sin-sq->cos-sq simplify-and-flatten))
(def ^:private sincos-simplifier
  (simplify-and-canonicalize rules/sincos-flush-ones simplify-and-flatten))
(def ^:private square-root-simplifier
  (simplify-and-canonicalize rules/simplify-square-roots simplify-and-flatten))

;; looks like we might have the modules inverted: rulesets will need some functions from the
;; simplification library, so this one has to go here. Not ideal the way we have split things
;; up, but at least things are beginning to simplify adequately.

(def ^:private simplify-zero
  #(-> % *poly-analyzer* g/zero?))

(def ^:private sincos-cleanup
  "This finds things like a - a cos^2 x and replaces them with a sin^2 x"
  (let [at-least-two? #(and (number? %) (>= % 2))]
    (simplify-until-stable
     (rule/rule-simplifier
      (rule/ruleset
       ;;  ... + a + ... + cos^n x + ...   if a + cos^(n-2) x = 0: a sin^2 x
       (+ :a1* :a :a2* (expt (cos :x) (:? n at-least-two?)) :a3*)
       #(simplify-zero `(~'+ (~'expt (~'cos ~(% :x)) ~(- (% 'n) 2)) ~(% :a)))
       (+ :a1* :a2* :a3* (* :a (expt (sin :x) 2)))

       (+ :a1* (expt (cos :x) (:? n at-least-two?)) :a2* :a :a3*)
       #(simplify-zero `(~'+ (~'expt (~'cos ~(% :x)) ~(- (% 'n) 2)) ~(% :a)))
       (+ :a1* :a2* :a3* (* :a (expt (sin :x) 2)))

       (+ :a1* :a :a2* (* :b1* (expt (cos :x) (:? n at-least-two?)) :b2*) :a3*)
       #(simplify-zero `(~'+ (~'* ~@(% :b1*) ~@(% :b2*) (~'expt (~'cos ~(% :x)) ~(- (% 'n) 2))) ~(% :a)))
       (+ :a1* :a2* :a3* (* :a (expt (sin :x) 2)))

       (+ :a1* (* :b1* (expt (cos :x) (:? n at-least-two?)) :b2*) :a2* :a :a3*)
       #(simplify-zero `(~'+ (~'* ~@(% :b1*) ~@(% :b2*) (~'expt (~'cos ~(% :x)) ~(- (% 'n) 2))) ~(% :a)))
       (+ :a1* :a2* :a3* (* :a (expt (sin :x) 2)))))
     simplify-and-flatten)))

;; (defn ^:private spy [x a]
;;   (println a x)
;;   x)

(def ^:private simplify-expression-1
  #(-> %
       rules/canonicalize-partials
       rules/trig->sincos
       simplify-and-flatten
       rules/complex-trig
       sincos-simplifier
       sin-sq->cos-sq-simplifier
       sincos-cleanup
       rules/sincos->trig
       square-root-simplifier
       ;;rules/divide-numbers-through
       simplify-and-flatten))

(def simplify-expression (simplify-until-stable simplify-expression-1 simplify-and-flatten))

(defmethod g/simplify [:sicmutils.expression/numerical-expression]
  [a]
  (->> a v/freeze simplify-expression))

(defmethod g/simplify :default [a] (v/freeze a))
(defmethod g/simplify [Var] [a] (-> a meta :name))
(defmethod g/simplify [Sequential] [a] (map g/simplify a))
(prefer-method g/simplify [:sicmutils.structure/structure] [Sequential])

(defn expression->string
  "Renders an expression through the simplifier and into a string,
  which is returned."
  [expr]
  (let [w (StringWriter.)]
    (-> expr g/simplify (pp/write :stream w))
    (str w)))
(def print-expression #(-> % g/simplify pp/pprint))
(def pe print-expression)
