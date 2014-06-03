(ns linkin.core
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


(defn make-body-consumer
  "Sets up a channel, and binds a body-parsing function to it.
Assumes that the body-parser takes a URL, content-type and body (all Strings).
Assumes that the channel will contain messages, each of which is a map of those 3 elements."
  [body-parser]
  (let [c (chan (buffer 1000))]
    (go (loop []
          (when-let [v (<! c)]
            (trace "[body-consumer] processing body")
            (let [body (:body v)
                  url (:url v)
                  content-type (:content-type v)]
              (body-parser url content-type body)
              (recur)))))
    c))


(defn do-get
  "Asynchronously fetch the contents of URL an pas it to handler."
  [handler url]
  (go
   (->> url
        http-get
        <!
        handler)))


(defn response-handler
  "Taking in an HTTP response, extract anchors from the body, kick off fetches on those URLs, then consume the body (enqueue the body for further processing)"
  [mem pred body-consumer {{url :url} :opts {content-type :content-type} :headers body :body :as resp}]
  
  (let [urls (extract-anchors body content-type url)
        handler (partial response-handler mem pred body-consumer)
        target-urls (filter pred urls)]
    (debug "[response-handler] got [" url "] of type [" content-type "] containing" (count urls) "URLs, of which we want to crawl" (count target-urls))
    
    (trace "[response-handler] enqueuing for body parsing")
    (>!! body-consumer {:url url :content-type content-type :body body})

    (doseq [u target-urls]
      (trace "[response-handler] target URL [" u "]")
      (if (pred u) (do-get handler u))
      (mark-as-crawled mem u))))


(defn sitemap-handler
  "Given an HTTP response containing a sitemap.xml, prase it, and recur through all sub-sitemaps,
   building up the list of URLs in the memory"
  [mem {{url :url} :opts {content-type :content-type} :headers body :body :as resp}]
  (let [sitemap-locs (find-sitemaps body)
        page-locs (find-urls body)]
    (debug "[sitemap-handler] got" (count sitemap-locs) "sitemap locs and" (count page-locs) "page locs")

    (if (and body (sitemap-index? body))
      (doseq [l sitemap-locs]
        (debug "[sitemap-handler] fetching" l)
        ;; TODO always?
        (do-get (partial sitemap-handler mem) (:loc l)))

      (doseq [l page-locs]
        (do-get (partial sitemap-handler mem) (:loc l))
        (record-url-from-sitemap mem l)))))


(defn crawl
  "Start crawling at the given base URL"
  [^String base-url body-parser]
  
  (info "[crawl] Starting crawl of" base-url)

  (let [robots-txt (get-robots-txt base-url)
        robots (irobot.core/robots (<!! robots-txt))
        sitemaps (irobot.core/sitemaps robots)
        body-consumer (make-body-consumer body-parser)
        memory (atom (create-memory))
        pred #(crawl? %1 robots memory base-url)
        response-handler (partial response-handler memory pred body-consumer)
        sitemap-handler (partial sitemap-handler mem)] ;; requires URL as param

    (doseq [m sitemaps]
      (do-get sitemap-handler (:loc m)))

    (if (pred base-url)
      (do-get response-handler base-url))
    
    memory))
