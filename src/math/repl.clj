(ns math.repl
  (:refer-clojure :exclude [+ - * / zero?])
  (:require [clojure.main :as m]
            [math.generic :refer :all]
            [math.structure :refer :all]
            [math.expression :refer :all]
            [math.numbers]
            [math.numsymb]
            [math.function :refer :all]
            [math.operator]
            [math.numerical.integrate]
            [math.numerical.minimize :refer :all]
            [math.calculus.derivative]
            [math.mechanics.lagrange :refer :all])
  (:gen-class :main true))

(defn -main
  [& args]
  (println "Won't you sign in, stranger?")
  (m/with-bindings
    (in-ns 'math.repl)
    (if args
      ;; read and eval the contents of the supplied files
      (doseq [a args]
        (prn "arg" a)
        )
      (m/repl)
      )
    (println "Home at last.")))
