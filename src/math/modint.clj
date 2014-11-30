(ns math.modint
  (:require [math.value :as v]
            [math.generic :as g]
            [math.euclid :as e]))

(defrecord ModInt [^BigInteger a ^BigInteger m]
  v/Value
  (zero? [m] (= a 0))
  (one? [m] (= a 1))
  (zero-like [m] (ModInt. 0 a))
  (exact? [m] true)
  (sort-key [m] 15)
  (numerical? [m] true)
  (compound? [m] false)
  )

(defn make [a m]
  (ModInt. (mod a m) m))

(defn modint? [x]
  (instance? ModInt x))

(defn- modular-binop [op]
  (fn [^ModInt a ^ModInt b]
    (if-not (= (.m a) (.m b))
      (throw (ArithmeticException. "unequal moduli"))
      (make (op (.a a) (.a b)) (.m a)))))

(defn- modular-inv [^ModInt m]
  (let [modulus (.m m)
        [g a b invertible] (e/extended-euclid (.a m) modulus)]
    (if (< g 2) (make a modulus)
        (throw (ArithmeticException.
                (str m " is not invertible mod " modulus))))))

(g/defhandler :+ [modint? modint?] (modular-binop +))
(g/defhandler :+ [integer? modint?] (fn [i ^ModInt m] (make (+ i (.a m)) (.m m))))
(g/defhandler :- [modint? modint?] (modular-binop -))
(g/defhandler :* [modint? modint?] (modular-binop *))
(g/defhandler :negate [modint?] (fn [^ModInt m] (make (- (.a m)) (.m m))))
(g/defhandler :invert [modint?] modular-inv)


(println "modint initialized")
