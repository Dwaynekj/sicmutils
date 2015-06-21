(ns net.littleredcomputer.math.numerical.ode-test
  (:refer-clojure :exclude [+ - * / zero?])
  (:require [clojure.test :refer :all]
            [net.littleredcomputer.math
             [structure :refer :all]
             [generic :refer :all]
             [numsymb]
             [value :as v]]
            [net.littleredcomputer.math.numerical.ode :refer :all]))

(def ^:private near? (v/within 1e-8))

(deftest simple-odes
  (testing "y' = y"
    ;; we should get exp(x), with suitable initial condition
    (let [result ((state-advancer (constantly identity)) (up 1.) 1. 1.e-10)]
      (is ((v/within 1e-8) (Math/exp 1) (nth result 0))))
    (let [f (constantly identity)]
      (doseq [compile? [true false true true]]
       (let [states (atom [])
             result ((evolve f)         ;; solve: y' = y
                      (up 1.)                               ;;        y(0) = 1
                      #(swap! states conj [%1 %2])          ;; accumulate results
                      0.1                                   ;; ... with step size 0.1
                      1                                     ;; solve until t = 1
                      1e-10                                 ;; accuracy desired
                      {:compile compile?})]
         (is (= (count @states) 11))
         (is (near? (Math/exp 1) (first result)))))))
  (testing "y'' = -y"
    (let [f (fn [] (fn [[y u]] (up u (- y))))]
      (doseq [compile? [true false true]]
       (let [states (atom [])
             ;; let u = y', then we have the first-order system {y' = u, u' = -y}
             ;; with initial conditions y(0) = 0, y'(0) = 1; we expect y = sin(x).
             result ((evolve f)
                      (up 0. 1.)                            ;; y(0) = 0, y'(0) = 1
                      #(swap! states conj [%1 %2])          ;; accumulate results
                      0.1                                   ;; ... with step size 0.1
                      (* 2 (Math/PI))                       ;; over [0, 2π]
                      1.e-10                                ;; accuracy desired
                      {:compile compile?})]
         (is (= 64 (count @states)))                        ;; 0.0 .. 6.2 by .1, plus 2π
         (is (near? 0 (first result)))
         (is (near? 1 (second result)))
         (let [[t [s c]] (nth @states 15)]                  ;; state #15 is t = 1.5
           (is (near? t 1.5))
           (is (near? s (Math/sin 1.5)))
           (is (near? c (Math/cos 1.5))))
         (let [[t [s c]] (nth @states 30)]
           (is (near? t 3.0))
           (is (near? s (Math/sin 3)))
           (is (near? c (Math/cos 3))))))))
  (testing "with parameter"
    (let [f (fn [k] (fn [[y]] (up (* k y))))]
      (doseq [compile? [true false true]]
        (let [result ((evolve f 0.2)
                       (up 1)
                       nil
                       0
                       3
                       1e-10
                       {:compile compile?})]
          (is (near? (Math/exp 0.6) (first result))))))))
