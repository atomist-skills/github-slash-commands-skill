(ns atomist.main
  (:require [atomist.api :as api]
            [cljs.pprint :refer [pprint]]
            [cljs.core.async :refer [<!]]
            [goog.string :as gstring]
            [goog.string.format]
            [clojure.data]
            [atomist.cljs-log :as log]
            [atomist.github]
            [cljs.spec.alpha :as s])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn no-null-format [f & args]
  (try
    (apply gstring/format f args)
    (catch :default _
      f)))

(defn slack-message [{:keys [command args login repo] :as request}]
  (-> request
      (api/channel "git-chatops-skill")
      (api/simple-message (no-null-format "```run %s %s \n    on %s/%s from %s```"
                                          command
                                          args
                                          (:owner repo)
                                          (:name repo)
                                          login))))

(defmulti run :command/command)
(defmethod run "pr" [{:command/keys [args token repo message]
                      :pr/keys [branch]}]
  (go (<! (atomist.github/post-pr
           {:token token :owner (:owner repo) :repo (:name repo)}
           "title" "body" branch "base"))))
(defmethod run "label" [{:command/keys [args token repo message]
                         :label/keys [number]}]
  (go {:return "return"}))

(defn run-command [handler]
  (fn [request]
    (go
      (assoc request :return (<! (run (:command request)))))))

(defn validate-command-spec [d]
  (if (not (s/valid? :command/spec d))
    (str (s/explain :command/spec d))))

(defn validate-command [handler]
  (fn [request]
    (go
      (if-let [failure (validate-command-spec (:command request))]
        (<! (api/finish request :failure failure))
        (<! (handler request))))))

(defn add-command [handler]
  (fn [{:keys [command args token repo number branch message] :as request}]
    (go
      (<! (slack-message request))
      (<! (handler (assoc request
                          :command (merge
                                    {:command/command command
                                     :command/args args
                                     :command/token token
                                     :command/repo repo
                                     :command/message message}
                                    (if (= "label" command)
                                      {:label/number number})
                                    (if (= "pr" command)
                                      {:pr/branch branch}))))))))

(defn atomist-command [keyword s]
  (re-find (re-pattern (gstring/format "(?m)%s (\w+)(.*)?" keyword)) s))

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
       (api/add-skill-config :keyword)
       (api/extract-github-token)
       (api/add-slack-source-to-event)
       (api/log-event)
       (api/status :send-status (fn [request] (if-let [data-keys (-> request :data keys)]
                                                (gstring/format "processed %s" data-keys)
                                                "check this"))))))

