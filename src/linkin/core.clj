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
   :urls-from-sitemaps #{}
   :base-url ""})
  ([_] (initial-state)))


(def application (atom (initial-state)))


(defn reset-application!
  "Return to starting state"
  []
  (swap! application initial-state))


(defn crawled-urls
  "List out URLs crawled so far (since last reset-application!)"
  []
  (:crawled-urls @application))


(defn urls-from-sitemaps
  "List out URLs retrieved from a sitemap crawl"
  []
  (:urls-from-sitemaps @application))


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


(defn get-robots-txt
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
  "If we should fetch a resource, asynchronously retrieve and pass it to response-handler"
  [robots body-consumer url]
  (if (crawl? url robots (crawled-urls) (base-url))
    (go (->> url
             http-get
             <!
             (response-handler robots body-consumer)))))


(defn response-handler
  "Taking in an HTTP response, extract anchors from the body, kick off fetches on those URLs, then consume the body (enqueue the body for further processing)"
  [robots body-consumer {{url :url} :opts {content-type :content-type} :headers body :body :as resp}]
  
  (let [urls (extract-anchors body content-type url)]

    (debug "[response-handler] got [" url "] of type [" content-type "] containing " (count urls) "URLs")
    
    (mark-as-crawled url)
    
    (doseq [u urls]
      (do-get robots body-consumer u))

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
  [^String url body-parser]
  (set-base-url url)
  
  (info "[crawl] Starting crawl of" (base-url))

  (let [robots-txt (get-robots-txt url)
        robots (irobot.core/robots (<!! robots-txt))
        body-consumer (make-body-consumer body-parser)]

    (do-get robots body-consumer url)
    "Crawl started"))
