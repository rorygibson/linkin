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




(defn initial-state
  "Define initial state var"
  ([]
     { :crawled-urls #{}
      :urls-from-sitemaps #{}})
  ([_] (initial-state)))




;; (defn urls-from-sitemaps
;;   "List out URLs retrieved from a sitemap crawl"
;;   []
;;   (:urls-from-sitemaps @application))



;; (defn record-url-from-sitemap
;;   [url]
;;   (swap! application (fn [app] (assoc app :urls-from-sitemaps (conj (:urls-from-sitemaps app) url)))))


(defn mark-as-crawled
  "Indicates that we have already crawled a specific URL"
  [memory url]
  (swap! memory (fn [mem] (assoc mem :crawled-urls (conj (:crawled-urls mem) url)))))


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


(declare response-handler)


(defn do-get
  "If we should fetch a resource, asynchronously retrieve and pass it to response-handler, and mark it as having been crawled"
  [mem robots body-consumer base-url url]
  (if (crawl? url robots (:crawled-urls @mem) base-url)    
    (do (mark-as-crawled mem url)    
        (go (->> url
                 http-get
                 <!
                 (response-handler mem robots body-consumer base-url))))))


(defn response-handler
  "Taking in an HTTP response, extract anchors from the body, kick off fetches on those URLs, then consume the body (enqueue the body for further processing)"
  [mem robots body-consumer base-url {{url :url} :opts {content-type :content-type} :headers body :body :as resp}]
  
  (let [urls (extract-anchors body content-type url)]

    (debug "[response-handler] got [" url "] of type [" content-type "] containing " (count urls) "URLs")
    
    (doseq [u urls]
      (do-get mem robots body-consumer base-url u))

    (consume body-consumer resp)))





;; (defn sitemap-handler
;;   "Request handler for responses to sitemap requests"
;;   [^String url {:keys [status headers body error opts] :as resp}]
;;   (mark-as-crawled url)

;;   (debug "[sitemap-handler] got" url)

;;   (if (and body (sitemap-index? body))
;;     (let [locs (find-sitemaps body)]
;;       (debug "[sitemap-handler] got" (count locs) "sitemap locs")

;;       (doall
;;        (map
;;         (fn [l]
;;           (debug "[sitemap-handler] processing" l)
;;           (send (link-agent)
;;                 (fn [_]
;;                   (debug "[sitemap-handler] agent called with sitemap loc" l)
;;                   (http/get (:loc l) (partial sitemap-handler (:loc l))))))
;;         locs)))

;;     (let [found (find-urls body)]
;;       (debug "[sitemap-handler] got" (count found) "URLs")      
;;       (doall (map record-url-from-sitemap found))      
;;       (debug "[sitemap-handler] total count is" (count (urls-from-sitemaps))))))


;; (defn crawl-sitemaps
;;   "Starting from a base sitemap.xml URL, locate and parse all sitemaps and sitemapindexes
;; to produce a list of target URLs"
;;   [sitemap-url]
;;   (go (->> sitemap-url          
;;            http-get
;;            <!
;;           (sitemap-handler sitemap-url))))


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


(defn crawl
  "Start crawling at the given base URL"
  [^String base-url body-parser]
  
  (info "[crawl] Starting crawl of" base-url)

  (let [robots-txt (get-robots-txt base-url)
        robots (irobot.core/robots (<!! robots-txt))
        body-consumer (make-body-consumer body-parser)
        memory (atom (initial-state))]

    (do-get memory robots body-consumer base-url base-url)
    memory))
