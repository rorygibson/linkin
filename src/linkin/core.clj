(ns linkin.core
  (:gen-class)
  (:require [clojure.core.async :refer [go put! chan <!! <! >! >!! buffer thread]]
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
     {:sitemap-channel (chan (buffer 1000))
      :response-channel (chan (buffer 1000))
      :crawled-urls #{}
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
  (let [url (str base-url "/robots.txt")
        c (chan)]
    (info "[get-robots-txt] Requesting robots rules from" url)
    (http-get c url)
    (:body (<!! c))))


(defn response-handler
  "Taking in an HTTP response, extract anchors from the body, kick off fetches on those URLs, then consume the body (enqueue the body for further processing)"
  [mem pred body-parser {{url :url} :opts {content-type :content-type} :headers body :body :as resp}]

  (debug "[response-handler] handling" url)
  (let [urls (extract-anchors body content-type url)
        target-urls (filter pred urls)]
    
    (debug "[response-handler] got [" url "] of type [" content-type "] containing" (count urls) "URLs, of which we want to crawl" (count target-urls))

    (if (empty? content-type)
      (debug "[response-handler] content-type empty; full response:" resp))
    
    (body-parser url content-type body)

    (doseq [u target-urls]
      (trace "[response-handler] target URL [" u "]")
      (if (pred u)
        (http-get (:response-channel @mem) u))
      (mark-as-crawled mem u))))


(defn start-consumer
  [channel handler]
  (go (loop []
        (when-let [v (<! channel)]
          (trace "[consumer] processing body")
          (handler v)
          (recur)))))


(defn sitemap-handler
  "Given an HTTP response containing a sitemap.xml, prase it, and recur through all sub-sitemaps,
   building up the list of URLs in the memory"
  [mem {{url :url} :opts {content-type :content-type} :headers body :body :as resp}]
  (let [sitemap-locs (find-sitemaps body)
        page-locs (find-urls body)]
    (debug "[sitemap-handler] got" (count sitemap-locs) "sitemap locs and" (count page-locs) "page locs")

    (if (and body (sitemap-index? body))
      (doseq [l sitemap-locs]
        (debug "[sitemap-handler] fetching further sitemap" l)
        (http-get (:sitemap-channel @mem) (:loc l)))

      (doseq [l page-locs]
        (debug "[sitemap-handler] found leaf URL" url)
        (http-get (:response-channel @mem) (:loc l))
        (record-url-from-sitemap mem l)))))


(defn crawl
  "Start crawling at the given base URL"
  [^String base-url body-parser]

  (let [robots-txt (get-robots-txt base-url)
        robots-txt (if robots-txt robots-txt "")
        robots (irobot.core/robots robots-txt)
        sitemaps (irobot.core/sitemaps robots)
        sitemap-urls (map :loc sitemaps)
        
        mem (atom (create-memory))
        pred #(crawl? %1 robots mem base-url)
        
        sitemap-handler (partial sitemap-handler mem)
        response-handler (partial response-handler mem pred body-parser)]

    (info "[crawl] starting consumers")
    
    (start-consumer (:response-channel @mem) response-handler)
    (start-consumer (:sitemap-channel @mem) sitemap-handler)

    (info "[crawl] fetching" (count sitemap-urls) "top level sitemaps")
    (doseq [m sitemap-urls]
      (if m
        (http-get (:sitemap-channel @mem) m)))

    (info "[crawl] Starting crawl of" base-url)
    (if (pred base-url)
      (http-get (:response-channel @mem) base-url))
    
    mem))



(defn -main
  "Run the crawler from the command line"
  [& args]
  (crawl (first args) linkin.parsers/saving-body-parser)
  (loop [] (recur)))
