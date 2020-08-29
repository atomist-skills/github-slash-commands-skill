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
  (-> (lr/fake-comment-on-issue "AEIB5886C" "slimslender" "clj1" 2 "")
      (lr/add-configuration {:name "default" :parameters []})
      (lr/call-event-handler main/handler)))

(comment
 ;; wrong
  (on-push "something\n\n/pr --draft\n" :sha "803c44e10439f6fb8a2e4ae7c4c9d9cf64646217")
 ;; right
  (on-push "something\n\n/pr --title \"my pr\" --base master --draft" :branch "slimtest")
 ;; mark as ready
  (on-push "something\n\n/pr ready\n" :branch "slimtest")
 ;; close
  (on-push "something\n\n/pr close\n" :branch "slimtest")
 ;; multiple steps
  (on-push "something\n/pr --title \"my multi step\" --base master --draft --label segment --label api\n/cc #segment-team" :branch "slimtest")

  (on-push "/issue create --title \"new issue\" --label test1 --assignee slimslenderslacks"))

(comment
  (on-push "/cc @slimslenderslacks" :sha "803c44e10439f6fb8a2e4ae7c4c9d9cf64646217"))

(comment)


