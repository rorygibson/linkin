(ns linkin.parsers
  (:require [clojure.java.io :refer :all]
            [clojure.tools.logging :refer [debug info error warn trace]]))


(def ^{:private true} TEXT-HTML "text/html")
(def ^{:private true} TEMP-DIR (System/getProperty "savedFilesDir" "/tmp/linkin/"))


(defn simple-body-parser
  "Simplest possible body parser; prints out URL content type and length of body, for HTML documents"
  [^String url ^String content-type body]
  (if (= content-type TEXT-HTML)
    (debug "[simple-body-parser] got [" url "] of type [" content-type "] with " (count body) " bytes data")))


(defn saving-body-parser
  "Writes body data as a file"
  [^String url ^String content-type body]
  (let [body (if body body "")
        fname (str TEMP-DIR (clojure.string/join "-" (drop 2 (clojure.string/split url #"/"))))]
    (trace "[saving-body-parser] writing body to" fname)
    (try
      (with-open [wrtr (writer fname)]
        (.write wrtr body))
      (catch Exception e
        (error "[saving-body-parser] exception writing data to" fname e)))))
