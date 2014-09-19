(ns math.generic-test
  (:require [clojure.test :refer :all]
            [math.generic :refer :all]))

(def T1 (dtree-insert
         (dtree-insert
          (dtree-insert empty-dtree :op1 [:p1 :p2])
          :op2 [:p2 :p1])
         :op3 [:p2 :p1 :p3]))

(deftest dtree-1
  (testing "lookup"
    (is (= :op1 (dtree-lookup T1 [#{:p1} #{:p2}])))
    (is (= :op2 (dtree-lookup T1 [#{:p2} #{:p1}])))
    (is (= :op3 (dtree-lookup T1 [#{:p2} #{:p1} #{:p3}])))
    (is (not (dtree-lookup T1 [#{:p2} #{:p1} #{:p3} #{:p1}])))
    (is (not (dtree-lookup T1 [])))
    (is (not (dtree-lookup T1 [#{:p2}#{:p2}])))))

(defhandler :y [integer? integer?] :intint)
(defhandler :y [integer? float?]   :intfloat)
(defhandler :y [string? string?]   :strstr)
(defhandler :z [number? string?]   :numstr)

(deftest handler-map
  (testing "lookup"
    (is (= :intint (findhandler :y [1 1])))
    (is (not (findhandler :y [1 "2"])))
    (is (= :intfloat (findhandler :y [1 3.13])))
    (is (= :numstr (findhandler :z [1e10, "baz"])))
    (is (not (findhandler :y [1e10, "baz"])))))

(defn multiply-string
  [n s]
  (apply str (repeat n s)))

(defn product-string
  [s t]
  (apply str(for [cs s ct t] (str cs ct))))

(def any? (constantly true))

(defhandler :* [number? string?] multiply-string)
(defhandler :* [string? string?] product-string)
(defhandler :+ [string? string?] str)

(deftest handler-fn
  (testing "multiply-string"
    (is (= "foofoofoo" (multiply-string 3 "foo")))
    (is (= "" (multiply-string 0 "bar")))
    (is (= "" (multiply-string -2 "bar")))
    (is (= "barbarbar" (let [args [3 "bar"]
                             h (findhandler :* args)]
                         (apply h args)))))
  (testing "mul"
    (is (= "bazbaz" (mul 2 "baz")))
    (is (= "quxquxqux" (mul 3 "qux")))
    (is (thrown? IllegalArgumentException
                 (mul "qux" 3)))
    (is (= "cecrcicnoeoroionlelrlilnieiriiinnenrninn" (mul "colin" "erin")))
    (is (= "eceoeleienrcrorlrirnicioiliiinncnonlninn" (mul "erin" "colin"))))
  (testing "add"
    (is (= "foobar" (add "foo" "bar")))
    (is (= 4 (add 2 2)))
    (is (= 3.5 (add 1.5 2)))
    (is (= 13/40 (add 1/5 1/8))))
  (testing "symbol"
    (is (= '(add 3 x) (add 3 'x)))
    (is (= '(add x 4.8) (add 'x 4.8)))
    (is (= '(add x y) (add 'x 'y)))
    (is (= '(add (mul 3 x) (mul y 4)) (add (mul 3 'x) (mul 'y 4))))
    ))

