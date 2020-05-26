(ns atomist.main
  (:require [atomist.api :as api]
            [cljs.pprint :refer [pprint]]
            [cljs.core.async :refer [<!] :as async]
            [goog.string :as gstring]
            [goog.string.format]
            [clojure.data]
            [atomist.cljs-log :as log]
            [atomist.github]
            [cljs.spec.alpha :as s]
            [atomist.local-runner]
            [atomist.commands :as commands]
            [atomist.commands.cc]
            [atomist.commands.label]
            [atomist.commands.pr]
            [atomist.slack-debug :as slack])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn run-commands [handler]
  (fn [request]
    (go
      (log/info "run commands " (:commands request))
      (let [return-values (<! (->> (for [command (:commands request)]
                                     (commands/run (assoc request :command command)))
                                   (async/merge)
                                   (async/reduce conj [])))]
        (log/info "return values " return-values)
        (<! (handler (assoc request :return return-values)))))))

(defn validate-command-spec [d]
  (if (not (s/valid? :command/spec d))
    (with-out-str (s/explain :command/spec d))))

(defn validate-commands [handler]
  (fn [request]
    (go
      (let [failures (->> (:commands request)
                          (map validate-command-spec)
                          (filter identity))]
        (if (not (empty? failures))
          (<! (api/finish request :failure (str failures)))
          (<! (handler request)))))))

(defn add-commands [handler]
  (fn [{:keys [intents] :as request}]
    (go
      (<! (slack/slack-message request))
      (<! (handler (assoc request
                          :commands (for [intent intents
                                          :let [{:keys [command args repo number branch message default-color sha login]} intent]]
                                      (merge
                                       {:command/command command
                                        :command/args args
                                        :command/token (:token request)
                                        :command/repo repo
                                        :command/message message
                                        :command/login login
                                        :label/default-color (or default-color "f29513")}
                                       (if number
                                         {:label/number number})
                                       (if branch
                                         {:push/branch branch})
                                       (if sha
                                         {:push/sha sha})))))))))

(defn atomist-commands [keyword s]
  (re-seq (re-pattern (gstring/format "(?m)/%s (\\w+)(.*)?" (or keyword "atomist"))) s))

(defn push-mode [{:keys [keyword]} {[{:keys [branch repo] {:keys [message sha] {:keys [login]} :author} :after}] :Push}]
  (->> (for [[_ command args] (atomist-commands keyword message)]
         (if command
           {:command command :args args :repo repo :branch branch :login login :message message :sha sha}))
       (filter identity)))

(defn comment-mode [{:keys [keyword]} {[{:keys [body issue pullRequest] {:keys [login]} :by}] :Comment}]
  (->> (for [[_ command args] (atomist-commands keyword body)]
         (if command
           (merge (or issue pullRequest) {:login login :command command :args args :message body})))
       (filter identity)))

(defn check-push-or-comment-for-intents [handler]
  (fn [request]
    (go
      (let [data (-> request :data)
            intents (cond
                      (contains? data :Push) (push-mode request data)
                      (contains? data :Comment) (comment-mode request data)
                      :else :none)]
        (if (not (empty? intents))
          (<! (handler (assoc request :intents intents)))
          (<! (api/finish request :success "skipping - no intents" :visibility :hidden)))))))

(defn ^:export handler
  [data sendreponse]
  (api/make-request
   data
   sendreponse
   (-> (api/finished)
       (run-commands)
       (validate-commands)
       (add-commands)
       (check-push-or-comment-for-intents)
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
      (assoc-in [:data :Push 0 :after :message] "some stuff \n/atomist pr --title thing")
      (assoc-in [:data :Push 0 :after :author :login] "slimslenderslacks")
      (assoc :configuration {:name "whatever"
                             :parameters [{:name "keyword"
                                           :value "atomist"}]})
      (atomist.local-runner/call-event-handler atomist.main/handler))

  (-> (atomist.local-runner/fake-comment-on-issue "T29E48P34" "atomist-skills" "git-chatops-skill" 15
                                                  "/atomist label hey20\n/atomist cc @jim-atomist")
      (assoc :configuration {:name "whatever"
                             :parameters [{:name "keyword"
                                           :value "atomist"}]})
      (atomist.local-runner/call-event-handler atomist.main/handler))

  (-> (atomist.local-runner/fake-comment-on-issue "T29E48P34" "atomist-skills" "git-chatops-skill" 14 "/atomist cc @jim-atomist")
      (assoc :configuration {:name "whatever"
                             :parameters [{:name "keyword"
                                           :value "atomist"}]})
      (atomist.local-runner/call-event-handler atomist.main/handler))

  (-> (atomist.local-runner/fake-comment-on-issue "T29E48P34" "atomist-skills" "git-chatops-skill" 15 "/atomist cc #git-chatops-skill")
      (assoc :configuration {:name "whatever"
                             :parameters [{:name "keyword"
                                           :value "atomist"}]})
      (atomist.local-runner/call-event-handler atomist.main/handler)))

