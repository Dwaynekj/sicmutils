(ns math.struct
  (:require [math.generic :as g]))

(defn up [& xs] (with-meta (apply vector xs) {:orientation :up}))
(defn down [& xs] (with-meta (apply vector xs) {:orientation :down}))

(defn- orientation [s]
  (or (:orientation (meta s)) :up))

(defn with-orientation-of [s t]
  (with-meta t {:orientation (orientation s)}))

(extend-protocol g/Value
  clojure.lang.PersistentVector
  (zero? [x] (every? g/zero? x))
  (one? [x] false)
  (zero-like [x] (with-orientation-of x (vec (repeat (count x) 0))))
  (exact? [x] (every? g/exact? x)))

(def ^:private structure? vector?)

(defn- row? [s]
  (and (structure? s) (= (orientation s) :down)))

(defn- column? [s]
  (and (structure? s) (= (orientation s) :up)))

(defn- elementwise [op s t]
  (if (= (count s) (count t))
    (with-orientation-of s (vec (map op s t)))
    (throw (IllegalArgumentException.
            (str op " provided arguments of differing length")))))

(defn- scalar-multiply [a s]
  (with-orientation-of s (vec (map #(g/* a %) s))))

(defn- compatible-for-contraction? [s t]
  (and (= (count s) (count t))
       (not= (orientation s) (orientation t))))

(defn inner-product [s t]
  (apply g/+ (map g/* s t)))

(defn outer-product [s t]
  (with-orientation-of t (vec (map #(g/* s %) t))))

(defn- mul [s t]
  (if (compatible-for-contraction? s t)
    (inner-product s t)
    (outer-product s t)))

(g/defhandler :+   [row? row?]          (partial elementwise g/+))
(g/defhandler :+   [column? column?]    (partial elementwise g/+)) 
(g/defhandler :-   [row? row?]          (partial elementwise g/-))
(g/defhandler :-   [column? column?]    (partial elementwise g/-))
(g/defhandler :*   [number? structure?] scalar-multiply)
(g/defhandler :*   [structure? number?] #(scalar-multiply %2 %1))
(g/defhandler :/   [structure? number?] #(scalar-multiply (/ %2) %1))
(g/defhandler :*   [structure? structure?] mul)

(g/defhandler :square [structure?]
  (fn [s] (inner-product s s)))
(g/defhandler :cube [structure?]  ; XXX redo with expt?
  (fn [s] (g/* s s s))) 
(g/defhandler :negate [structure?]
  (fn [s] (with-orientation-of s (vec (map g/negate s)))))

(println "struct initialized")


