(ns math.expression
  (:require [math.value :as v]
            [clojure.walk :refer :all]))

(declare freeze-expression)

(defrecord Expression [type expression]
  v/Value
  (nullity? [_] false)                                      ;; XXX what if it's a wrapped zero? one?
  (unity? [_] false)
  (zero-like [_] 0)
  (numerical? [x] (= (:type x) ::number))
  (exact? [_] false)
  (sort-key [_] 17)
  (compound? [_] false)
  (freeze [x] (-> x :expression freeze-expression)))

(defn make [x]
  (Expression. ::number x))

(defn literal-number
  [expression]
  (if (number? expression)
    expression
    (Expression. ::number expression)))

(defn abstract? [^Expression x]
  ;; TODO: GJS also allows for up, down, matrix here. We do not yet have
  ;; abstract structures.
  (= (:type x) ::number))

(defn expression-of
  [expr]
  (cond (instance? Expression expr) (:expression expr)
        (symbol? expr) expr
        :else (throw (IllegalArgumentException. (str "unknown expression type:" expr)))))


(defn variables-in
  "Return the 'variabls' (e.g. symbols) found in the expression x,
  which is an unwrapped expression."
  [x]
  (if (symbol? x) #{x}
                  (->> x flatten (filter symbol?) (into #{}))))


(defn walk-expression
  "Walk the unwrapped expression x in postorder, replacing symbols found there
  with their values in the map environment, if present; an unbound
  symbol is an error. Function applications are applied."
  [environment]
  (fn walk [x]
    ;(prn "WALK" x environment)
    (cond (number? x) x
          (symbol? x) (if-let [binding (x environment)]
                        binding
                        (throw (IllegalArgumentException.
                                 (str "no binding for " x " found."))))
          (instance? Expression x) (walk (expression-of x))
          (sequential? x) (apply (walk (first x)) (map walk (rest x)))

          :else (throw (IllegalArgumentException. (str "unknown expression type " x))))))

(defn print-expression
  [x]
  (postwalk
    (fn [x]
      (cond (symbol? x) (let [sym-ns (namespace x)
                              sym-name (name x)]
                          ; kind of a hack, but we don't want a circular dependency
                          ; here.
                          (if (and (= sym-ns "math.generic")
                                   (= sym-name "divide"))
                            '/
                            (symbol sym-name)))
            :else x))
    (freeze-expression x)))

(defn freeze-expression
  "Freezing an expression means removing wrappers and other metadata
  from subexpressions, so that the result is basically a pure
  S-expression with the same structure as the input. Doing this will
  rob an expression of useful information fur further computation; so
  this is intended to be done just before simplification and printing, to
  simplify those processes."
  [x]
  (cond (keyword? x) x
        (satisfies? v/Value x) (v/freeze x)
        (sequential? x) (map freeze-expression x)
        :else x))

(println "expression initialized")

;; this guy goes in here. metadata? or a Value?
;; expression of type T? predicate for experssionator?
;;
;; (define (make-real-literal expression)
;;   (let ((e (make-numerical-literal expression)))
;;     (add-property! e 'real #t)
;;     e))

;; (define (make-literal type-tag expression)
;;   (list type-tag (list 'expression expression)))

;; property management: plan: do this with metadata ?

;;
;; (define (add-property! abstract-quantity property-name property-value)
;;   (if (pair? abstract-quantity)
;;       (set-cdr! (last-pair abstract-quantity)
;;              (list (list property-name property-value)))
;;       (error "Bad abstract quantity -- ADD-PROPERTY!")))

;; (define ((has-property? property-name) abstract-quantity)
;;   (cond ((pair? abstract-quantity)
;;       (assq property-name (cdr abstract-quantity)))
;;      ((symbol? abstract-quantity)
;;       (if (eq? property-name 'expression)
;;           (list 'expression abstract-quantity)
;;           (error "Symbols have only EXPRESSION properties")))
;;      (else
;;       (error "Bad abstract quantity"))))

;; (define (get-property abstract-quantity property-name #!optional default)
;;   (cond ((pair? abstract-quantity)
;;       (let ((default (if (default-object? default) #f default))
;;             (v (assq property-name (cdr abstract-quantity))))
;;         (if v (cadr v) default)))
;;      ((symbol? abstract-quantity)
;;       (if (eq? property-name 'expression)
;;           abstract-quantity
;;           default))
;;      (else
;;       (error "Bad abstract quantity"))))


;;
;; general organization of simplification in scmutils from the top down
;
; first of all, there seems to be a generic simplification operator that we
; will have to install. It takes care of things like propagating simplification
; through structured objects. There's even a case for simplify-differential...
; but when would a "final" value contain a differential? it's not obvious
; that that would ever be called under normal circumstances. Maybe we'll leave
; it out on the first go-round.
;
; Hmmm. insofar as GJS has (assign-operation 'simplify expression abstract-{column,row,matrix}?
; perhaps he is using simplify to unwrap expression wrapping in certain circumstances. I wonder
; if we should do the same?

; a ha. perhaps the key is here:
; (define (simplify-literal-number expr)
;   (new-simplify (expression expr))
;
; that's how we get to the rules tables!
; but within them, there's plenty of rcf:simplify, which we aren't anywhere clost
; to having, but we might get fpf:simplify to work, if we can understand the
; horrible statefulness of it: is it just to memorize previous simplifications?
; that is probably a win, judging by the unsimplified output we're observing from
; differentiations.
;
; (define fpf:simplify
;(hash-memoize-1arg
;  (compose canonical-copy
;           (expression-simplifier fpf:analyzer)))
; we don't need canonical-copy: that's some kind of structure-equality hack
; in scmutils that we can almost certainly live without in Clojure.
; expression-simplifier is where the hurt is.
;
; inside an analyzer of a given type (say, FPF, all we have)
; there is base-simplify, which basically pumps an expression
; through the lifting and lowering parts of the analyzer hoping
; for a simplification.
;
; The idea being that like terms would be rounded up, I think.
; let's begin there.

;

;
