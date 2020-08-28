(ns atomist.driver
  (:require [atomist.main :as main]
            [atomist.local-runner :as lr]))

(lr/set-env :prod-github-auth)
(-> (lr/fake-push "AEIB5886C" "slimslender" {:id "AEIB5886C_AEIB5886C_slimslender_132627478" :name "clj1"} "slimtest")
    (lr/add-configuration {:name "default" :parameters []})
    (assoc-in [:data :Push 0 :after] {:message "something\n\n/pr --title \"slimtest\" --base master --draft\n"
                                      :author {:login "slimslenderslacks"}})
    (lr/call-event-handler main/handler))

(-> (lr/fake-push "AEIB5886C" "slimslender" {:id "AEIB5886C_AEIB5886C_slimslender_132627478" :name "clj1"} "slimtest")
    (lr/add-configuration {:name "default" :parameters []})
    (assoc-in [:data :Push 0 :after] {:message "something\n\n/pr ready\n"
                                      :author {:login "slimslenderslacks"}})
    (lr/call-event-handler main/handler))

(-> (lr/fake-push "AEIB5886C" "slimslender" {:id "AEIB5886C_AEIB5886C_slimslender_132627478" :name "clj1"} "slimtest")
    (lr/add-configuration {:name "default" :parameters []})
    (assoc-in [:data :Push 0 :after] {:message "something\n\n/pr close\n"
                                      :author {:login "slimslenderslacks"}})
    (lr/call-event-handler main/handler))