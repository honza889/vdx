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

(ns vdx-test.util
  (:require [clojure.test :refer :all]
            [clojure.java.io :as io]
            [clojure.string :as str])
  (:import org.projectodd.vdx.core.Util))

(deftest extracting-xmlns-should-work
  (is (= #{"urn:jboss:domain:4.2" "urn:jboss:domain:batch-jberet:1.0" "urn:jboss:domain:bean-validation:1.0"
           "urn:jboss:domain:datasources:4.0" "urn:jboss:domain:deployment-scanner:2.0" "urn:jboss:domain:ee:4.0"
           "urn:jboss:domain:ejb3:4.0" "urn:jboss:domain:infinispan:4.0" "urn:jboss:domain:io:1.1"
           "urn:jboss:domain:jaxrs:1.0" "urn:jboss:domain:jca:4.0" "urn:jboss:domain:jdr:1.0" "urn:jboss:domain:jmx:1.3"
           "urn:jboss:domain:jpa:1.1" "urn:jboss:domain:jsf:1.0" "urn:jboss:domain:logging:3.0" "urn:jboss:domain:mail:2.0"
           "urn:jboss:domain:naming:2.0" "urn:jboss:domain:pojo:1.0" "urn:jboss:domain:remoting:3.0"
           "urn:jboss:domain:request-controller:1.0" "urn:jboss:domain:resource-adapters:4.0" "urn:jboss:domain:sar:1.0"
           "urn:jboss:domain:security-manager:1.0" "urn:jboss:domain:security:1.2" "urn:jboss:domain:transactions:3.0"
           "urn:jboss:domain:undertow:3.1" "urn:jboss:domain:webservices:2.0" "urn:jboss:domain:weld:3.0"}
         (Util/extractXMLNS (-> "standalone.xml" io/resource slurp (str/split #"\n"))))))

(deftest provides-xmlns-should-work
  (is (Util/providesXMLNS #{"urn:jboss:domain:4.2"} (io/resource "wildfly-config_4_2.xsd")))
  (is (not (Util/providesXMLNS #{"urn:jboss:domain:datasources:4.0"} (io/resource "wildfly-config_4_2.xsd")))))

