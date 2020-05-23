(ns atomist.main
  (:require [atomist.api :as api]
            [cljs.pprint :refer [pprint]]
            [cljs.core.async :refer [<!]]
            [goog.string :as gstring]
            [goog.string.format]
            [clojure.data]
            [atomist.cljs-log :as log]
            [atomist.github]
            [cljs.spec.alpha :as s]
            [atomist.local-runner]
            [atomist.commands :as commands]
            [atomist.slack-debug :as slack])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn run-command [handler]
  (fn [request]
    (go
      (<! (handler (assoc request :return (<! (commands/run (:command request)))))))))

(defn validate-command-spec [d]
  (if (not (s/valid? :command/spec d))
    (with-out-str (s/explain :command/spec d))))

(defn validate-command [handler]
  (fn [request]
    (go
      (if-let [failure (validate-command-spec (:command request))]
        (<! (api/finish request :failure failure))
        (<! (handler request))))))

(defn add-command [handler]
  (fn [{:keys [command args token repo number branch message default-color] :as request}]
    (go
      (<! (slack/slack-message request))
      (<! (handler (assoc request
                          :command (merge
                                    {:command/command command
                                     :command/args args
                                     :command/token token
                                     :command/repo repo
                                     :command/message message}
                                    (if (= "label" command)
                                      {:label/number number
                                       :label/default-color (or default-color "f29513")})
                                    (if (= "pr" command)
                                      {:pr/branch branch}))))))))

(defn atomist-command [keyword s]
  (re-find (re-pattern (gstring/format "(?m)/%s (\\w+)(.*)?" (or keyword "atomist"))) s))

(defn push-mode [{:keys [keyword]} {[{:keys [branch repo] {:keys [message sha] {:keys [login]} :author} :after}] :Push}]
  (let [[_ command args] (atomist-command keyword message)]
    (if command
      {:command command :args args :repo repo :branch branch :login login :message message}
      :none)))

(defn comment-mode [{:keys [keyword]} {[{:keys [body issue pullRequest] {:keys [login]} :by}] :Comment}]
  (let [[_ command args] (atomist-command keyword body)]
    (if (and command (or issue pullRequest))
      (merge (or issue pullRequest) {:login login :command command :args args :message body})
      :none)))

(defn check-push-or-comment [handler]
  (fn [request]
    (go
      (let [data (-> request :data)
            mode (cond
                   (contains? data :Push) (push-mode request data)
                   (contains? data :Comment) (comment-mode request data)
                   :else :none)]
        (if (not (= mode :none))
          (<! (handler (merge request mode)))
          (<! (api/finish request :success "skipping" :visibility :hidden)))))))

(defn ^:export handler
  [data sendreponse]
  (api/make-request
   data
   sendreponse
   (-> (api/finished)
       (run-command)
       (validate-command)
       (add-command)
       (check-push-or-comment)
       (api/add-skill-config :keyword :default-color)
       (api/extract-github-token)
       (api/create-ref-from-event)
       (api/add-slack-source-to-event)
       (api/log-event)
       (api/status :send-status (fn [{{:keys [errors status]} :status :as request}]
                                  (cond
                                    (not (empty? errors))
                                    (apply str errors)
                                    (not (nil? status))
                                    (gstring/format "command status %s" status)
                                    :else
                                    (if-let [data-keys (-> request :data keys)]
                                      (gstring/format "processed %s" data-keys)
                                      "check this")))))))

(comment
  (enable-console-print!)
  (atomist.local-runner/set-env :prod-github-auth)
  (-> (atomist.local-runner/fake-push "T29E48P34" "atomist-skills" "git-chatops-skill" "branch1")
      (assoc-in [:data :Push 0 :after :message] "some stuff \natomist pr --title thing")
      (assoc :configuration {:name "whatever"
                             :parameters [{:name "keyword"
                                           :value "atomist"}]})
      (atomist.local-runner/call-event-handler atomist.main/handler))
  (-> (atomist.local-runner/fake-comment-on-issue "T29E48P34" "atomist-skills" "git-chatops-skill" 9 "/atomist label hey2")
      (assoc :configuration {:name "whatever"
                             :parameters [{:name "keyword"
                                           :value "atomist"}]})
      (atomist.local-runner/call-event-handler atomist.main/handler)))

