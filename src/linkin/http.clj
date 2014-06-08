(ns linkin.http
  (:require [clojure.core.async :refer [go put! chan <!! <! >! dropping-buffer]]
            [linkin.urls :as urls]
            [irobot.core]
            [org.httpkit.client :as http]
            [clojure.tools.logging :refer [debug info error warn trace]]))


(def USER_AGENT "linkin 0.1.0")

(defn http-get
  "Use http-kit to fetch a resource asynchronously, and stuff the result into a channel."
  [channel url]
  (debug "[http-get]" url channel)
  (http/get url (fn [r] (put! channel r))))


(defn crawl?
  "We should only crawl a URL if it's local and we haven't already crawled it (or a similar URL)"
  [^String url robots memory ^String base-url]
  (let [already-crawled (:crawled-urls @memory)
        done (urls/already-crawled? url already-crawled)
        local (urls/local? url base-url)]
    
    (if (and (not done) local)
      (irobot.core/allows? robots USER_AGENT (urls/relativize-url url))
      false)))

