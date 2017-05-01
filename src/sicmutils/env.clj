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

(ns sicmutils.env
  "The purpose of these definitions is to let the import of sicmutils.env
   bring all the functions in the book into scope without qualification,
   so you can just start working with examples."
  (:refer-clojure :exclude [+ - * / zero?]
                  :rename {ref core-ref partial core-partial})
  (:require [potemkin]
            [sicmutils
             [structure]
             [complex]
             [infix]
             [generic :as g]
             [simplify :as simp]
             [function :as f]
             [matrix :as matrix]
             [series :as series]]
            [sicmutils.numerical
             [minimize]
             [ode]
             [integrate]]
            [sicmutils.mechanics
             [lagrange]
             [hamilton]
             [rotation]]
            [sicmutils.calculus.derivative :as d]
            [sicmutils.calculus.manifold]
            [sicmutils.calculus.coordinate]
            [sicmutils.value :as v]))

(def zero? v/nullity?)

(defmacro literal-function
  ([f] `(f/literal-function ~f))
  ([f sicm-signature] `(f/literal-function ~f '~sicm-signature))
  ([f domain range] `(f/literal-function ~f ~domain ~range)))

(defmacro with-literal-functions
  [& args]
  `(f/with-literal-functions ~@args))

(def cot (g/divide g/cos g/sin))
(def csc (g/invert g/sin))
(def sec (g/invert g/cos))

(def print-expression simp/print-expression)

(defn ref
  "A shim so that ref can act like nth in SICM contexts, as clojure
  core ref elsewhere."
  [a & as]
  (let [m? (matrix/matrix? a)]
    (if (and as
             (or (sequential? a) m?)
             (every? integer? as))
      (if m?
        (matrix/get-in a as)
        (get-in a as))
     (apply core-ref a as))))

(defn partial
  "A shim. Dispatches to partial differentiation when all the arguments
  are integers; falls back to the core meaning (partial function application)
  otherwise."
  [& selectors]
  (if (every? integer? selectors)
    (apply d/∂ selectors)
    (apply core-partial selectors)))

(def m:transpose matrix/transpose)
(def s->m matrix/s->m)
(def qp-submatrix #(matrix/without % 0 0))
(def m:dimension matrix/dimension)
(def matrix-by-rows matrix/by-rows)
(def column-matrix matrix/column)

(def pi Math/PI)
(def principal-value v/principal-value)

(def series series/starting-with)
(def series:sum series/sum)

(potemkin/import-vars
 [sicmutils.complex
  angle
  complex
  conjugate
  imag-part
  real-part]
 [sicmutils.function
  compose]
 [sicmutils.generic
  * + - /
  abs
  acos
  asin
  atan
  cos
  cross-product
  cube
  determinant
  exp
  expt
  invert
  log
  magnitude
  negate
  simplify
  sin
  sqrt
  square
  tan
  transpose]
 [sicmutils.structure
  compatible-shape
  component
  down
  mapr
  orientation
  structure->vector
  structure?
  up
  up?
  vector->down
  vector->up]
 [sicmutils.infix
  ->infix
  ->TeX
  ->JavaScript]
 [sicmutils.calculus.derivative D ∂]
 [sicmutils.calculus.manifold
  chart
  point
  literal-manifold-function
  R2-rect
  R2-polar
  R3-rect
  R3-cyl
  S2-spherical
  S2-stereographic
  S2-Riemann]
 [sicmutils.calculus.coordinate
  let-coordinates
  using-coordinates]
 [sicmutils.calculus.vector-field
  evolution
  literal-vector-field
  vector-field->components]
 [sicmutils.mechanics.lagrange
  ->L-state
  ->L-state
  ->local
  Euler-Lagrange-operator
  F->C
  Gamma
  Gamma-bar
  Lagrange-equations
  Lagrange-equations-first-order
  Lagrange-interpolation-function
  Lagrangian->energy
  Lagrangian->state-derivative
  Lagrangian-action
  coordinate
  coordinate-tuple
  find-path
  linear-interpolants
  osculating-path
  p->r
  s->r
  velocity
  Γ]
 [sicmutils.mechanics.hamilton
  ->H-state
  F->CT
  Hamilton-equations
  Hamiltonian->state-derivative
  Lagrangian->Hamiltonian
  Legendre-transform
  Lie-derivative
  Lie-transform
  Poisson-bracket
  compositional-canonical?
  iterated-map
  momentum
  momentum-tuple
  polar-canonical
  standard-map
  symplectic-transform?
  symplectic-unit
  time-independent-canonical?]
 [sicmutils.mechanics.rotation Rx Ry Rz]
 [sicmutils.numerical.ode
  evolve
  state-advancer]
 [sicmutils.numerical.integrate definite-integral]
 [sicmutils.numerical.minimize minimize])
