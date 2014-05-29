(ns linkin.parsers
  (:require [clojure.tools.logging :refer [debug info error warn trace]]))


(def ^{:private true} TEXT-HTML "text/html")


(defn simple-body-parser
  "Simplest possible body parser; prints out URL content type and length of body, for HTML documents"
  [^String url ^String content-type body]
  (if (= content-type TEXT-HTML)
    (debug "[simple-body-parser] got [" url "] of type [" content-type "] with " (count body) " bytes data")))

