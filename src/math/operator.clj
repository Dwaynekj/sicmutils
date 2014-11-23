(ns math.operator
  (:require [math.value :as v]))

(defrecord Operator [f name]
  v/Value
  (zero? [x] false)
  (one? [x] false)
  (zero-like [x] false)
  (exact? [x] false)
  (sort-key [x] 45)
  clojure.lang.IFn
  (invoke [operator function]
    (let [operated-function ((.f operator) function)]
      (fn [x] (operated-function x))))
  )

(defn make-operator
  [f name]
  (Operator. f name))
