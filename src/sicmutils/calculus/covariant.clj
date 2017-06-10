(ns sicmutils.calculus.covariant
  (:require
   [sicmutils
    [generic :as g]
    [structure :as s]
    [operator :as o]]
   [sicmutils.calculus
    [basis :as b]
    [manifold :as m]
    [vector-field :as vf]
    [form-field :as ff]])
  )
(defn ^:private vector-field-Lie-derivative
  [X]
  (let [name `(~'Lie-derivative ~(m/diffop-name X))]
    (o/make-operator
     (fn [Y]
       (cond (fn? Y) (X Y)
             (vf/vector-field? Y) (o/commutator X Y)
             (ff/form-field? Y) (let [k (ff/get-rank Y)]
                               (ff/procedure->nform-field
                                (fn [& vectors]
                                  (assert (= k (count vectors)) `(~'≠ ~k ~(count vectors) ~@vectors ~@(map meta vectors)))
                                  (g/- ((g/Lie-derivative X) (apply Y vectors))
                                       (reduce g/+ (for [i (range 0 k)]
                                                     (apply Y (map-indexed (fn [j v]
                                                                             (if (= j i)
                                                                               ((g/Lie-derivative X) v)
                                                                               v))
                                                                           vectors))))))
                                k
                                `((~'Lie-derivative ~(m/diffop-name X)) ~(m/diffop-name Y))))
             :else (throw (UnsupportedOperationException. "Can't take the Lie derivative of that yet"))))
     `(Lie-derivative ~(m/diffop-name X)))))

(defmethod g/Lie-derivative [::vf/vector-field] [V] (vector-field-Lie-derivative V))

(defn interior-product
  [V]
  (assert (vf/vector-field? V))
  (fn [omega]
    (assert (ff/form-field? omega))
    (let [k (ff/get-rank omega)]
      (ff/procedure->nform-field
       (fn [& vectors]
         (assert (= (dec k) (count vectors)))
         (apply omega V vectors))
       (dec k)
       `((~'interior-product ~(m/diffop-name V)) ~(m/diffop-name omega))))))

(defn make-Christoffel
  [symbols basis]
  {:type ::Christoffel
   :symbols symbols
   :basis basis})

(def Christoffel->basis :basis)
(def Christoffel->symbols :symbols)

(defn make-Cartan
  [forms basis]
  {:type ::Cartan
   :forms forms
   :basis basis})

(def Cartan->basis :basis)
(def Cartan->forms :forms)

(defn Christoffel->Cartan
  [Christoffel]
  (assert (= (:type Christoffel) ::Christoffel))
  (let [basis (Christoffel->basis Christoffel)
        Christoffel-symbols (Christoffel->symbols Christoffel)]
    (make-Cartan (g/* Christoffel-symbols (b/basis->oneform-basis basis))
                 basis)))

(defn Cartan-transform
  [cartan basis-prime]
  (let [basis (Cartan->basis cartan) ;; tuple of basis vectors
        forms (Cartan->forms cartan)
        prime-dual-basis (b/basis->oneform-basis basis-prime)
        prime-vector-basis (b/basis->vector-basis basis-prime)
        vector-basis (b/basis->vector-basis basis)
        oneform-basis (b/basis->oneform-basis basis)
        J-inv (s/mapr oneform-basis prime-vector-basis)
        J (s/mapr prime-dual-basis vector-basis)
        omega-prime-forms (ff/procedure->oneform-field
                           (fn [u]
                             (+ (* J (u J-inv))
                                (* J (* (forms u) J-inv))))
                           'omega-prime-forms)]
    (make-Cartan omega-prime-forms basis-prime)))
