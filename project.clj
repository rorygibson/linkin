(defproject linkin "0.1.0-SNAPSHOT"
  :description "Web crawling with async magic"
  
  :url "http://github.com/rorygibson/linkin"
  
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  
  :main linkin.core

  :jvm-opts ["-Xms500m" "-Xmx500m" "-server"] 

  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/core.async "0.1.303.0-886421-alpha"]
                 [http-kit "2.1.18"]
                 [irobot/irobot "0.1.1-SNAPSHOT"]
                 [mundi "0.1.0-SNAPSHOT"]
                 [org.jsoup/jsoup "1.7.3"]
                 [log4j "1.2.17"]
                 [org.clojure/tools.logging "0.2.6"]]

  :profiles { :dev
              {
               :dependencies [[midje "1.6.3"]]
               :plugins      [[lein-ancient "0.5.4" :exclusions [org.clojure/clojure commons-codec org.clojure/data.xml]]
                              [lein-midje "3.1.1"]]}
              })
