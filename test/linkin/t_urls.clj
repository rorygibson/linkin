(ns linkin.t-urls
  (:use midje.sweet)
  (:require [linkin.urls :refer :all]))


(facts "about determining whether a URL is local to a base-url"

  (fact "works with normal absolute URLs"
    (local? "http://foo/bar.html" "http://foo") => true)
  

  (fact "returns falsey if the URL is nil"
    (local? nil "http://foo") => falsey)


  (fact "returns falsey if the base-url is nil"
    (local? "http://foo/index.html" nil) => falsey))


 
(facts "about relativizing URLs"

  (fact "domain with empty path goes to /"
    (relativize-url "http://foo") => "/")


  (fact "domain with / path goes to /"
    (relativize-url "http://foo/") => "/")


  (fact "an already-relative path is passed through"
    (relativize-url "/foo.html") => "/foo.html")


  (fact "URLs with extensions work"
    (relativize-url "http://foo/index.html") => "/index.html")


  (fact "URLs with multiple path segments work"
    (relativize-url "http://foo/bar/index.html") => "/bar/index.html"))


(facts "about URL trimming"
  (fact "Removes a simple hash portion"
    (trim-hash-portion "http://foo.com/bar.html#xyz") => "http://foo.com/bar.html")

  (fact "Removes an empty hash portion"
    (trim-hash-portion "http://foo.com/bar.html#") => "http://foo.com/bar.html")

  (fact "Removes a complex hash portion"
    (trim-hash-portion "http://foo.com/bar.html#xyz?foo=bar&123") => "http://foo.com/bar.html")

  (fact "behaves with nil and empty strings"
    (trim-hash-portion "") => ""
    (trim-hash-portion nil) => ""))


(facts "about similar-urls"
  (fact "contains the original URL"
    (similar-urls "http://foo") => (contains "http://foo"))


  (fact "contains one suffixed with #"
    (similar-urls "http://foo") => (contains "http://foo#"))


  (fact "contains one suffixed with /"
    (similar-urls "http://foo") => (contains "http://foo/"))


  (fact "contains one suffixed with /#"
    (similar-urls "http://foo") => (contains "http://foo/#"))


  (fact "contains one suffixed with ?"
    (similar-urls "http://foo") => (contains "http://foo?"))

  (fact "contains one suffixed with /?"
    (similar-urls "http://foo") => (contains "http://foo/?")))


(facts "about checking the set of already-crawled URLs"
  (fact "if we have an empty or nil set of crawled URLs, we return falsey"
    (already-crawled? "http://foo" #{}) => falsey
    (already-crawled? "http://foo" nil) => falsey)


  (fact "if we have a set containing only the precise url, we return the URL"
    (already-crawled? "http://foo" #{"http://foo"}) => "http://foo")


  (fact "if we have a set of multiple URLs which contains the target url, we return the URL"
    (already-crawled? "http://foo" #{"http://foo" "http://bar"}) => "http://foo")


  (fact "if we have a set of multiple URLs which does not contain the target url, we return nil"
    (already-crawled? "http://baz" #{"http://foo" "http://bar"}) => nil)


  (fact "we return the URL if we have a URL in the set which is 'similar' to the target URL"
    (already-crawled? "http://foo" #{"http://foo#" "http://bar"}) => "http://foo#"
    (already-crawled? "http://foo" #{"http://foo/" "http://bar"}) => "http://foo/"
    (already-crawled? "http://foo#" #{"http://foo" "http://bar"}) => "http://foo"
    (already-crawled? "http://foo/" #{"http://foo" "http://bar"}) => "http://foo"
    (already-crawled? "http://foo?" #{"http://foo" "http://bar"}) => "http://foo"))
