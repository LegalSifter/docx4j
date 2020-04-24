(defproject serv "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :min-lein-version "2.0.0"
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [org.docx4j/docx4j-core "8.1.7-SNAPSHOT"]
                 [org.apache.commons/commons-compress "1.13"]
                 [compojure "1.6.1"]
                 [cheshire "5.7.0"]
                 [org.slf4j/slf4j-log4j12 "1.7.22"]
                 [me.raynes/fs "1.4.6"]
                 [ring/ring-defaults "0.3.2"]]
  :plugins [[lein-ring "0.12.5"]]
  :ring {:handler serv.handler/app}
  :profiles
  {:dev {:dependencies [[javax.servlet/servlet-api "2.5"]
                        [ring/ring-mock "0.3.2"]]}})
