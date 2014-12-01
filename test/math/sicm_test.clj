(ns math.sicm-test
  (:refer-clojure :exclude [+ - * / zero?])
  (:require [clojure.test :refer :all]
            [math.generic :refer :all]
            [math.structure :refer :all]
            [math.numbers :refer :all]
            [math.expression :refer :all]
            [math.numerical.integrate :refer :all]
            [math.numerical.minimize :refer :all]
            [math.function :refer :all]
            [math.operator :refer :all]
            [math.value :as v]
            [math.calculus.derivative :refer :all]))

(defn velocity [local] (nth local 2))

(def ^:private near (v/within 1e-6))

(defn L-free-particle [mass]
  (fn [local]
    (let [v (velocity local)]
      (* 1/2 mass (square v)))))

(def q
  (up (literal-function 'x)
      (literal-function 'y)
      (literal-function 'z)))

(defn Γ
  [q]
  (fn [t]
    (up t (q t) ((D q) t))))

(defn Lagrangian-action
  [L q t1 t2]
  (integrate (comp L (Γ q)) t1 t2))

(defn test-path
  [t]
  (up (+ (* 4 t) 7)
      (+ (* 3 t) 5)
      (+ (* 2 t) 1)))

(defn make-η
  [ν t1 t2]
  (fn [t]
    (* (- t t1) (- t t2) (ν t))))

(defn varied-free-particle-action
  [mass q ν t1 t2]
  (fn [ε]
    (let [η (make-η ν t1 t2)]
      (Lagrangian-action (L-free-particle mass)
                         (+ q (* ε η)) t1 t2))))

(deftest sicm
  (testing "apply-struct"
    (is (= '(up (x t)
               (y t)
               (z t))
           (freeze-expression (q 't))))
    (is (= '(up ((D x) t)
                ((D y) t)
                ((D z) t))
           (freeze-expression ((D q) 't))))
    ;; need to get exponentiation of operators before we can
    ;; do this.
    ;; (is (= (up (literal-number (((expt D 2) 'x) 't))
    ;;            (literal-number (((expt D 2) 'y) 't))
    ;;            (literal-number (((expt D 2) 'z) 't)))
    ;;        ((D (D q)) 't)))
    (is (= '(up t
                (up (x t)
                    (y t)
                    (z t))
                (up ((D x) t)
                    ((D y) t)
                    ((D z) t)))
           (freeze-expression ((Γ q) 't))))
    ;; this is beginning to take shape... the simplifier is not
    ;; deployed yet; the text has (expt ((D x) t) 2) where we have
    ;; literal multiplication.
    (is (= '(* 1/2 m (+
                      (* ((D z) t)
                         ((D z) t))
                      (* ((D x) t)
                         ((D x) t))
                      (* ((D y) t)
                         ((D y) t))))
           (freeze-expression ((comp (L-free-particle 'm) (Γ q)) 't))))
    ;; at this point in the text we should be able to show-expression
    ;; in TeX form XXX.
    (is (= 435.0 (Lagrangian-action (L-free-particle 3.0) test-path 0.0 10.0)))
    (is (= (up (sin 2.0) (cos 2.0) (square 2.0)) ((up sin cos square) 2.0)))
    (let [η (make-η #(* % %) 0 1)
          ε 1/1000
          f (* η ε)
          η2 (make-η (up sin cos square) 0 1)
          Γη2 (Γ η2)]
      (is (= 0.0 (η 0.0)))
      (is (= 0.0 (η 1.0)))
      (is (= -1/16 (η 1/2)))
      (is (= -1/16000 (f 1/2)))
      (is (= (up 0 0 0) (η2 0.0)))
      (is (= (up 0 0 0) (η2 1.0)))
      (is (= (up (* -1/4 (sin 0.5)) (* -1/4 (cos 0.5)) (/ 1. -16.)) (η2 0.5)))
      (is (= (up 0 0 0) ((Γ η) 0)))
      (is (= (up 1 0 1) ((Γ η) 1)))
      ;; the following two are pre-simplification
      (is (= '(* t t t (- t 1)) (freeze-expression (η 't))))
      (is (= '(+ (* t t (+ t (- t 1))) (* t (+ t t) (- t 1))) (freeze-expression ((D η) 't))))
      (is (= '(up t (* t t t (- t 1)) (+ (* t t (+ t (- t 1))) (* t (+ t t) (- t 1)))) (freeze-expression ((Γ η) 't))))
      (is (= '(up (+ (* t (sin t)) (* (- t 1) (+ (sin t) (* t (cos t)))))
                  (+ (* t (cos t)) (* (- t 1) (+ (cos t) (* -1 t (sin t)))))
                  (+ (* t t t) (* (- t 1) (+ (* t t) (* t (+ t t))))))
             (freeze-expression ((D η2) 't))))
      )

    (is (near 436.2912143 ((varied-free-particle-action 3.0 test-path (up sin cos square) 0.0 10.0) 0.001)))
    (let [m (minimize (varied-free-particle-action 3.0 test-path (up sin cos square) 0.0 10.0) -2.0 1.0)]
      (is (near 0.0 (first m)))
      (is (near 435 (second m))))
    ))
