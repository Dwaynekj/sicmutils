;
; Copyright © 2017 Colin Smith.
; This work is based on the Scmutils system of MIT/GNU Scheme:
; Copyright © 2002 Massachusetts Institute of Technology
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

(ns sicmutils.numerical.integrate-test
  (:require [clojure.test :refer :all]
            [sicmutils.value :as v]
            [sicmutils.numerical.integrate :refer :all]))

(def ^:private near (v/within 1e-6))

(def ^:private natural-log (partial definite-integral / 1.))

(def ^:private sine (partial definite-integral #(Math/cos %) 0.))

(defn bessel-j0 [x]
  (/ (definite-integral #(Math/cos (- (* x (Math/sin %)))) 0. Math/PI) Math/PI))

(deftest integrals
  (testing "easy"
    (is (near 0.333333 (definite-integral #(* % %) 0. 1.)))
    (is (near 0.5 (definite-integral identity 0. 1.)))
    (is (near 3 (definite-integral (constantly 1.0) 0. 3.)))
    (is (near 0 (definite-integral (constantly 0.0) 0. 1000.)))
    (is (near 1.0 (natural-log (Math/exp 1.))))
    (is (near 0 (sine Math/PI)))
    (is (near 1 (sine (/ Math/PI 2))))
    (is (near 0.7651976 (bessel-j0 1)))
    (is (near -0.2459358 (bessel-j0 10))))
  (testing "harder"
    ;; Apache commons math doesn't seem to have an integrator handy that works
    ;; well on integrals with a singularity at the edge
    (let [sort-of-near (v/within 5e-3)
          integrand (fn [g theta0]
                      (fn [theta]
                        (/ (Math/sqrt (* 2 g (- (Math/cos theta) (Math/cos theta0)))))))
          epsilon 0.000001
          L (fn [a] (* 4 (definite-integral (integrand 9.8 a)
                           0 (- a epsilon)
                           :method :legendre-gauss
                           :max-evaluations 500000) ))]
      (is (sort-of-near 2.00992 (L 0.15)))
      (is (sort-of-near 2.01844 (L 0.30)))
      (is (sort-of-near 2.03279 (L 0.45)))
      (is (sort-of-near 2.0532 (L 0.60)))
      (is (sort-of-near 2.08001 (L 0.75)))
      (is (sort-of-near 2.11368 (L 0.9)))))
  (testing "direct elliptic"
    (is (near 0.300712 (elliptic-f 0.3 0.4)))
    (is (near 0.738059 (elliptic-f 0.7 0.8))))
  (testing "general pendulum periods"
    (let [period (fn [theta_0]
                   (/ (* 8 (elliptic-f (/ theta_0 2) (/ (Math/sin (/ theta_0 2)))))
                      (* (Math/sqrt (* 2 9.8))
                         (Math/sqrt (- 1 (Math/cos theta_0))))))]
      (is (near 2.009916 (period 0.15)))
      (is (near 2.018438 (period 0.30)))
      (is (near 2.032791 (period 0.45)))
      (is (near 2.053204 (period 0.60)))
      (is (near 2.080013 (period 0.75)))
      (is (near 2.113680 (period 0.90)))
      (is (near 2.154814 (period 1.05)))
      (is (near 2.204206 (period 1.20)))
      (is (near 2.262882 (period 1.35)))
      (is (near 2.332176 (period 1.50)))
      (is (near 2.413836 (period 1.65)))
      (is (near 2.510197 (period 1.80)))
      (is (near 2.624447 (period 1.95))))))
