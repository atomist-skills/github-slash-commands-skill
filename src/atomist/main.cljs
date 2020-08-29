(ns atomist.main
  (:require [atomist.api :as api]
            [cljs.core.async :refer [<!] :as async]
            [goog.string :as gstring]
            [goog.string.format]
            [clojure.data]
            [atomist.github]
            [cljs.spec.alpha :as s]
            [atomist.local-runner]
            [atomist.commands :as commands]
            [atomist.commands.cc]
            [atomist.commands.label]
            [atomist.commands.pr]
            [atomist.commands.issue]
            [atomist.cljs-log :as log])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn run-commands [handler]
  (fn [request]
    (go
      (let [return-values (<! (->> (for [command (:commands request)]
                                     (commands/run (assoc request :command command)))
                                   (async/merge)
                                   (async/reduce conj [])))]
        (log/info "return-values " return-values)
        (<! (handler (assoc request :status {:command-count (count return-values)
                                             :errors (->> (mapcat :errors return-values)
                                                          (filter identity)
                                                          (into []))
                                             :statuses (->> (map :status return-values)
                                                            (filter identity)
                                                            (into []))})))))))

(defn validate-command-spec [d]
  (when (not (s/valid? :command/spec d))
    (with-out-str (s/explain :command/spec d))))

(defn validate-commands [handler]
  (fn [request]
    (go
      (let [failures (->> (:commands request)
                          (map validate-command-spec)
                          (filter identity))]
        (if (seq failures)
          (<! (api/finish request :failure (str failures)))
          (<! (handler request)))))))

(defn add-commands [handler]
  (fn [{:keys [intents] :as request}]
    (go
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
                                       (when number
                                         {:label/number number})
                                       (when branch
                                         {:push/branch branch})
                                       (when sha
                                         {:push/sha sha})))))))))

(defn atomist-commands [s]
  (re-seq (re-pattern (gstring/format "(?m)/(%s) (.*)?"
                                      (->> (interpose "|" commands/command-prefixes)
                                           (apply str))
                                      (or keyword "atomist"))) s))

(defn push-mode [_ {[{:keys [branch repo] {:keys [message sha] {:keys [login]} :author} :after}] :Push}]
  (->> (for [[_ command args] (atomist-commands message)]
         (when command
           {:command command :args args :repo repo :branch branch :login login :message message :sha sha}))
       (filter identity)))

(defn comment-mode [_ {[{:keys [body issue pullRequest] {:keys [login]} :by}] :Comment}]
  (->> (for [[_ command args] (atomist-commands body)]
         (when command
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
        (if (seq intents)
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
       (api/add-skill-config)
       (api/extract-github-token)
       (api/create-ref-from-event)
       (api/add-slack-source-to-event)
       (api/log-event)
       (api/status :send-status (fn [{{:keys [command-count errors statuses]} :status commands :commands}]
                                  (log/infof "statuses are %s" statuses)
                                  (log/infof "errors are %s" errors)
                                  (log/infof "ran %d commands" command-count)
                                  (cond (seq errors)
                                        (->> (interpose "," errors) (apply str))
                                        (seq statuses)
                                        (gstring/format "command statuses %s" (->> (interpose "," statuses) (apply str)))
                                        :else
                                        (gstring/format "ran %s" (->> commands (map :command/command) (into [])))))))))
