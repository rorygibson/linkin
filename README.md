# linkin

A minimal, async Clojure web crawling library.

("linkin" from Linkin Park's
["Crawling"](http://www.azlyrics.com/lyrics/linkinpark/crawling.html),
and because links)

## Features
+ Uses http-kit for async fetching
+ Uses Jsoup to reliably extract links from scraped pages
+ Allows the user to pass in their own function to handle the body content of scraped pages
+ Respects the robots.txt of the target website (allow & disallow rules)




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
(linkin.core/crawl "http://example.com" linkin.core/simple-body-parser)
```

## Todo
+ Throttling (ie don't DoS target sites...)
+ Control throttling using the spider delay directives in robots.txt
+ Control of max depth / number of pages crawled
+ Ability to spider across domains
+ Pass options through to http-kit (eg following redirects)
+ Filtering by content type
+ Stats while running
+ Namespace state by run (currently shares state so can't run multiple instances alongside)
+ Look at using a crawl set / queue instead of the link-agent - to prevent duplication of crawled pages
+ Better URL normalization (for detecting URLs we've seen before) - see http://en.wikipedia.org/wiki/URL_normalization


## License

Copyright © 2014 Rory Gibson

Distributed under the [Eclipse Public License version 1.0.](http://www.eclipse.org/legal/epl-v10.html)
