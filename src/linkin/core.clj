(ns linkin.core
  (:require [org.httpkit.client :as http]
            [clojure.tools.logging :refer [debug info error warn trace]])
  (:import [org.jsoup Jsoup]
           [org.jsoup.nodes Document]))


(def crawled-urls (atom #{}))
(def link-agent (agent '()))
(def handler-agent (agent ""))
(def base-url (atom ""))


(defn crawl?
  [url]

  (let [similar-url (str url "#")
        done (some #{url} @crawled-urls)
        done-similar (some #{similar-url} @crawled-urls)
        not-done (not (or done done-similar))
        local (.startsWith url @base-url)
        result (and not-done local)]
    result))


(defn simple-body-parser
  [url content-type body]
  (if (= content-type "text/html")
    (debug "[simple-body-parser] got [" url "] of type [" content-type "] with " (count body) " bytes data")))


(defn extract-anchors
  "Uses JSoup magic to extract anchor tags"
  [body ^String content-type ^String base-uri]
  (if (= content-type "text/html")
    (let [body (if (nil? body) "" body)
          doc (Jsoup/parse body)
          doc (doto doc (.setBaseUri base-uri))
          anchors (.select doc "a")]
      (map #(.attr % "abs:href") anchors))
    []))


(defn mark-as-crawled
  [url]
  (swap! crawled-urls conj url))


(defn response-handler
  "Called as a callback by the HTTP-Kit fetcher.
   Obtains and enqueues for processing the anchors and full body text from the response"
  [body-parser url {:keys [status headers body error opts] :as resp}]

  (if (crawl? url)

    (let [content-type (:content-type headers)
          anchors (extract-anchors body content-type url)]
      (mark-as-crawled url)
      (trace "[response-handler] got" url content-type)
        
      (doseq [link anchors]      
        (if (crawl? link)
          (send link-agent (fn [_] (http/get link (partial response-handler body-parser link))))))

      (send handler-agent (fn [_] (body-parser url content-type body))))))


(defn crawl
  "Start crawling at the given URL"
  [url body-parser]
  (swap! base-url (fn [_] url))
  
  (info "[crawl] Starting crawl of" @base-url)

  (http/get url {} (partial response-handler body-parser @base-url)))
