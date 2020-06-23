(ns atomist.commands.pr
  (:require [atomist.shell :as shell]
            [cljs.core.async :refer [<!]]
            [atomist.commands :refer [run]]
            [atomist.cljs-log :as log]
            [atomist.github :as github])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defmethod run "pr" [{{:command/keys [args token repo]
                       :push/keys [branch]} :command}]
  (go
    (let [{{:keys [title base]} :options errors :errors}
          (shell/raw-message->options {:raw_message args}
                                      [[nil "--title TITLE" "Pull Request Title"]
                                       [nil "--base BASE" "base branch ref"
                                        :default "master"]])]
      (if (empty? errors)
        (let [response (<! (github/post-pr
                            {:token token :owner (:owner repo) :repo (:name repo)}
                            title "submitted from git-chatops-skill" branch base []))]
          (log/info response)
          (:status response))
        {:errors errors}))))