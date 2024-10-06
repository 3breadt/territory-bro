;; Copyright © 2015-2024 Esko Luontola
;; This software is released under the Apache License 2.0.
;; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.ui.markdown-test
  (:require [clojure.java.io :as io]
            [clojure.test :refer :all]
            [matcher-combinators.test :refer :all]
            [territory-bro.ui.markdown :as markdown])
  (:import (clojure.lang ExceptionInfo)))

(deftest render-test
  (testing "plain markdown"
    (is (= "<p>content</p>\n"
           (str (markdown/render "content")))))

  (testing "headings have anchor links"
    (is (= "<h1><a href=\"#some-title\" id=\"some-title\">Some Title</a></h1>\n"
           (str (markdown/render "# Some Title"))))))

(deftest render-resource-test
  (testing "renders a markdown resource"
    (is (= "<p>dummy page</p>\n"
           (str (markdown/render-resource (io/resource "public/dummy-page.md"))))))

  (testing "error 404 if resource not found"
    (is (thrown-match? ExceptionInfo
                       {:type :ring.util.http-response/response
                        :response {:status 404
                                   :body "Not found"
                                   :headers {}}}
                       (markdown/render-resource nil)))))
