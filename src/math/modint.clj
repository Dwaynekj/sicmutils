(ns math.modint
  (:require [math.generic :as g]
            [math.euclid :as e]))

(deftype ModInt [^BigInteger a ^BigInteger m]
  g/Value
  (zero? [x] (= a 0))
  (one? [x] (= a 1))
  (zero-like [x] (ModInt. 0 a))
  (exact? [x] true)
  (sort-key [x] 15)
  Object
  (toString [x] (str (.a x) " mod " (.m x)))
  (equals [x y] (and (= (.m x) (.m y)) (= (.a x) (.a y))))
  )

(defn make [a m]
  (ModInt. (mod a m) m))

(defn modint? [x]
  (instance? ModInt x))

(defn- modular-binop [op]
  (fn [a b]
    (if-not (= (.m a) (.m b))
      (throw (IllegalArgumentException. "unequal moduli"))
      (make (op (.a a) (.a b)) (.m a)))))

(defn- modular-inv [m]
  (let [modulus (.m m)
        [g a b invertible] (e/extended-euclid (.a m) modulus)]
    (if (< g 2) (make a modulus)
        (throw (IllegalArgumentException.
                (str m " is not invertible mod " modulus))))))

(g/defhandler :+ [modint? modint?] (modular-binop +))
(g/defhandler :+ [integer? modint?] (fn [i m] (make (+ i (.a m)) (.m m))))
(g/defhandler :- [modint? modint?] (modular-binop -))
(g/defhandler :* [modint? modint?] (modular-binop *))
(g/defhandler :negate [modint?] (fn [m] (make (- (.a m)) (.m m))))
(g/defhandler :invert [modint?] modular-inv)


(println "modint initialized")
