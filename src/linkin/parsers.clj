(ns linkin.parsers
  (:require [clojure.java.io :refer :all]
            [clojure.tools.logging :refer [debug info error warn trace]]))


(def ^{:private true} TEXT-HTML "text/html")
(def ^{:private true} TEMP-DIR "/tmp/linkin")


(defn simple-body-parser
  "Simplest possible body parser; prints out URL content type and length of body, for HTML documents"
  [^String url ^String content-type body]
  (if (= content-type TEXT-HTML)
    (debug "[simple-body-parser] got [" url "] of type [" content-type "] with " (count body) " bytes data")))


(defn saving-body-parser
  "Writes body data as a file"
  [^String url ^String content-type body]
  (let [fname (str TEMP-DIR (clojure.string/join "-" (drop 2 (clojure.string/split url #"/"))))]
    (debug "[saving-body-parser] writing to" fname)
    (try
      (with-open [wrtr (writer fname)]
        (.write wrtr body))
      (catch Exception e
        (error e)))))
