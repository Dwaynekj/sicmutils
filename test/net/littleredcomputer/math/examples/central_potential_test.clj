;
; Copyright (C) 2015 Colin Smith.
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

(ns net.littleredcomputer.math.examples.central-potential-test
  (:refer-clojure :exclude [+ - * /])
  (:require [net.littleredcomputer.math.env :refer :all]
            [net.littleredcomputer.math.mechanics.lagrange :refer :all]
            [net.littleredcomputer.math.examples.central-potential :as central]
            [clojure.test :refer :all]))

(deftest equations
  (with-literal-functions
    [m M x y X Y]
    (let [state (up 't (up 'x 'y 'X 'Y) (up 'dx 'dy 'dX 'dY))]
      (is (= '(+ (* 1/2 (expt dX 2) m2)
                 (* 1/2 (expt dY 2) m2)
                 (* 1/2 (expt dx 2) m1)
                 (* 1/2 (expt dy 2) m1))
             (simplify ((central/T 'm1 'm2) state))))
      (is (= '(* -1 (/ (* m1 m2) (sqrt (+ (expt X 2) (* -2 X x) (expt Y 2) (* -2 Y y) (expt x 2) (expt y 2)))))
             (simplify ((central/V 'm1 'm2) state))))
      (is (= '(+ (* 1/2 (expt dX 2) m2)
                 (* 1/2 (expt dY 2) m2)
                 (* 1/2 (expt dx 2) m1)
                 (* 1/2 (expt dy 2) m1)
                 (/ (* m1 m2)
                    (sqrt (+ (expt X 2) (* -2 X x) (expt Y 2) (* -2 Y y) (expt x 2) (expt y 2)))))
             (simplify ((central/L 'm1 'm2) state))))
      (let [F ((∂ 1) (central/L 'm1 'm2))
            P ((∂ 2) (central/L 'm1 'm2))
            N (- F
                 (+ ((∂ 0) P)
                    (* ((∂ 1) P) velocity)))
            A ((∂ 2) P)]
        (is (= '(down (+ (* 2 (/ 1 (* 2 (sqrt (+ (expt X 2) (* -2 X x) (expt Y 2) (* -2 Y y) (expt x 2) (expt y 2)))))
                            (/ (* m1 m2) (+ (expt X 2) (* -2 X x) (expt Y 2) (* -2 Y y) (expt x 2) (expt y 2))) X)
                         (* -2 (/ 1 (* 2 (sqrt (+ (expt X 2) (* -2 X x) (expt Y 2) (* -2 Y y) (expt x 2) (expt y 2)))))
                            (/ (* m1 m2) (+ (expt X 2) (* -2 X x) (expt Y 2) (* -2 Y y) (expt x 2) (expt y 2))) x))
                      (+ (* 2 (/ 1 (* 2 (sqrt (+ (expt X 2) (* -2 X x) (expt Y 2) (* -2 Y y) (expt x 2) (expt y 2)))))
                            (/ (* m1 m2) (+ (expt X 2) (* -2 X x) (expt Y 2) (* -2 Y y) (expt x 2) (expt y 2))) Y)
                         (* -2 (/ 1 (* 2 (sqrt (+ (expt X 2) (* -2 X x) (expt Y 2) (* -2 Y y) (expt x 2) (expt y 2)))))
                            (/ (* m1 m2) (+ (expt X 2) (* -2 X x) (expt Y 2) (* -2 Y y) (expt x 2) (expt y 2))) y))
                      (+ (* -2 (/ 1 (* 2 (sqrt (+ (expt X 2) (* -2 X x) (expt Y 2) (* -2 Y y) (expt x 2) (expt y 2)))))
                            (/ (* m1 m2) (+ (expt X 2) (* -2 X x) (expt Y 2) (* -2 Y y) (expt x 2) (expt y 2))) X)
                         (* 2 (/ 1 (* 2 (sqrt (+ (expt X 2) (* -2 X x) (expt Y 2) (* -2 Y y) (expt x 2) (expt y 2)))))
                            (/ (* m1 m2) (+ (expt X 2) (* -2 X x) (expt Y 2) (* -2 Y y) (expt x 2) (expt y 2))) x))
                      (+ (* -2 (/ 1 (* 2 (sqrt (+ (expt X 2) (* -2 X x) (expt Y 2) (* -2 Y y) (expt x 2) (expt y 2)))))
                            (/ (* m1 m2) (+ (expt X 2) (* -2 X x) (expt Y 2) (* -2 Y y) (expt x 2) (expt y 2))) Y)
                         (* 2 (/ 1 (* 2 (sqrt (+ (expt X 2) (* -2 X x) (expt Y 2) (* -2 Y y) (expt x 2) (expt y 2)))))
                            (/ (* m1 m2) (+ (expt X 2) (* -2 X x) (expt Y 2) (* -2 Y y) (expt x 2) (expt y 2))) y)))
               (simplify (F state))))
        (is (= '(down (* dx m1)
                      (* dy m1)
                      (* dX m2)
                      (* dY m2))
               (simplify (P state))))
        (is (= '(down (+ (* 2 (/ 1 (* 2 (sqrt (+ (expt X 2) (* -2 X x) (expt Y 2) (* -2 Y y) (expt x 2) (expt y 2)))))
                            (/ (* m1 m2) (+ (expt X 2) (* -2 X x) (expt Y 2) (* -2 Y y) (expt x 2) (expt y 2))) X)
                         (* -2 (/ 1 (* 2 (sqrt (+ (expt X 2) (* -2 X x) (expt Y 2) (* -2 Y y) (expt x 2) (expt y 2)))))
                            (/ (* m1 m2) (+ (expt X 2) (* -2 X x) (expt Y 2) (* -2 Y y) (expt x 2) (expt y 2))) x))
                      (+ (* 2 (/ 1 (* 2 (sqrt (+ (expt X 2) (* -2 X x) (expt Y 2) (* -2 Y y) (expt x 2) (expt y 2)))))
                            (/ (* m1 m2) (+ (expt X 2) (* -2 X x) (expt Y 2) (* -2 Y y) (expt x 2) (expt y 2))) Y)
                         (* -2 (/ 1 (* 2 (sqrt (+ (expt X 2) (* -2 X x) (expt Y 2) (* -2 Y y) (expt x 2) (expt y 2)))))
                            (/ (* m1 m2) (+ (expt X 2) (* -2 X x) (expt Y 2) (* -2 Y y) (expt x 2) (expt y 2))) y))
                      (+ (* -2 (/ 1 (* 2 (sqrt (+ (expt X 2) (* -2 X x) (expt Y 2) (* -2 Y y) (expt x 2) (expt y 2)))))
                            (/ (* m1 m2) (+ (expt X 2) (* -2 X x) (expt Y 2) (* -2 Y y) (expt x 2) (expt y 2))) X)
                         (* 2 (/ 1 (* 2 (sqrt (+ (expt X 2) (* -2 X x) (expt Y 2) (* -2 Y y) (expt x 2) (expt y 2)))))
                            (/ (* m1 m2) (+ (expt X 2) (* -2 X x) (expt Y 2) (* -2 Y y) (expt x 2) (expt y 2))) x))
                      (+ (* -2 (/ 1 (* 2 (sqrt (+ (expt X 2) (* -2 X x) (expt Y 2) (* -2 Y y) (expt x 2) (expt y 2)))))
                            (/ (* m1 m2) (+ (expt X 2) (* -2 X x) (expt Y 2) (* -2 Y y) (expt x 2) (expt y 2))) Y)
                         (* 2 (/ 1 (* 2 (sqrt (+ (expt X 2) (* -2 X x) (expt Y 2) (* -2 Y y) (expt x 2) (expt y 2)))))
                            (/ (* m1 m2) (+ (expt X 2) (* -2 X x) (expt Y 2) (* -2 Y y) (expt x 2) (expt y 2))) y)))
               (simplify (N state))))
        (is (= '(down 0 0 0 0) (simplify ((- F N) state))))
        (is (= 4 (count (A state))))
        (is (= [4 4 4 4] (map count (A state))))
        (is (= '(down (down m1 0 0 0)
                      (down 0 m1 0 0)
                      (down 0 0 m2 0)
                      (down 0 0 0 m2))
               (simplify (A state))))
        (is (= '(up (up (/ (* m1 (expt m2 2)) (* (expt m1 2) (expt m2 2))) 0 0 0)
                    (up 0 (/ (* m1 (expt m2 2)) (* (expt m1 2) (expt m2 2))) 0 0)
                    (up 0 0 (/ (* (expt m1 2) m2) (* (expt m1 2) (expt m2 2))) 0)
                    (up 0 0 0 (/ (* (expt m1 2) m2) (* (expt m1 2) (expt m2 2)))))
               (simplify (/ (A state))))))
      (is (= '(down (down (up (up (* (((expt D 2) x) t) m) (* 1/2 (((expt D 2) y) t) m)) (up (* 1/2 (((expt D 2) y) t) m) 0))
                          (up (up 0 (* 1/2 (((expt D 2) x) t) m)) (up (* 1/2 (((expt D 2) x) t) m) (* (((expt D 2) y) t) m))))
                    (down (up (up 0 0) (up 0 0))
                          (up (up 0 0) (up 0 0))))
             (simplify (((Lagrange-equations (central/L 'm 'M))
                         (up (up x y) (up (constantly 0) (constantly 0)))) 't))))
      (is (= '(up 1
                  (up dx dy dX dY)
                  (up (+ (* 2 (/ 1 (* 2 (sqrt (+ (expt X 2) (* -2 X x) (expt Y 2) (* -2 Y y) (expt x 2) (expt y 2)))))
                            (/ (* M m) (+ (expt X 2) (* -2 X x) (expt Y 2) (* -2 Y y) (expt x 2) (expt y 2)))
                            (/ (* (expt M 2) m) (* (expt M 2) (expt m 2))) X)
                         (* -2 (/ 1 (* 2 (sqrt (+ (expt X 2) (* -2 X x) (expt Y 2) (* -2 Y y) (expt x 2) (expt y 2)))))
                            (/ (* M m) (+ (expt X 2) (* -2 X x) (expt Y 2) (* -2 Y y) (expt x 2) (expt y 2)))
                            (/ (* (expt M 2) m) (* (expt M 2) (expt m 2))) x))
                      (+ (* 2 (/ 1 (* 2 (sqrt (+ (expt X 2) (* -2 X x) (expt Y 2) (* -2 Y y) (expt x 2) (expt y 2)))))
                            (/ (* M m) (+ (expt X 2) (* -2 X x) (expt Y 2) (* -2 Y y) (expt x 2) (expt y 2)))
                            (/ (* (expt M 2) m) (* (expt M 2) (expt m 2))) Y)
                         (* -2 (/ 1 (* 2 (sqrt (+ (expt X 2) (* -2 X x) (expt Y 2) (* -2 Y y) (expt x 2) (expt y 2)))))
                            (/ (* M m) (+ (expt X 2) (* -2 X x) (expt Y 2) (* -2 Y y) (expt x 2) (expt y 2)))
                            (/ (* (expt M 2) m) (* (expt M 2) (expt m 2))) y))
                      (+ (* -2 (/ 1 (* 2 (sqrt (+ (expt X 2) (* -2 X x) (expt Y 2) (* -2 Y y) (expt x 2) (expt y 2)))))
                            (/ (* M m) (+ (expt X 2) (* -2 X x) (expt Y 2) (* -2 Y y) (expt x 2) (expt y 2)))
                            (/ (* M (expt m 2)) (* (expt M 2) (expt m 2))) X)
                         (* 2 (/ 1 (* 2 (sqrt (+ (expt X 2) (* -2 X x) (expt Y 2) (* -2 Y y) (expt x 2) (expt y 2)))))
                            (/ (* M m) (+ (expt X 2) (* -2 X x) (expt Y 2) (* -2 Y y) (expt x 2) (expt y 2)))
                            (/ (* M (expt m 2)) (* (expt M 2) (expt m 2))) x))
                      (+ (* -2 (/ 1 (* 2 (sqrt (+ (expt X 2) (* -2 X x) (expt Y 2) (* -2 Y y) (expt x 2) (expt y 2)))))
                            (/ (* M m) (+ (expt X 2) (* -2 X x) (expt Y 2) (* -2 Y y) (expt x 2) (expt y 2)))
                            (/ (* M (expt m 2)) (* (expt M 2) (expt m 2))) Y)
                         (* 2 (/ 1 (* 2 (sqrt (+ (expt X 2) (* -2 X x) (expt Y 2) (* -2 Y y) (expt x 2) (expt y 2)))))
                            (/ (* M m) (+ (expt X 2) (* -2 X x) (expt Y 2) (* -2 Y y) (expt x 2) (expt y 2)))
                            (/ (* M (expt m 2)) (* (expt M 2) (expt m 2))) y))))
             (simplify ((central/state-derivative 'm 'M) state))))
      (is (central/evolver 1 1/60 100 100 20 20 -2 0)))))
