(ns linkin.t-html
  (:use midje.sweet)
  (:require [linkin.html :refer :all]))


(def single-absolute-anchor "<html><body><a href=\"http://domain/foo.html\">foo</a></body></html>")
(def single-relative-anchor "<html><body><a href=\"/foo.html\">foo</a></body></html>")

(def multiple-relative-anchors
  "<html><body><a href=\"/foo.html\">foo</a><a href=\"/bar.html\">bar</a></body></html>")

(def multiple-absolute-anchors
  "<html><body><a href=\"http://domain/foo.html\">foo</a><a href=\"http://domain/bar.html\">bar</a></body></html>")


(fact "can extract a single absolute anchor from a simple HTML doc"
  (extract-anchors single-absolute-anchor TEXT-HTML "http://domain") 
  => '("http://domain/foo.html"))


(fact "if the HTML is not HTML, is nil or is empty, returns an empty seq"
  (extract-anchors nil TEXT-HTML "http://domain") => '()
  (extract-anchors "" TEXT-HTML "http://domain") => '()
  (extract-anchors "error" TEXT-HTML "http://domain") => '())


(fact "if there are no anchors, returns an empty seq"
  (extract-anchors "<html><body>blah</body></html>" TEXT-HTML "http://domain") => '())

 
(fact "can return multiple absolute anchors"
  (extract-anchors multiple-absolute-anchors TEXT-HTML "http://domain")
  => '("http://domain/foo.html" "http://domain/bar.html"))


(fact "can return a single relative anchor, converted to an absolute URL"
  (extract-anchors single-relative-anchor TEXT-HTML "http://domain")
  => '("http://domain/foo.html"))


(fact "can return multiple relative anchors, converted to absolute URLs"
  (extract-anchors multiple-relative-anchors TEXT-HTML "http://domain")
  => '("http://domain/foo.html" "http://domain/bar.html"))
