(ns atomist.slack-debug
  (:require [atomist.api :as api]
            [goog.string :as gstring]
            [goog.string.format]))

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
