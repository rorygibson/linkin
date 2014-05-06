(ns linkin.core
  (:require [org.httpkit.client :as http]
            [clojure.tools.logging :refer [debug info error warn trace]])
  (:import [org.jsoup Jsoup]
           [org.jsoup.nodes Document]))


;;
;; Constants
;; 

(def TEXT-HTML "text/html")


;;
;; State
;;

(defn initial-state
  []
  { :crawled-urls #{}
    :link-agent (agent "")
    :handler-agent (agent "")
    :base-url ""})


(def application (atom (initial-state)))
(defn reset-application! [] (swap! application initial-state))

(defn crawled-urls [] (:crawled-urls @application))
(defn link-agent [] (:link-agent @application))
(defn handler-agent [] (:handler-agent @application))
(defn base-url [] (:base-url @application))


(defn mark-as-crawled
  [url]
  (swap! application (fn [app] (assoc app :crawled-urls (conj (:crawled-urls app) url)))))


(defn set-base-url
  [u]
  (swap! application assoc :base-url u))





         
(defn local?
  [url base-url]
  (.startsWith url base-url))


(defn already-crawled?
  [url crawled-urls]
  (let [similar-url (str url "#")
        done (some #{url} crawled-urls)
        done-similar (some #{similar-url} crawled-urls)]
    (or done done-similar)))


(defn crawl?
  [url already-crawled base-url]

  (let [done (already-crawled? url already-crawled)
        local (local? url base-url)]
    (and (not done) local)))


(defn simple-body-parser
  [url content-type body]
  (if (= content-type TEXT-HTML)
    (debug "[simple-body-parser] got [" url "] of type [" content-type "] with " (count body) " bytes data")))


(defn extract-anchors
  "Uses JSoup magic to extract anchor tags"
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
   Obtains and enqueues for processing the anchors and full body text from the response"
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
  "Start crawling at the given URL"
  [url body-parser]
  (set-base-url url)
  
  (info "[crawl] Starting crawl of" (base-url))

  (http/get url {} (partial response-handler body-parser (base-url))))
