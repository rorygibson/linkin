(ns linkin.core
  (:require [org.httpkit.client :as http])
  (:import [org.jsoup Jsoup]
           [org.jsoup.nodes Document]))


(def links (atom '()))


(def handler-agent (agent ""))



(defn enqueue-links
  [ls]
  (println "[enqueue-links] accepting links" ls)
  (swap! links into ls))


(defn run-user-handler
  [handler-fn url h]
  (println "[run-user-handler] sending to" (.toString handler-fn))
  (send handler-agent (fn [_] (handler-fn url h))))


(defn extract-anchors
  "Uses JSoup magic to extract anchor tags"
  [^String body ^String base-uri]
  (let [doc (Jsoup/parse body)
        doc (doto doc (.setBaseUri base-uri))
        anchors (.select doc "a")]
    (map #(.attr % "abs:href") anchors)))


(defn http-kit-handler
  "Called as a callback by the HTTP-Kit fetcher.
   Obtains and enqueues for processing the anchors and full body text from the response"
  [handler url {:keys [status headers body error opts] :as resp}]
  (println "[http-kit-handler] called for" url)
  
  (if error
    (println "[http-kit-handler] Failed, exception is " error)    
    (do
      (enqueue-links (extract-links body url))
      (run-user-handler handler url body))))


(defn crawl
  "Start crawling at the given URL"
  [url handler]
  (http/get url {} (partial http-kit-handler handler url)))
