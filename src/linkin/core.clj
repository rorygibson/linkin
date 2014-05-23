(ns linkin.core
  (:require [irobot.core]
            [mundi.core :refer :all]
            [org.httpkit.client :as http]
            [clojure.tools.logging :refer [debug info error warn trace]])
  (:import [org.jsoup Jsoup]
           [org.jsoup.nodes Document Node]))


(def TEXT-HTML "text/html")
(def USER_AGENT "irobot 0.1.0")


(defn initial-state
  "Define initial state var"
  []
  { :crawled-urls #{}
    :urls-from-sitemaps #{}
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


(defn urls-from-sitemaps
  "List out URLs retrieved from a sitemap crawl"
  []
  (:urls-from-sitemaps @application))


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


(defn record-url-from-sitemap
  [url]
  (swap! application (fn [app] (assoc app :urls-from-sitemaps (conj (:urls-from-sitemaps app) url)))))


(defn set-base-url
  "Sets the base URL for a crawl"
  [u]
  (swap! application assoc :base-url u))

         
(defn local?
  "Determine if a URL is local relative to a certain base URL"
  [^String url ^String base-url]
  (.startsWith url base-url))


(defn already-crawled?
  "Check if we have already crawled this URL or a similar URL (<url> + '#')"
  [^String url crawled-urls]
  (let [similar-url (str url "#")
        done (some #{url} crawled-urls)
        done-similar (some #{similar-url} crawled-urls)]
    (or done done-similar)))


(defn relativize-url
  [url]
  (str "/" (clojure.string/join "/" (drop 2 (filter (comp not empty?) (clojure.string/split url #"/"))))))


(defn crawl?
  "We should ony crawl a URL if it's local and we haven't already crawled it (or a similar URL)"
  [^String url robots already-crawled ^String base-url]

  (let [relative-url (relativize-url url)
        allowed (irobot.core/allows? robots USER_AGENT relative-url)
        done (already-crawled? url already-crawled)
        local (local? url base-url)]
    (and (not done) local allowed)))


(defn simple-body-parser
  "Simplest possible body parser; prints out URL content type and length of body, for HTML documents"
  [^String url ^String content-type body]
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
      (map (fn [^org.jsoup.nodes.Node a] (.attr a "abs:href")) anchors))
    []))


(defn response-handler
  "Called as a callback by the HTTP-Kit fetcher.
   Obtains and enqueues for processing the anchors and body text from the response"
  [robots body-parser ^String url {:keys [status headers body error opts] :as resp}]

  (if (crawl? url robots (crawled-urls) (base-url))

    (let [content-type (:content-type headers)
          anchors (extract-anchors body content-type url)]
      (mark-as-crawled url)
      (trace "[response-handler] got" url content-type)
        
      (doseq [link anchors]      
        (if (crawl? link robots (crawled-urls) (base-url))
          (send (link-agent) (fn [_] (http/get link (partial response-handler robots body-parser link))))))

      (send (handler-agent) (fn [_] (body-parser url content-type body))))))


(defn get-robots-txt
  [base-url]
  (let [url (str base-url "/robots.txt")]
    (info "[get-robots-txt] Requesting robots rules from" url)
    (:body @(http/get url))))


(defn sitemap-handler
  "Request handler for responses to sitemap requests"
  [^String url {:keys [status headers body error opts] :as resp}]
  (mark-as-crawled url)

  (debug "[sitemap-handler] got" url)
  
  (if (and body (sitemap-index? body))
    (let [locs (find-sitemaps body)]
      (debug "[sitemap-handler] got" (count locs) "sitemap locs")

      (doall
       (map
        (fn [l]
          (debug "[sitemap-handler] processing" l)
          (send (link-agent)
                (fn [_]
                  (debug "[sitemap-handler] agent called with sitemap loc" l)
                  (http/get (:loc l) (partial sitemap-handler (:loc l))))))
        locs)))
    
    (let [found (find-urls body)]
      (debug "[sitemap-handler] got" (count found) "URLs")      
      (doall (map record-url-from-sitemap found))      
      (debug "[sitemap-handler] total count is" (count (urls-from-sitemaps))))))


(defn crawl-sitemaps
  "Starting from a base sitemap.xml URL, locate and parse all sitemaps and sitemapindexes
to produce a list of target URLs"
  [sitemap-url]
  (http/get sitemap-url {} (partial sitemap-handler sitemap-url)))


(defn crawl
  "Start crawling at the given base URL"
  [^String url body-parser]
  (set-base-url url)
  
  (info "[crawl] Starting crawl of" (base-url))

  (let [robots-txt (get-robots-txt url)
        robots (irobot.core/robots robots-txt)]
        
    (if (crawl? url robots (crawled-urls) url)
      (http/get url {} (partial response-handler robots body-parser url))
      "Not crawling - base URL not allowed")
    "Crawl started"))
