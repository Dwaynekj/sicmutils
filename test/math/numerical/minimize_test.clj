(ns math.numerical.minimize-test
  (:require [clojure.test :refer :all]
            [math.value :as v]
            [math.numerical.minimize :refer :all]
            ))

(def tol (* 10 v/machine-epsilon))

(def ^:private near (v/within 1e-6))

(deftest minima
  (testing "easy"
    (let [B (fn [f a b] (first (minimize f a b)))]
      ;; http://www.wolframalpha.com/input/?i=x%5E2+%2B+exp%28-x%29
      (is (near 0.351734  (B #(+ (* % %) (Math/exp (- %))) 0.0 1.0)))
      ;; http://www.wolframalpha.com/input/?i=x%5E4%2B2x%5E2%2Bx%2B3
      (is (near -0.236733 (B (fn [x]
                               (let [x2 (* x x)
                                     x4 (* x2 x2)]
                                 (+ x4 (* 2 x2) x 3))) -1.0 0)))
      ;; http://www.wolframalpha.com/input/?i=exp%28x%29%2B0.01%2Fx
      (is (near 0.0953446 (B #(+ (Math/exp %) (/ 0.01 %)) 0.001 1)))
      ;; http://www.wolframalpha.com/input/?i=exp%28x%29+-+2x+%2B+%28.01%2Fx%29+-+%280.000001%2Fx%2Fx%29
      (is (near 0.703205 (B #(+ (/ 0.01 %) (- (Math/exp %) (* 2 %) (/ 0.000001 % %))) 0.001 1)))
      )))
