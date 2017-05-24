(ns sicmutils.calculus.coordinate
  (:require [sicmutils
             [structure :as s]
             [matrix :as matrix]]
            [sicmutils.calculus
             [manifold :refer :all]
             [vector-field :refer :all]
             [form-field :refer :all]]))

(defn coordinate-functions
  [coordinate-system]
  (let [prototype (coordinate-prototype coordinate-system)]
    (s/mapr (fn [access-chain]
              (comp (apply s/component access-chain)
                    #(point->coords coordinate-system %)))
            (s/structure->access-chains prototype))))

(defn ^:private quotify-coordinate-prototype
  "Scmutils wants to allow forms like this:
     (using-coordinates (up x y) R2-rect ...)
   Note that x, y are unquoted. This function converts such an unquoted for
   into a quoted one that could be evaluated to return an up-tuple of the symbols:
     (up 'x 'y)
   Such an object is useful for s/mapr. The function xf is applied before quoting."
  [xf p]
  (let [q (fn q [p]
            (cond (and (sequential? p)
                       ('#{up down} (first p))) `(~(first p) ~@(map q (rest p)))
                  (vector? p) (mapv q p)
                  (symbol? p) `'~(xf p)
                  :else (throw (IllegalArgumentException. "Invalid coordinate prototype"))))]
    (q p)))

(defn ^:private symbols-from-prototype
  [p]
  (cond (and (sequential? p)
             ('#{up down} (first p))) (mapcat symbols-from-prototype (rest p))
        (vector? p) (mapcat symbols-from-prototype p)
        (symbol? p) `(~p)
        :else (throw (IllegalArgumentException. (str "Invalid coordinate prototype: " p)))))

(defmacro let-coordinates
  "Example:
    (let-coordinates [[x y] R2-rect
                      [r theta] R2-polar]
      body...)"
  [bindings & body]
  (when-not (even? (count bindings))
    (throw (IllegalArgumentException. "let-coordinates requires an even number of bindings")))
  (let [pairs (partition 2 bindings)
        prototypes (map first pairs)
        c-systems (mapv second pairs)
        coordinate-names (mapcat symbols-from-prototype prototypes)
        coordinate-vector-field-names (map coordinate-name->vf-name coordinate-names)
        coordinate-form-field-names (map coordinate-name->ff-name coordinate-names)]
    `(let [[~@c-systems :as c-systems#]
           (mapv with-coordinate-prototype
                 ~c-systems
                 ~(mapv #(quotify-coordinate-prototype identity %) prototypes))
           c-fns# (map coordinate-functions c-systems#)
           c-vfs# (map coordinate-basis-vector-fields c-systems#)
           c-ffs# (map coordinate-basis-oneform-fields c-systems#)
           ~(vec coordinate-names) (flatten c-fns#)
           ~(vec coordinate-vector-field-names) (flatten c-vfs#)
           ~(vec coordinate-form-field-names) (flatten c-ffs#)]
       ~@body)))

(defmacro using-coordinates
  "Example:
    (using-coordinates (up x y) R2-rect
      body...)

  Note: this is just a macro wrapping let-coordinates, the use of which is
  preferred."
  [coordinate-prototype coordinate-system & body]
  `(let-coordinates [~coordinate-prototype ~coordinate-system] ~@body))

(defn coordinate-system->vector-basis
  [coordinate-system]
  (coordinate-basis-vector-fields coordinate-system))

(defn ^:private c:generate
  [n orientation f]
  (if (= n 1)
    (f 0)
    (s/generate n orientation f)))

(defn vector-basis->dual
  [vector-basis coordinate-system]
  (let [prototype (coordinate-prototype coordinate-system)
        _ (println "prototype" prototype)
        vector-basis-coefficient-functions (s/mapr #(vector-field->components % coordinate-system) vector-basis)
        guts (fn [coords]
               (matrix/s:transpose (s/compatible-shape prototype)
                                   (matrix/s:inverse
                                    (s/compatible-shape prototype)
                                    (s/mapr #(% coords) vector-basis-coefficient-functions)
                                    prototype)
                                   prototype))
        oneform-basis-coefficient-functions (c:generate (:dimension (manifold coordinate-system))
                                                        ::s/up
                                                        #(comp (s/component %) guts))
        oneform-basis (s/mapr #(components->oneform-field % coordinate-system) oneform-basis-coefficient-functions)]
    oneform-basis))
