(ns math.numerical.ode-test
  (:require [clojure.test :refer :all]
            [math.structure :refer :all]
            [math.value :as v]
            [math.numerical.ode :refer :all]))

(deftest simple-odes
  (testing "y' = y"
    (let [result ((state-advancer (constantly identity)) (up 1.) 1. 1.e-10)]
      (is ((v/within 1e-8) (Math/exp 1) (nth result 0))))
    (let [states (atom [])
          result ((evolve (constantly identity)) ;; solve: y' = y
                  (up 1.)                        ;;        y(0) = 1
                  #(swap! states conj [%1 %2])   ;; accumulate intermediate results
                  0.1                            ;; ... with a mesh size of 0.1
                  1                              ;; solve until t = 1
                  1e-10)]                        ;; accuracy desired
      (is (= (count @states) 11))
      (is ((v/within 1e-8) (Math/exp 1) (first result))))))
