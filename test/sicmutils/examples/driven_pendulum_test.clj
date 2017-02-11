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

(ns sicmutils.examples.driven-pendulum-test
  (:refer-clojure :exclude [+ - * / zero? partial ref])
  (:require [clojure.test :refer :all]
            [sicmutils.env :refer :all]
            [sicmutils.examples.driven-pendulum :as driven]
            [sicmutils.simplify :refer [hermetic-simplify-fixture]]))

(use-fixtures :once hermetic-simplify-fixture)

(deftest equations
  (with-literal-functions
    [θ y]
    (is (= '(+ (* -1 (cos (* t ω)) (sin (θ t)) a l m (expt ω 2))
               (* (sin (θ t)) g l m)
               (* (((expt D 2) θ) t) (expt l 2) m))
           (simplify (((Lagrange-equations
                         (driven/L 'm 'l 'g 'a 'ω))
                        θ)
                       't))))
    (let [o (atom [])
          observe (fn [t q] (swap! o conj [t q]))]
      (driven/evolver {:t 3/60 :dt 1/60 :observe observe})
      (is (= 4 (count @o))))))

(deftest as-javascript
  (let [eq (simplify
            ((driven/state-derivative 'm 'l 'g 'a 'omega)
             (up 't 'theta 'thetadot)))]
    (is (= (str "function(t, theta, thetadot) {\n"
                "  var _1 = Math.sin(theta);\n"
                "  return [1, thetadot, (_1 * Math.cos(omega * t) * a * Math.pow(omega, 2) - _1 * g) / l];\n}")
           (->JavaScript eq :parameter-order '[t theta thetadot]))))
  (let [eq (simplify
            ((Hamiltonian->state-derivative
              (Lagrangian->Hamiltonian
               (driven/L 'm 'l 'g 'a 'omega)))
             (->H-state 't 'theta 'p_theta)))]
    (is (= (str "function(t, theta, p_theta) {\n"
                "  var _1 = Math.sin(omega * t);\n"
                "  var _2 = Math.pow(l, 2);\n"
                "  var _3 = omega * t;\n"
                "  var _4 = Math.sin(theta);\n"
                "  var _5 = Math.cos(theta);\n"
                "  return [1, (Math.sin(_3) * _4 * a * l * m * omega + p_theta) / (_2 * m), (- Math.pow(Math.sin(_3), 2) * _5 * _4 * Math.pow(a, 2) * l * m * Math.pow(omega, 2) - Math.sin(_3) * _5 * a * omega * p_theta - _4 * g * _2 * m) / l];\n}")
           (->JavaScript eq :parameter-order '[t theta p_theta])))))
