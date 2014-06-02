(ns linkin.core
  (:require [clojure.core.async :refer [go put! chan <!! <! >! dropping-buffer]]
            [irobot.core]
            [linkin.urls :refer :all]
            [linkin.html :refer :all]
            [linkin.parsers :refer :all]
            [linkin.http :refer :all]
            [mundi.core :refer :all]
            [org.httpkit.client :as http]
            [clojure.tools.logging :refer [debug info error warn trace]]))




(defn create-memory
  "Create a data structure for a crawl"
  ([]
     { :crawled-urls #{}
      :urls-from-sitemaps #{}})
  ([_] (create-memory)))


(defn record-url-from-sitemap
  "Record a located sitemap file"
  [memory url]
  (swap! memory
         (fn [mem] (assoc mem :urls-from-sitemaps (conj (:urls-from-sitemaps mem) url)))))


(defn mark-as-crawled
  "Indicates that we have already crawled a specific URL"
  [memory url]
  (swap! memory
         (fn [mem] (assoc mem :crawled-urls (conj (:crawled-urls mem) url)))))


(defn get-robots-txt
  "Return the content of the robots.txt for a given base URL"
  [base-url]
  (let [url (str base-url "/robots.txt")]
    (info "[get-robots-txt] Requesting robots rules from" url)
    (go (:body (<! (http-get url))))))


(defn consume
  "Take in URL, content-type and body. Package & enqueue on a channel for processing."
  [consumer {{url :url} :opts {content-type :content-type} :headers body :body}]
  (go
   (>! consumer {:url url :content-type content-type :body body})))


(defn make-body-consumer
  "Sets up a channel, and binds a body-parsing function to it.
Assumes that the body-parser takes a URL, content-type and body (all Strings).
Assumes that the channel will contain messages, each of which is a map of those 3 elements."
  [body-parser]
  (let [c (chan)]
    (go (loop []
          (when-let [v (<! c)]
            (let [body (:body v)
                  url (:url v)
                  content-type (:content-type v)]
              (body-parser url content-type body)
              (recur)))))
    c))


(defn do-get
  "If (pred url) is true, asynchronously fetch the contents of URL an pas it to handler."
  [pred handler url]
  (if (pred url)
    (go (->> url
             http-get
             <!
             handler))))


(defn response-handler
  "Taking in an HTTP response, extract anchors from the body, kick off fetches on those URLs, then consume the body (enqueue the body for further processing)"
  [mem pred body-consumer {{url :url} :opts {content-type :content-type} :headers body :body :as resp}]
  
  (let [urls (extract-anchors body content-type url)
        handler (partial response-handler mem pred body-consumer)]
    (debug "[response-handler] got [" url "] of type [" content-type "] containing " (count urls) "URLs")
    
    (doseq [u urls]
      (do-get pred handler u)
      (mark-as-crawled mem u))

    (consume body-consumer resp)))


(defn sitemap-handler
  "Given an HTTP response containing a sitemap.xml, prase it, and recur through all sub-sitemaps,
   building up the list of URLs in the memory"
  [mem {{url :url} :opts {content-type :content-type} :headers body :body :as resp}]
  (let [sitemap-locs (find-sitemaps body)
        page-locs (find-urls body)]
    (debug "[sitemap-handler] got" (count sitemap-locs) "sitemap locs and" (count page-locs) "page locs")

    (if (and body (sitemap-index? body))
      (doseq [l sitemap-locs]
        (debug "[sitemap-handler] processing" l)
        (do-get (fn [_] true) (partial sitemap-handler mem) (:loc l)))

      (doseq [l page-locs]
        (record-url-from-sitemap mem l)))))


(defn crawl-sitemaps
  "Starting from a base sitemap.xml URL, locate and parse all sitemaps and sitemapindexes
to produce a list of target URLs"
  [mem url]
  (let [handler (partial sitemap-handler mem)
        pred (fn [_] true)]    
    (do-get pred handler url))
  mem)


(defn crawl
  "Start crawling at the given base URL"
  [^String base-url body-parser]
  
  (info "[crawl] Starting crawl of" base-url)

  (let [robots-txt (get-robots-txt base-url)
        robots (irobot.core/robots (<!! robots-txt))
        body-consumer (make-body-consumer body-parser)
        memory (atom (create-memory))
        pred #(crawl? %1 robots memory base-url)
        handler (partial response-handler memory pred body-consumer)] ;; requires URL as param

    ;; TODO
    ;; get seq of any sitemaps from the robots.txt
    ;; for each, kick off a go block to fetch it and process the
    ;;   contents
    ;; if it's a sitemapindex, recurse this algorithm
    ;; if it's a sitemap, add the locs to state

    (do-get pred handler base-url)
    memory))
