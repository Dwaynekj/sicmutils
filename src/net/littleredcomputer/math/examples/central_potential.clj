(ns net.littleredcomputer.math.examples.central-potential
  (:refer-clojure :exclude [+ - * /])
  (:require [clojure.tools.logging :as log]
            [hiccup.core :refer :all]
            [hiccup.page :refer :all]
            [net.littleredcomputer.math.env :refer :all]
            [net.littleredcomputer.math.mechanics.lagrange :refer :all]))

;; these are nice but they deal with fixed bodies.
;; how to generalize this if the condition M_i >> m
;; no longer holds?
;;
;; The kinetic energy part is always easy.
;; We would have to come up with an expression for the
;; potential of the system that took account of all
;; the masses. Does it just add? Try to obtain mutually
;; orbiting elliptical configuration

(defn V-central
  "Potential energy of an object of mass M with state given by a local
   tuple in a 1/r^2 field generated by an object of mass m1 situated
   at (x1, y1)."
  [m M1 x1 y1]
  (let [c (up x1 y1)
        -Mm (- (* M1 m))]
    (fn [[t x v]]
      (let [r (sqrt (square (- x c)))]
        (/ -Mm r)))))

(defn V-central2
  [m M1 x1 y1 M2 x2 y2]
  (let [c1 (up x1 y1)
        -Mm1 (- (* M1 m))
        c2 (up x2 y2)
        -Mm2 (- (* M2 m))]
    (fn [[t x v]]
      (let [r1 (sqrt (square (- x c1)))
            r2 (sqrt (square (- x c2)))]
        (+ (/ -Mm1 r1) (/ -Mm2 r2))))))

(defn V-central3
  [m M1 x1 y1 M2 x2 y2 M3 x3 y3]
  (let [c1 (up x1 y1)
        -Mm1 (- (* M1 m))
        c2 (up x2 y2)
        -Mm2 (- (* M2 m))
        c3 (up x3 y3)
        -Mm3 (- (* M3 m))]
    (fn [[t x v]]
      (let [r1 (sqrt (square (- x c1)))
            r2 (sqrt (square (- x c2)))
            r3 (sqrt (square (- x c3)))]
        (+ (/ -Mm1 r1) (/ -Mm2 r2) (/ -Mm3 r3))))))

(defn T-central
  "Kinetic energy of an object of mass M in free-fall with given local
  state (NB: the configuration of fixed masses do not contribute to the
  kinetic energy)"
  [m M1 x1 y1]
  (L-free-particle m))

(defn T-central2
  [m M1 x1 y1 M2 x2 y2]
  (L-free-particle m))

(defn T-central3
  [m M1 x1 y1 M2 x2 y2 M3 x3 y3]
  (L-free-particle m))

(def L-central (- T-central V-central))
(def L-central2 (- T-central2 V-central2))
(def L-central3 (- T-central3 V-central3))

(defn central-state-derivative
  [m M1 x1 y1]
  (Lagrangian->state-derivative
   (L-central m M1 x1 y1)))

(defn central-state-derivative2
  [m M1 x1 y1 M2 x2 y2]
  (Lagrangian->state-derivative
   (L-central2 m M1 x1 y1 M2 x2 y2)))

(defn central-state-derivative3
  [m M1 x1 y1 M2 x2 y2 M3 x3 y3]
  (Lagrangian->state-derivative
   (L-central3 m M1 x1 y1 M2 x2 y2 M3 x3 y3)))

(defn evolver
  [t dt m x0 y0 xDot0 yDot0]
  (let [state-history (atom [])]
    ((evolve central-state-derivative3
             m ;; object mass
             1000 0 0 ;; first planetary mass,x,y
             1000 20 0 ;; second planetary mass, x, y
             1000 0 20 ;; third planetary mass, x, y
             )
      (up 0.0
          (up x0 y0)
          (up xDot0 yDot0))
      (fn [t [_ q _]] (swap! state-history conj (into [t] q)))
      dt
      t
      1.0e-6
      {:compile true})
    @state-history))

(defn- to-svg
  [evolution]
  [:svg {:width 480 :height 480}
   [:rect {:width 480 :height 480 :fill "#330033"}]
   [:g {:transform "translate(240,240)"}
    [:circle {:fill "green" :stroke "none" :r 5 :cx 0 :cy 0}]
    [:circle {:fill "green" :stroke "none" :r 5 :cx 20 :cy 0}]
    [:circle {:fill "green" :stroke "none" :r 5 :cx 0 :cy 20}]
    (for [[t x y] evolution]
      [:circle {:fill "orange" :stroke "none" :r 1 :cx x :cy y}])]])

;; Simó's initial data
;; x1=−x2=0.97000436−0.24308753i,x3=0; V~ = ˙x3=−2 ˙x1=−2 ˙x2=−0.93240737−0.86473146i
;; T =12T =6.32591398, I(0)=2, m1=m2=m3=1

(defn -main
  [& args]
  (let [head [:head {:title "foo"}]
        counter (atom 0)
        body [:body
              (for [dy (range -16 -1 1/10) :when (not= dy -47/15)]
                (let [svg (to-svg (evolver 400 1/3 1 50 50 dy 0))]
                  (log/info (str "dy " dy))
                  (spit (format "%03d.svg" @counter) (html svg))
                  (swap! counter inc)
                  svg))]]
    (println (html5 head body))))
