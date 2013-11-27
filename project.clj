(defproject hyzhenhok "0.1.0-SNAPSHOT"
  :description "a fledgling bitcoin implementation written in clojure."
  :url "http://github.com/danneu/hyzhenhok"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :jvm-opts ["-XX:MaxPermSize=128m"
             "-Xmx1g"
             ;"-Djava.awt.headless=true"
             ]
  :uberjar-name "../hyzhenhok-standalone.jar"
  :main hyzhenhok.cli
  :aot [hyzhenhok.cli]
  :ring {:handler hyzhenhok.explorer/app}
  :repositories {"sonatype-oss-public"
                 "https://oss.sonatype.org/content/groups/public/"}
  ;:global-vars {*warn-on-reflection* true}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.bouncycastle/bcprov-jdk15on "1.49"]
                 [potemkin "0.3.4"]
                 [commons-codec "1.8"]
                 [com.datomic/datomic-free "0.8.4254"]
                 [gloss "0.2.2"]
                 [org.clojure/core.typed "0.2.19"]
                 [expectations "1.4.56"]
                 [criterium "0.4.2"]
                 [org.clojure/math.numeric-tower "0.0.2"]
                 ;[org.clojure/tools.cli "0.2.4"]
                 ;; For hyzhenhok.explorer:
                 [ring/ring-jetty-adapter "1.2.1"]
                 [clj-time "0.6.0"]
                 [compojure "1.1.6"]
                 [hiccup "1.0.4"]]
  :profiles {:dev
             {:dependencies
              [[javax.servlet/servlet-api "2.5"]
               [ring-mock "0.1.5"]]}}
  :plugins [[lein-expectations "0.0.7"]
            [lein-ring "0.8.8"]])
