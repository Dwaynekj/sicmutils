;; Copyright (C) 2015 Colin Smith.
;; This work is based on the Scmutils system of MIT/GNU Scheme.
;;
;; This is free software;  you can redistribute it and/or modify
;; it under the terms of the GNU General Public License as published by
;; the Free Software Foundation; either version 3 of the License, or (at
;; your option) any later version.

;; This software is distributed in the hope that it will be useful, but
;; WITHOUT ANY WARRANTY; without even the implied warranty of
;; MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
;; General Public License for more details.

;; You should have received a copy of the GNU General Public License
;; along with this code; if not, see <http://www.gnu.org/licenses/>.

(ns math.numerical.ode
  (:require [clojure.walk :refer [postwalk-replace]]
            [clojure.tools.logging :as log]
            [math.structure :as struct]
            [math.generic :as g]
            [math.numbers]
            [math.numsymb]
            [math.simplify])
  (:import (org.apache.commons.math3.ode.nonstiff GraggBulirschStoerIntegrator)
           (org.apache.commons.math3.ode FirstOrderDifferentialEquations)
           (org.apache.commons.math3.ode.sampling StepHandler StepInterpolator)))

(def ^:private compiled-function-whitelist {'up `struct/up
                                            'down `struct/down
                                            'cos #(Math/cos %)
                                            'sin #(Math/sin %)
                                            'tan #(Math/tan %)
                                            '+ +
                                            '- -
                                            '* *
                                            '/ /
                                            'expt #(Math/pow %1 %2)
                                            'sqrt #(Math/sqrt %)})

(defn- construct-function-exp
  "Given a state model (a structure which is in the domain and range
  of the function) and its body, produce a function of the flattened
  form of the argument structure as a sequence.
  FIXME: give an example here, since nobody could figure out what's
  going on just by reading this"
  [state-model body]
  `(fn ~(-> state-model flatten vec vector)
     ~(postwalk-replace compiled-function-whitelist body)))

(defn- compile-state-function
  [initial-state f]
  (let [generic-initial-state (struct/mapr (fn [_] (gensym)) initial-state)]
    (->> generic-initial-state
         f
         g/simplify
         (construct-function-exp generic-initial-state)
         eval)))

(defn- make-integrator
  "make-integrator takes a state derivative function (which in this
  system is assumed to be a map from a structure to a structure of the
  same shape, as differentiating a function does not change its
  shape), and returns an integrator, which is a function of several
  arguments: the initial state, an intermediate-state observation
  function, the step size desired, the final time to seek, and an
  error tolerance. If the observation function is not nil, it will be
  invoked with the time as first argument and integrated state as the
  second."
  [d:dt]
  (fn [initial-state observe step-size t ε & [options]]
    (let [state->array #(-> % flatten double-array)
          array->state #(struct/unflatten % initial-state)
          initial-state-array (doubles (state->array initial-state))
          derivative-fn (if (:compile options)
                          (compile-state-function initial-state d:dt)
                          #(-> % array->state d:dt))
          dimension (alength initial-state-array)
          integrator (GraggBulirschStoerIntegrator. 0. 1. (double ε) (double ε))
          equations (proxy [FirstOrderDifferentialEquations] []
                      (computeDerivatives
                        [_ ^doubles y ^doubles out]
                        ;; XXX the "safe" way
                        ;; (let [y' (doubles (-> y array->state d:dt state->array))]
                        ;;   (System/arraycopy y' 0 out 0 (alength y')))
                        ;; following is the compiled way
                        (let [y' (doubles (-> y derivative-fn state->array))]
                          (System/arraycopy y' 0 out 0 (alength y')))
                        )
                      (getDimension [] dimension))
          out (double-array dimension)]
      (when-not (:compile options)
        (log/warn "Not compiling function for ODE analysis"))
      (when observe
        ;; We implement the observation callback by adding a StepHandler
        ;; to the integration. The StepHandler is not invoked at every grid
        ;; point; rather, it is invoked once in a while over a range of time
        ;; within which the integrated function may be accurately evaluated.
        ;; The handler we install does this, invoking the callback for
        ;; each requested grid point within the valid range, ensuring that we
        ;; also invoke the callback for the final point.
        (.addStepHandler
         integrator
         (proxy [StepHandler] []
           (handleStep
             [^StepInterpolator interpolator is-last]
             (let [it0 (.getPreviousTime interpolator)
                   it1 (.getCurrentTime interpolator)
                   adjust (mod it0 step-size)
                   t0 (if (> adjust 0) (+ (- it0 adjust) step-size) it0)
                   last-state (when is-last (double-array (.getInterpolatedState interpolator)))]
               (doseq [t (range t0 it1 step-size)]
                 (.setInterpolatedTime interpolator t)
                 (observe t (-> interpolator .getInterpolatedState array->state)))
               (when is-last
                 (observe it1 (array->state last-state)))))
           (init [_ _ _]))))
      (.integrate integrator equations 0 initial-state-array t out)
      (array->state out))))

(defn state-advancer
  "state-advancer takes a state derivative function constructor
  followed by the arguments to construct it with. The state derivative
  function is constructed and an integrator is produced which takes
  the initial state, target time, and error tolerance as
  arguments. The final state is returned. The state derivative is
  expected to map a structure to a structure of the same shape,
  and is required to have the time parameter as the first element."
  [state-derivative & state-derivative-args]
  (let [d:dt (apply state-derivative state-derivative-args)
        I (make-integrator d:dt)]
    (fn [initial-state t ε & [options]]
      (I initial-state nil 0 t ε options))))

(defn evolve
  "evolve takes a state derivative function constructor and its
  arguments, and returns an integrator via make-integrator. In
  particular, the returned function accepts a callback function which
  will be invoked at intermediate grid points of the integration."
  [state-derivative & state-derivative-args]
  (let [d:dt (apply state-derivative state-derivative-args)]
    (make-integrator d:dt)))
