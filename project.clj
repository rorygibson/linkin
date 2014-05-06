(defproject linkin "0.1.0-SNAPSHOT"
  :description "Web crawling with async magic"
  :url "http://github.com/rorygibson/linkin"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :jvm-opts ["-Xmx1g" "-server"] 
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [http-kit "2.1.16"]
                 [org.jsoup/jsoup "1.7.3"]
                 [log4j "1.2.17" :exclusions [javax.mail/mail javax.jms/jms com.sun.jdmk/jmxtools com.sun.jmx/jmxri]]
                 [org.clojure/tools.logging "0.2.6"]])
