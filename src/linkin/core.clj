(ns linkin.core
  (:require [org.httpkit.client :as http]
            [clojure.tools.logging :refer [debug info error warn trace]])
  (:import [org.jsoup Jsoup]
           [org.jsoup.nodes Document]))


(def TEXT-HTML "text/html")


(defn initial-state
  "Define initial state var"
  []
  { :crawled-urls #{}
    :link-agent (agent "")
    :handler-agent (agent "")
    :base-url ""})


(def application (atom (initial-state)))


(defn reset-application!
  "Return to starting state"
  []
  (swap! application (fn [_] (initial-state))))


(defn crawled-urls
  "List out URLs crawled so far (since last reset-application!)"
  []
  (:crawled-urls @application))


(defn link-agent
  "Return reference ti agent for queueing fresh links"
  []
  (:link-agent @application))


(defn handler-agent
  "Return referece to agent for queueing body parsing"
  []
  (:handler-agent @application))


(defn base-url
  "Get the base url for this crawl"
  []
  (:base-url @application))


(defn mark-as-crawled
  "Indicates that we have already crawled a specific URL"
  [url]
  (swap! application (fn [app] (assoc app :crawled-urls (conj (:crawled-urls app) url)))))


(defn set-base-url
  "Sets the base URL for a crawl"
  [u]
  (swap! application assoc :base-url u))

         
(defn local?
  "Determine if a URL is local relative to a certain base URL"
  [url base-url]
  (.startsWith url base-url))


(defn already-crawled?
  "Check if we have already crawled this URL or a similar URL (<url> + '#')"
  [url crawled-urls]
  (let [similar-url (str url "#")
        done (some #{url} crawled-urls)
        done-similar (some #{similar-url} crawled-urls)]
    (or done done-similar)))


(defn crawl?
  "We should ony crawl a URL if it's local and we haven't already crawled it (or a similar URL)"
  [url already-crawled base-url]

  (let [done (already-crawled? url already-crawled)
        local (local? url base-url)]
    (and (not done) local)))


(defn simple-body-parser
  "Simplest possible body parser; prints out URL content type and length of body, for HTML documents"
  [url content-type body]
  (if (= content-type TEXT-HTML)
    (debug "[simple-body-parser] got [" url "] of type [" content-type "] with " (count body) " bytes data")))


(defn extract-anchors
  "Uses JSoup magic to extract anchor tags from HTML"
  [body ^String content-type ^String base-uri]
  (if (= content-type TEXT-HTML)
    (let [body (if (nil? body) "" body)
          doc (Jsoup/parse body)
          doc (doto doc (.setBaseUri base-uri))
          anchors (.select doc "a")]
      (map #(.attr % "abs:href") anchors))
    []))


(defn response-handler
  "Called as a callback by the HTTP-Kit fetcher.
   Obtains and enqueues for processing the anchors and body text from the response"
  [body-parser url {:keys [status headers body error opts] :as resp}]

  (if (crawl? url (crawled-urls) (base-url))

    (let [content-type (:content-type headers)
          anchors (extract-anchors body content-type url)]
      (mark-as-crawled url)
      (trace "[response-handler] got" url content-type)
        
      (doseq [link anchors]      
        (if (crawl? link (crawled-urls) (base-url))
          (send (link-agent) (fn [_] (http/get link (partial response-handler body-parser link))))))

      (send (handler-agent) (fn [_] (body-parser url content-type body))))))


(defn crawl
  "Start crawling at the given base URL"
  [url body-parser]
  (set-base-url url)
  
  (info "[crawl] Starting crawl of" (base-url))

  (http/get url {} (partial response-handler body-parser (base-url))))
