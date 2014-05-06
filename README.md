# linkin

A minimal Clojure web crawling library built atop
[http-kit](http://http-kit.org) for asynchronicity & concurrency, and
[Jsoup](http://jsoup.org) for HTML parsing.

("linkin" from Linkin Park's
["Crawling"](http://www.azlyrics.com/lyrics/linkinpark/crawling.html),
and because links)



## Usage
Right now it's not on [clojars](http://clojars.org) yet, so you'll
need to build and install it locally using a method of your own choosing
([lein-localrepo](https://github.com/kumarshantanu/lein-localrepo)
is a good choice)

Then include the following dependency in your project.clj:
```clojure
[linkin "0.1.0-SNAPSHOT"]
```

Then:
```clojure
(require 'linkin.core)
(defn my-handler [url body] (println "URL [" url "Body [" body "]"))
(linkin.core/crawl "http://www.example.com" #(prn %1 %2))
```

## License

Copyright © 2014 Rory Gibson

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
