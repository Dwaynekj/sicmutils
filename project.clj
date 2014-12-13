(defproject math "0.0.1-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "The MIT License"
            :url "http://www.opensource.org/licenses/MIT"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/math.numeric-tower "0.0.4"]]
  :main math.repl
  :repl-options {:prompt (fn [ns]  (str "algebra [" ns "] > "))
                 :welcome "clojure algebra system"
                 :init-ns math.repl
                 :init (setup)
                 }
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}
             :repl {:plugins [[cider/cider-nrepl "0.8.1"]]}})
