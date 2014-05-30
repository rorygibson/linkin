(ns linkin.html
  (:require [clojure.tools.logging :refer [debug info error warn trace]])
  (:import [org.jsoup Jsoup]
           [org.jsoup.nodes Document Node]))


(def ^{:private true} TEXT-HTML "text/html")


(defn extract-anchors
  "Uses JSoup magic to extract anchor tags from HTML"
  [body ^String content-type ^String base-uri]
  (if (and content-type (.startsWith content-type TEXT-HTML))
    (let [body (if (nil? body) "" body)
          doc (Jsoup/parse body)
          doc (doto doc (.setBaseUri base-uri))
          anchors (.select doc "a")]
      (map (fn [^org.jsoup.nodes.Node a] (.attr a "abs:href")) anchors))
    (do
      (debug "[extract-anchors] not a parseable content type:" content-type)
      [])))



