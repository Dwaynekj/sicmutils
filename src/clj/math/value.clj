;; Copyright (C) 2015 Colin Smith.
;; This work is based on the Scmutils system of MIT/GNU Scheme.
;;
;; This is free software;  you can redistribute it and/or modify
;; it under the terms of the GNU General Public License as published by
;; the Free Software Foundation; either version 3 of the License, or (at
;; your option) any later version.

;; This software is distributed in the hope that it will be useful, but
;; WITpHOUT ANY WARRANTY; without even the implied warranty of
;; MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
;; General Public License for more details.

;; You should have received a copy of the GNU General Public License
;; along with this code; if not, see <http://www.gnu.org/licenses/>.

(ns math.value
  (:refer-clojure :rename {zero? core-zero?})
  (:require [clojure.tools.logging :as log]))

(defprotocol Value
  (numerical? [this])
  (nullity? [this])
  (unity? [this])
  (zero-like [this])
  (exact? [this])
  (compound? [this])
  (sort-key [this])
  ;; Freezing an expression means removing wrappers and other metadata
  ;; from subexpressions, so that the result is basically a pure
  ;; S-expression with the same structure as the input. Doing this will
  ;; rob an expression of useful information fur further computation; so
  ;; this is intended to be done just before simplification and printing, to
  ;; simplify those processes.
  (freeze [this])
  (arity [this]))

(declare primitive-arity)

(extend-type Object
  Value
  (numerical? [_] false)
  (nullity? [_] false)
  (unity? [_] false)
  (compound? [_] false)
  (exact? [o] (or (integer? o) (ratio? o)))
  (zero-like [z] 0)
  (sort-key [_] 99)
  (freeze [o] (cond (sequential? o) (map freeze o) (keyword? o) o :else o)) ;; WTF?
  (arity [o] (primitive-arity o)))

(defn- primitive-arity
  [f]
  ;; this whole function is deeply bogus. We will have to spend some time
  ;; figuring out how to deal with arity in a more precise and defensive
  ;; way. TODO: there's some reflection going on in here we should get rid of
  (or (:arity f)
      (:arity (meta f))
      (cond (symbol? f) 0
            (ifn? f) (let [^"[java.lang.reflect.Method" ms (.getDeclaredMethods (class f))
                           arities (into #{} (map #(alength (.getParameterTypes %)) ms))]
                       (if (> (count arities) 1)
                         (let [smallest-nonzero-arity (reduce min (disj arities 0))]
                           (log/warn "guessing that arity of" f "is" smallest-nonzero-arity)
                           smallest-nonzero-arity)
                         (first arities)))

            :else 0)))

(def machine-epsilon
  (loop [e 1.0]
    (if (not= 1.0 (+ 1.0 (/ e 2.0)))
      (recur (/ e 2.0))
      e)))

(defn within
  "Returns a function that tests whether two values are within ε of each other."
  [^double ε]
  (fn [^double x ^double y] (< (Math/abs (- x y)) ε)))

(println "value initialized")
