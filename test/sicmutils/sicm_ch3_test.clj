(ns sicmutils.sicm-ch3-test
  (:refer-clojure :exclude [+ - * / zero? ref partial])
  (:require [clojure.test :refer :all]
            [sicmutils.env :refer :all]
            [sicmutils.mechanics.lagrange :as L]
            [sicmutils.mechanics.hamilton :as H]
            [sicmutils.simplify :refer [hermetic-simplify-fixture]]
            [sicmutils.examples.driven-pendulum :as driven]
            [sicmutils.examples.axisymmetric-top :as top]))

(use-fixtures :once hermetic-simplify-fixture)

(deftest section-3-1
  (testing "p.189"
    (is (= '(up 0
                (up (/ (+ (* ((D x) t) m) (* -1 (p_x t))) m)
                    (/ (+ (* ((D y) t) m) (* -1 (p_y t))) m))
                (down (+ ((D p_x) t) (((∂ 0) V) (x t) (y t)))
                      (+ ((D p_y) t) (((∂ 1) V) (x t) (y t)))))
           (simplify (((Hamilton-equations
                         (H/H-rectangular
                           'm
                           (literal-function 'V (-> (X Real Real) Real))))
                        (up (literal-function 'x) (literal-function 'y))
                        (down (literal-function 'p_x) (literal-function 'p_y)))
                       't)))))
  (testing "p.198"
    (is (= '(/ (+ (* 2 (V x y) m) (expt p_x 2) (expt p_y 2))
               (* 2 m))
           (simplify ((Lagrangian->Hamiltonian
                        (L/L-rectangular
                          'm (literal-function 'V (-> (X Real Real) Real))))
                       (up 't (up 'x 'y) (down 'p_x 'p_y))))))))

(deftest section-3-2
  (testing "p.205"
    (let [F (literal-function 'F (-> (UP Real (UP Real Real) (DOWN Real Real)) Real))
          G (literal-function 'G (-> (UP Real (UP Real Real) (DOWN Real Real)) Real))
          H (literal-function 'H (-> (UP Real (UP Real Real) (DOWN Real Real)) Real))]
      (is (zero? (simplify ((+ (Poisson-bracket F (Poisson-bracket G H))
                               (Poisson-bracket G (Poisson-bracket H F))
                               (Poisson-bracket H (Poisson-bracket F G)))
                             (up 't (up 'x 'y) (down 'px 'py)))))))))

(deftest section-3-4
  (testing "p.212"
    (is (= '(/ (+ (* 2 (V r) m (expt r 2))
                  (* (expt p_r 2) (expt r 2))
                  (expt p_phi 2))
               (* 2 m (expt r 2)))
           (simplify ((Lagrangian->Hamiltonian
                        (L/L-central-polar 'm (literal-function 'V)))
                       (up 't (up 'r 'phi) (down 'p_r 'p_phi))))))
    (is (= '(up 0
                (up (/ (+ (* ((D r) t) m) (* -1 (p_r t))) m)
                    (/ (+ (* (expt (r t) 2) ((D phi) t) m)
                          (* -1 (p_phi t)))
                       (* (expt (r t) 2) m)))
                (down (/ (+ (* (expt (r t) 3) ((D p_r) t) m)
                            (* (expt (r t) 3) ((D V) (r t)) m)
                            (* -1 (expt (p_phi t) 2)))
                         (* (expt (r t) 3) m))
                      ((D p_phi) t)))
           (simplify (((Hamilton-equations
                         (Lagrangian->Hamiltonian
                           (L/L-central-polar 'm (literal-function 'V))))
                        (up (literal-function 'r)
                            (literal-function 'phi))
                        (down (literal-function 'p_r)
                              (literal-function 'p_phi)))
                       't)))))
  (testing "p.213"
    (is (= '(/ (+ (* 2 (expt (sin theta) 2) (cos theta) A C gMR)
                  (* (expt (sin theta) 2) A (expt p_psi 2))
                  (* (expt (sin theta) 2) C (expt p_theta 2))
                  (* (expt (cos theta) 2) C (expt p_psi 2))
                  (* -2 (cos theta) C p_phi p_psi)
                  (* C (expt p_phi 2)))
               (* 2 (expt (sin theta) 2) A C))
           (simplify ((Lagrangian->Hamiltonian (top/L-axisymmetric-top 'A 'C 'gMR))
                       (up 't
                           (up 'theta 'phi 'psi)
                           (down 'p_theta 'p_phi 'p_psi)))))))
  (testing "p.214"
    (let [top-state (up 't
                        (up 'theta 'phi 'psi)
                        (down 'p_theta 'p_phi 'p_psi))
          H (Lagrangian->Hamiltonian
              (top/L-axisymmetric-top 'A 'C 'gMR))
          sysder (Hamiltonian->state-derivative H)]
      (is (= '(/ (+ (* 2 (expt (sin theta) 2) (cos theta) A C gMR)
                    (* (expt (sin theta) 2) A (expt p_psi 2))
                    (* (expt (sin theta) 2) C (expt p_theta 2))
                    (* (expt (cos theta) 2) C (expt p_psi 2))
                    (* -2 (cos theta) C p_phi p_psi)
                    (* C (expt p_phi 2)))
                 (* 2 (expt (sin theta) 2) A C))
             (simplify (H top-state))))
      (is (= '(up
                1
                (up
                  (/ p_theta A)
                  (/ (+ (* -1 (cos theta) p_psi) p_phi) (* (expt (sin theta) 2) A))
                  (/
                    (+
                      (* (expt (sin theta) 2) A p_psi)
                      (* (expt (cos theta) 2) C p_psi)
                      (* -1 (cos theta) C p_phi))
                    (* (expt (sin theta) 2) A C)))
                (down
                  (/
                    (+
                      (* (expt (cos theta) 4) A gMR)
                      (* -2 (expt (cos theta) 2) A gMR)
                      (* -1 (expt (cos theta) 2) p_phi p_psi)
                      (* (cos theta) (expt p_phi 2))
                      (* (cos theta) (expt p_psi 2))
                      (* A gMR)
                      (* -1 p_phi p_psi))
                    (* (expt (sin theta) 3) A))
                  0
                  0)) (simplify (sysder top-state))))
      (is (= (str "function(A, C, gMR, p_phi, p_psi, p_theta, theta) {\n"
                  "  var _1 = Math.sin(theta);\n"
                  "  var _2 = Math.pow(Math.cos(theta), 2);\n"
                  "  var _3 = Math.pow(Math.sin(theta), 2);\n"
                  "  var _4 = Math.cos(theta);\n"
                  "  return [1, [p_theta / A, (- _4 * p_psi + p_phi) / (Math.pow(_1, 2) * A), (Math.pow(_1, 2) * A * p_psi + Math.pow(_4, 2) * C * p_psi - _4 * C * p_phi) / (Math.pow(_1, 2) * A * C)], [(Math.pow(_4, 4) * A * gMR -2 * Math.pow(_4, 2) * A * gMR - Math.pow(_4, 2) * p_phi * p_psi + _4 * Math.pow(p_phi, 2) + _4 * Math.pow(p_psi, 2) + A * gMR - p_phi * p_psi) / (Math.pow(_1, 3) * A), 0, 0]];\n}")
             (-> top-state sysder simplify ->JavaScript))))))

(deftest section-3-5
  (testing "p.221"
    (let [H ((Lagrangian->Hamiltonian
               (driven/L 'm 'l 'g 'a 'omega))
              (up 't 'theta 'p_theta))]
      (is (= '(/ (+ (* -1 (expt (sin (* omega t)) 2)
                       (expt (cos theta) 2)
                       (expt a 2)
                       (expt l 2)
                       (expt m 2)
                       (expt omega 2))
                    (* 2 (sin (* omega t))
                       (sin theta)
                       a l m omega p_theta)
                    (* 2 (cos (* omega t))
                       a g (expt l 2) (expt m 2))
                    (* -2 (cos theta)
                       g (expt l 3)
                       (expt m 2))
                    (expt p_theta 2))
                 (* 2 (expt l 2) m))
             (simplify H))))
    (let [sysder (simplify
                   ((Hamiltonian->state-derivative
                      (Lagrangian->Hamiltonian
                        (driven/L 'm 'l 'g 'a 'omega)))
                     (up 't 'theta 'p_theta)))]
      (is (= '(up 1
                  (/ (+ (* (sin (* omega t)) (sin theta) a l m omega) p_theta) (* (expt l 2) m))
                  (/ (+ (* -1N (expt (sin (* omega t)) 2) (sin theta) (cos theta) (expt a 2) l m (expt omega 2))
                        (* -1N (sin (* omega t)) (cos theta) a omega p_theta)
                        (* -1N (sin theta) g (expt l 2) m)) l))
             sysder))
      ;; odd that we have _1 here when it's not used ... must be a bug in the CSE
      ;; ah, we observe that _3 is omega*t, and we have a few examples of
      ;; the sine of that. So our algorithm is a little on the naive side o_o
      (is (= (str "function(a, g, l, m, omega, p_theta, t, theta) {\n"
                  "  var _1 = Math.sin(omega * t);\n"
                  "  var _2 = Math.pow(l, 2);\n"
                  "  var _3 = omega * t;\n"
                  "  var _4 = Math.sin(theta);\n"
                  "  var _5 = Math.cos(theta);\n"
                  "  return [1, (Math.sin(_3) * _4 * a * l * m * omega + p_theta) / (_2 * m), (- Math.pow(Math.sin(_3), 2) * _4 * _5 * Math.pow(a, 2) * l * m * Math.pow(omega, 2) - Math.sin(_3) * _5 * a * omega * p_theta - _4 * g * _2 * m) / l];\n"
                  "}")
             (->JavaScript sysder))))))
