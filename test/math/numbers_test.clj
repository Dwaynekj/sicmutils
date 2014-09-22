(ns math.numbers-test
  (:require [clojure.test :refer :all]
            [math.generic :refer :all]
            [math.numbers]))

(deftest arithmetic
  (testing "with-numbers"
    (is (= 4 (add 2 2)))
    (is (= 3.5 (add 1.5 2)))
    (is (= 13/40 (add 1/5 1/8)))
    (is (= 20 (mul 5 4)))
    (is (= 5 (div 20 4)))
    (is (= 5/4 (div 5 4)))
    (is (= 1/8 (div 8))))
  (testing "with-symbols"
    (is (= '(math.generic/add 4 x) (add 4 'x)))
    (is (= '(math.generic/add 5 y) (add 'y 5)))
    (is (= '(math.generic/div 5 y) (div 5 'y)))
    (is (= '(math.generic/div x y) (div 'x 'y)))
    )
  (testing "zero/one elimination"
    (is (= 'x (add 0 'x)))
    (is (= 'x (mul 1 'x)))
    (is (= '(math.generic/neg x) (sub 0 'x)))
    (is (= 'x (add 'x 0)))
    (is (= 'x (mul 'x 1)))
    (is (= 'x (sub 'x 0)))
    (is (= 'x (add 0.0 'x)))
    (is (= 'x (mul 1.0 'x)))
    (is (= 'x (add 'x 0.0)))
    (is (= 'x (mul 'x 1.0)))
    (is (= 'x (div 'x 1.0)))
    (is (= 'x (div 'x 1)))
    (is (= 0 (div 0 'x)))
    (is (thrown? IllegalArgumentException (div 'x 0)))
    )
  (testing "neg"
    (is (= -4 (sub 0 4)))
    (is (= '(math.generic/neg x) (sub 0 'x)))
    (is (= -4 (sub 4)))
    (is (= -4.2 (neg 4.2)))
    ))

