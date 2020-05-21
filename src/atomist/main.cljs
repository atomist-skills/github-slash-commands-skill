(ns atomist.main
  (:require [atomist.api :as api]
            [cljs.pprint :refer [pprint]]
            [cljs.core.async :refer [<!]]
            [goog.string :as gstring]
            [goog.string.format]
            [clojure.data]
            [atomist.cljs-log :as log]
            [atomist.github])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn no-null-format [f & args]
  (try
    (apply gstring/format f args)
    (catch :default _
      f)))

(defn atomist-command [s]
  (re-find #"(?m)atomist (\w+)(.*)?" s))

(defn push [request {[{:keys [branch] {:keys [owner name]} :repo {:keys [message author sha]} :after}] :Push}]
  (go
    (let [[_ command args] (atomist-command message)]
      (if command
        (<! (-> request
                (api/channel "git-chatops-skill")
                (api/simple-message (no-null-format "run %s %s for branch %s on %s/%s from %s"
                                                    command
                                                    args
                                                    branch
                                                    owner
                                                    name
                                                    (:login author)))))
        (<! (-> request
                (api/channel "git-chatops-skill")
                (api/simple-message (gstring/format "No command in ```%s```" (or message "missing message"))))))
      request)))

(defn comment-made [request {[{:keys [body]}] :Comment}]
  (go
    (let [[_ command args] (atomist-command body)]
      (if command
        (assoc request :command command :args args)
        request))))

(defn custom-middleware [handler]
  (fn [request]
    (go
      (let [data (-> request :data)]
        (<! (handler
             (cond
               (contains? data :Push) (<! (push request data))
               (contains? data :Comment) (<! (comment-made request data))
               :else request)))))))

(defn ^:export handler
  [data sendreponse]
  (api/make-request
   data
   sendreponse
   (-> (api/finished)
       (custom-middleware)
       (api/extract-github-token)
       (api/add-slack-source-to-event)
       (api/log-event)
       (api/status :send-status (fn [request] (if-let [data-keys (-> request :data keys)]
                                                (gstring/format "processed %s" data-keys)
                                                "check this"))))))

