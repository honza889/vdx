;; Copyright 2016 Red Hat, Inc, and individual contributors.
;;
;; Licensed under the Apache License, Version 2.0 (the "License");
;; you may not use this file except in compliance with the License.
;; You may obtain a copy of the License at
;;
;; http://www.apache.org/licenses/LICENSE-2.0
;;
;; Unless required by applicable law or agreed to in writing, software
;; distributed under the License is distributed on an "AS IS" BASIS,
;; WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
;; See the License for the specific language governing permissions and
;; limitations under the License.

(ns vdx-test.handlers
  (:require [clojure.test :refer :all]
            [clojure.java.io :as io])
  (:import (org.projectodd.vdx.core ValidationContext ValidationError ErrorType I18N$Key)
           (org.projectodd.vdx.core.schema SchemaElement)
           (javax.xml.stream Location)
           (javax.xml.namespace QName)
           (java.util List)))

(defn location [line col]
  (reify Location
    (getLineNumber [_] line)
    (getColumnNumber [_] col)))

(defn coerce-value [v]
  (cond
    (instance? SchemaElement v) (.name v)
    (instance? List v) (map coerce-value v)
    :default v))

(defmacro assert-message [msg template & values]
  `(do
     (is (= ~template (.template ~msg)))
     (is (= (or [~@values] []) (map coerce-value (.rawValues ~msg))))))

(deftest test-DuplicateElementHandler
  (let [ctx (ValidationContext. (io/resource "handler-test.xml")
              [(io/resource "schemas/handler-test.xsd")])]
    (testing "with an attribute"
      (let [res (.handle ctx
                  (-> (ValidationError. ErrorType/DUPLICATE_ELEMENT
                        ""
                        (location 7 4))
                    (.element (QName. "urn:vdx:test" "bar"))
                    (.attribute (QName. "attr1"))
                    (.attributeValue "a")))]
        (assert-message (first (.primaryMessages res))
          I18N$Key/ELEMENT_WITH_ATTRIBUTE_DUPLICATED "bar" "attr1" "a")
        (assert-message (first (.secondaryMessages res))
          I18N$Key/ELEMENT_WITH_ATTRIBUTE_DUPLICATED_FIRST_OCCURRENCE "bar" "attr1")
        (is (empty? (.primaryMessages (first (.secondaryResults res)))))))
    
    (testing "without an attribute"
      (let [res (.handle ctx
                  (-> (ValidationError. ErrorType/DUPLICATE_ELEMENT
                        ""
                        (location 7 4))
                    (.element (QName. "urn:vdx:test" "bar"))))]
        (assert-message (first (.primaryMessages res))
          I18N$Key/ELEMENT_DUPLICATED "bar" "foo")
        (assert-message (first (.secondaryMessages res))
          I18N$Key/ELEMENT_DUPLICATED_FIRST_OCCURRENCE "bar")
        (is (empty? (.primaryMessages (first (.secondaryResults res)))))))))

(deftest test-UnexpectedAttributeHandler
  (let [ctx (ValidationContext. (io/resource "handler-test.xml")
              [(io/resource "schemas/handler-test.xsd")])]
    (testing "unmatchable attribute with no alternates"
      (let [res (.handle ctx
                  (-> (ValidationError. ErrorType/UNEXPECTED_ATTRIBUTE
                        ""
                        (location 6 4))
                    (.element (QName. "urn:vdx:test" "ham"))
                    (.attribute (QName. "biscuit"))))]
        (is (= 6 (.line res)))
        (is (= 8 (.column res)))
        (assert-message (first (.primaryMessages res))
          I18N$Key/ATTRIBUTE_NOT_ALLOWED
          "biscuit" "ham")
        (assert-message (second (.primaryMessages res))
          I18N$Key/ELEMENT_HAS_NO_ATTRIBUTES
          "ham")))

    (testing "unmatchable attribute with schema alternatives"
      (let [res (.handle ctx
                  (-> (ValidationError. ErrorType/UNEXPECTED_ATTRIBUTE
                        ""
                        (location 4 4))
                    (.element (QName. "urn:vdx:test" "bar"))
                    (.attribute (QName. "blahblahblah"))))]
        (assert-message (second (.primaryMessages res))
          I18N$Key/ATTRIBUTES_ALLOWED_HERE
          ["attr1" "some-attr"])))

    (testing "unmatchable attribute with provided alternatives"
      (let [res (.handle ctx
                  (-> (ValidationError. ErrorType/UNEXPECTED_ATTRIBUTE
                        ""
                        (location 4 4))
                    (.element (QName. "urn:vdx:test" "bar"))
                    (.attribute (QName. "blahblahblah"))
                    (.alternatives #{"abc"})))]
        (assert-message (second (.primaryMessages res))
          I18N$Key/ATTRIBUTES_ALLOWED_HERE
          ["abc"])))

    (testing "misspelled attribute with schema alternatives"
      (let [res (.handle ctx
                  (-> (ValidationError. ErrorType/UNEXPECTED_ATTRIBUTE
                        ""
                        (location 4 4))
                    (.element (QName. "urn:vdx:test" "bar"))
                    (.attribute (QName. "attr2"))))]
        (is (= 18 (.column res)))
        (assert-message (second (.primaryMessages res))
          I18N$Key/DID_YOU_MEAN
          "attr1")))

    (testing "misspelled attribute with provided alternatives"
      (let [res (.handle ctx
                  (-> (ValidationError. ErrorType/UNEXPECTED_ATTRIBUTE
                        ""
                        (location 4 4))
                    (.element (QName. "urn:vdx:test" "bar"))
                    (.attribute (QName. "attr2"))
                    (.alternatives #{"attrx"})))]
        (is (= 18 (.column res)))
        (assert-message (second (.primaryMessages res))
          I18N$Key/DID_YOU_MEAN "attrx")))

    (testing "matchable attribute"
      (let [res (.handle ctx
                  (-> (ValidationError. ErrorType/UNEXPECTED_ATTRIBUTE
                        ""
                        (location 4 4))
                    (.element (QName. "urn:vdx:test" "bar"))
                    (.attribute (QName. "attr3"))))]
        (is (= 28 (.column res)))
        (assert-message (first (.secondaryMessages res))
          I18N$Key/ATTRIBUTE_IS_ALLOWED_ON
          "attr3" [["foo"]])))))

(deftest test-UnexpectedElementHandler
  (let [ctx (ValidationContext. (io/resource "handler-test.xml")
              [(io/resource "schemas/handler-test.xsd")])]
    (testing "it's really a duplicate"
      (let [res (.handle ctx
                  (-> (ValidationError. ErrorType/UNEXPECTED_ELEMENT
                        ""
                        (location 7 4))
                    (.element (QName. "urn:vdx:test" "bar"))))]
        (assert-message (first (.primaryMessages res))
          I18N$Key/ELEMENT_DUPLICATED "bar" "foo")
        (assert-message (first (.secondaryMessages res))
          I18N$Key/ELEMENT_DUPLICATED_FIRST_OCCURRENCE "bar")
        (is (empty? (.primaryMessages (first (.secondaryResults res)))))))
    
    (testing "unmatchable element with no alternates"
      (let [res (.handle ctx
                  (-> (ValidationError. ErrorType/UNEXPECTED_ELEMENT
                        ""
                        (location 6 4))
                    (.element (QName. "urn:vdx:test" "ham"))))]
        (is (= 6 (.line res)))
        (is (= 4 (.column res)))
        (assert-message (first (.primaryMessages res))
          I18N$Key/ELEMENT_NOT_ALLOWED
          "ham")
        (is (empty? (.secondaryMessages res)))))

    (testing "unmatchable element with provided alternatives"
      (let [res (.handle ctx
                  (-> (ValidationError. ErrorType/UNEXPECTED_ELEMENT
                        ""
                        (location 6 4))
                    (.element (QName. "urn:vdx:test" "ham"))
                    (.alternatives #{"abcdefg"})))]
        (assert-message (second (.primaryMessages res))
          I18N$Key/ELEMENTS_ALLOWED_HERE
          ["abcdefg"])))

    (testing "misspelled element with provided alternatives"
      (let [res (.handle ctx
                  (-> (ValidationError. ErrorType/UNEXPECTED_ELEMENT
                        ""
                        (location 6 4))
                    (.element (QName. "urn:vdx:test" "ham"))
                    (.alternatives #{"ahm"})))]
        (assert-message (second (.primaryMessages res))
          I18N$Key/DID_YOU_MEAN
          "ahm")
        (assert-message (nth (.primaryMessages res) 2)
          I18N$Key/ELEMENTS_ALLOWED_HERE
          ["ahm"])
        ))

    (testing "misspelled element without provided alternatives"
      (let [res (.handle ctx
                  (-> (ValidationError. ErrorType/UNEXPECTED_ELEMENT
                        ""
                        (location 6 4))
                    (.element (QName. "urn:vdx:test" "ham"))))]
        (assert-message (second (.primaryMessages res))
          I18N$Key/DID_YOU_MEAN
          "bar")
        (assert-message (nth (.primaryMessages res) 2)
          I18N$Key/ELEMENTS_ALLOWED_HERE
          ["bar" "biscuit"])
        ))

    (testing "matchable element"
      (let [res (.handle ctx
                  (-> (ValidationError. ErrorType/UNEXPECTED_ELEMENT
                        ""
                        (location 7 4))
                    (.element (QName. "urn:vdx:test" "sandwich"))))]
        (assert-message (first (.secondaryMessages res))
          I18N$Key/ELEMENT_IS_ALLOWED_ON
          "sandwich" [["foo" "bar" "sandwiches"] ["omelet" "sandwiches"]])))))

(deftest test-UnknownErrorHandler
  (let [ctx (ValidationContext. (io/resource "handler-test.xml")
              [(io/resource "schemas/handler-test.xsd")])]
    (testing "with fallback"
      (let [res (.handle ctx
                  (-> (ValidationError. ErrorType/UNKNOWN_ERROR
                        "foo"
                        (location 1 1))
                    (.fallbackMessage "bar")))]
        (assert-message (first (.primaryMessages res))
          I18N$Key/PASSTHRU "bar")))
    
    (testing "with parse error"
      (let [res (.handle ctx
                  (ValidationError. ErrorType/UNKNOWN_ERROR
                    "Unexpected close tag </aauthentication>; expected </authentication>.\n at [row,col {unknown-source}]: [38,33]"
                    (location 1 1)))]
        (assert-message (first (.primaryMessages res))
          I18N$Key/PASSTHRU "Unexpected close tag </aauthentication>; expected </authentication>")))))

(deftest test-UnsupportedElementHandler
  (let [ctx (ValidationContext. (io/resource "handler-test.xml")
              [(io/resource "schemas/handler-test.xsd")])]
    (testing "with alternatives"
      (let [res (.handle ctx
                  (-> (ValidationError. ErrorType/UNSUPPORTED_ELEMENT
                        ""
                        (location 7 4))
                    (.element (QName. "urn:vdx:test" "bar"))
                    (.alternatives #{"barr"})))]
        (assert-message (first (.primaryMessages res))
          I18N$Key/ELEMENT_UNSUPPORTED "bar" "barr"))

      (testing "without alternatives"
      (let [res (.handle ctx
                  (-> (ValidationError. ErrorType/UNSUPPORTED_ELEMENT
                        ""
                        (location 7 4))
                    (.element (QName. "urn:vdx:test" "bar"))))]
        (assert-message (first (.primaryMessages res))
          I18N$Key/ELEMENT_UNSUPPORTED_NO_ALT "bar"))))
    
    (testing "without an attribute"
      (let [res (.handle ctx
                  (-> (ValidationError. ErrorType/DUPLICATE_ELEMENT
                        ""
                        (location 7 4))
                    (.element (QName. "urn:vdx:test" "bar"))))]
        (assert-message (first (.primaryMessages res))
          I18N$Key/ELEMENT_DUPLICATED "bar" "foo")
        (assert-message (first (.secondaryMessages res))
          I18N$Key/ELEMENT_DUPLICATED_FIRST_OCCURRENCE "bar")
        (is (empty? (.primaryMessages (first (.secondaryResults res)))))))))
