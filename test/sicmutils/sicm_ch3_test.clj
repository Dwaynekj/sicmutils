(ns sicmutils.sicm-ch3-test
  (:refer-clojure :exclude [+ - * / zero? ref partial])
  (:require [clojure.test :refer :all]
            [sicmutils.env :refer :all]
            [sicmutils.mechanics.lagrange :refer :all]
            [sicmutils.mechanics.hamilton :refer :all]
            [sicmutils.simplify :refer [hermetic-simplify-fixture]]))

(use-fixtures :once hermetic-simplify-fixture)

(deftest section-3.1
  (testing "p.189"
    (is (= '(up 0
                (up (/ (+ (* ((D x) t) m) (* -1 (p_x t))) m)
                    (/ (+ (* ((D y) t) m) (* -1 (p_y t))) m))
                (down (+ ((D p_x) t) (((∂ 0) V) (x t) (y t)))
                      (+ ((D p_y) t) (((∂ 1) V) (x t) (y t)))))
           (simplify (((Hamilton-equations
                              (H-rectangular
                                'm
                                (literal-function 'V (-> (X Real Real) Real))))
                             (up (literal-function 'x) (literal-function 'y))
                             (down (literal-function 'p_x) (literal-function 'p_y)))
                            't)))))
  (testing "p.198"
    (is (= '(/ (+ (* 2 (V x y) m) (expt p_x 2) (expt p_y 2))
               (* 2 m))
           (simplify ((Lagrangian->Hamiltonian
                        (L-rectangular
                          'm (literal-function 'V (-> (X Real Real) Real))))
                       (up 't (up 'x 'y) (down 'p_x 'p_y))))))))

(deftest section-3.2
  (testing "p.205"
    (let [F (literal-function 'F (-> (UP Real (UP Real Real) (DOWN Real Real)) Real))
          G (literal-function 'G (-> (UP Real (UP Real Real) (DOWN Real Real)) Real))
          H (literal-function 'H (-> (UP Real (UP Real Real) (DOWN Real Real)) Real))]
      (is (zero? (simplify ((+ (Poisson-bracket F (Poisson-bracket G H))
                               (Poisson-bracket G (Poisson-bracket H F))
                               (Poisson-bracket H (Poisson-bracket F G)))
                             (up 't (up 'x 'y) (down 'px 'py)))))))))

(deftest section-3.4
  (testing "p.212"
    (is (= '(/ (+ (* 2 (V r) m (expt r 2))
                  (* (expt p_r 2) (expt r 2))
                  (expt p_phi 2))
               (* 2 m (expt r 2)))
           (simplify ((Lagrangian->Hamiltonian
                        (L-central-polar 'm (literal-function 'V)))
                       (up 't (up 'r 'phi) (down 'p_r 'p_phi))))))
    (is (= '(up 0
                (up (/ (+ (* ((D r) t) m) (* -1 (p_r t))) m)
                    (/ (+ (* (expt (r t) 2) ((D phi) t) m) (* -1 (p_phi t))) (* (expt (r t) 2) m)))
                (down (/ (+ (* (expt (r t) 3) ((D p_r) t) m) (* (expt (r t) 3) ((D V) (r t)) m) (* -1 (expt (p_phi t) 2)))
                         (* (expt (r t) 3) m))
                      ((D p_phi) t)))
           (simplify (((Hamilton-equations
                         (Lagrangian->Hamiltonian
                           (L-central-polar 'm (literal-function 'V))))
                        (up (literal-function 'r)
                            (literal-function 'phi))
                        (down (literal-function 'p_r)
                              (literal-function 'p_phi)))
                       't)))))
  (testing "p.213"
    (let []
      (is (= '(/ (+ (* 2 (expt (sin theta) 2) (cos theta) A C gMR)
                    (* (expt (sin theta) 2) A (expt p_psi 2))
                    (* (expt (sin theta) 2) C (expt p_theta 2))
                    (* (expt (cos theta) 2) C (expt p_psi 2))
                    (* -2 (cos theta) C p_phi p_psi)
                    (* C (expt p_phi 2)))
                 (* 2 (expt (sin theta) 2) A C))
             (simplify ((Lagrangian->Hamiltonian (L-axisymmetric-top 'A 'C 'gMR))
                (up 't
                    (up 'theta 'phi 'psi)
                    (down 'p_theta 'p_phi 'p_psi)))))))))

(deftest section-3.5
  (testing "p.221"
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
           (simplify ((Lagrangian->Hamiltonian
                        (L-periodically-driven-pendulum 'm 'l 'g 'a 'omega))
                       (up 't 'theta 'p_theta)))))))
