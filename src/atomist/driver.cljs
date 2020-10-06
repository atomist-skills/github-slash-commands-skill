;; Copyright © 2020 Atomist, Inc.
;;
;; Licensed under the Apache License, Version 2.0 (the "License");
;; you may not use this file except in compliance with the License.
;; You may obtain a copy of the License at
;;
;;     http://www.apache.org/licenses/LICENSE-2.0
;;
;; Unless required by applicable law or agreed to in writing, software
;; distributed under the License is distributed on an "AS IS" BASIS,
;; WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
;; See the License for the specific language governing permissions and
;; limitations under the License.

(ns atomist.driver
  (:require [atomist.main :as main]
            [atomist.local-runner :as lr]))

(lr/set-env :prod-github-auth)
(defn on-push [s & {:keys [branch sha] :or {branch "master"}}]
  (-> (lr/fake-push "AEIB5886C" "slimslender" {:id "AEIB5886C_AEIB5886C_slimslender_132627478" :name "clj1"} branch)
      (lr/add-configuration {:name "default" :parameters []})
      (assoc-in [:data :Push 0 :after] {:message s
                                        :sha sha
                                        :author {:login "slimslenderslacks"}})
      (lr/call-event-handler main/handler)))

(defn on-comment [s & {:keys [number]}]
  (-> (lr/fake-comment-on-issue "AEIB5886C" "slimslender" "clj1" number s)
      (lr/add-configuration {:name "default" :parameters []})
      (lr/call-event-handler main/handler)))

(comment
 ;; wrong
  (on-push "something\n\n/pr --draft\n" :sha "71e4749ee79cfd900882c1ca62f0257ff1715f83")
 ;; right
  (on-push "something\n\n/pr --title \"my pr\" --base master --draft" :branch "slimtest1" :sha "71e4749ee79cfd900882c1ca62f0257ff1715f83")
 ;; mark as ready
  (on-push "something\n\n/pr ready\n" :branch "slimtest")
 ;; close
  (on-push "something\n\n/pr close\n" :branch "slimtest")
 ;; multiple steps
  (on-push "something\n/pr --title \"my multi step\" --base master --draft --label segment --label api\n/cc #segment-team" :branch "slimtest")

  (on-push "/issue create --title \"new issue\" --label test1 --assignee slimslenderslacks"))

(comment
  (on-push "/cc @slimslenderslacks" :sha "803c44e10439f6fb8a2e4ae7c4c9d9cf64646217"))

(comment
  (on-comment "okay\n\n/issue lock" :number 136)
  (on-comment "okay\n\n/issue lock spam" :number 136)
  (on-comment "okay\n\n/issue unlock" :number 136))
