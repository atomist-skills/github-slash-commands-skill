(ns atomist.commands.cc
  (:require [atomist.shell :as shell]
            [cljs.core.async :refer [<!]]
            [clojure.string :as string]
            [goog.string :as gstring]
            [goog.string.format]
            [atomist.api :as api]
            [atomist.commands :refer [run]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn setup-channel [request s]
  (if-let [[_ channel] (re-find #"^#(.*)$" s)]
    (api/channel request channel)
    (if-let [[_ user] (re-find #"^@(.*)$" s)]
      (api/user request user)
      request)))

(defn message-or-comment [{{:command/keys [repo]
                            :label/keys [number]
                            :push/keys [sha]} :command}]
  (if number
    [(gstring/format "https://github.com/%s/%s/issues/%d" (:owner repo) (:name repo) number)
     (gstring/format "Issue #%d" number)]
    [(gstring/format "https://github.com/%s/%s/commit/%s" (:owner repo) (:name repo) sha)
     (gstring/format "Commit %s" sha)]))

(defmethod run "cc" [{{:command/keys [args login message]} :command :as request}]
  (go
    (let [{_ :options errors :errors just-args :arguments}
          (shell/raw-message->options {:raw_message args}
                                      [[nil "--slack"]])
          channel-or-user (first just-args)]
      (if (empty? errors)
        (if (or
             (string/starts-with? channel-or-user "#")
             (string/starts-with? channel-or-user "@"))
          (let [[url s] (message-or-comment request)
                response (<! (-> request
                                 (setup-channel channel-or-user)
                                 (api/block-message [{:type "section"
                                                      :text {:type "mrkdwn"
                                                             :text (gstring/format "CC from %s in *<%s|%s>*"
                                                                                   login url s)}}
                                                     {:type "section"
                                                      :text {:type "mrkdwn"
                                                             :text (gstring/format "```%s```" (apply str (take 400 message)))}}])))]
            (:status response))
          {:errors "argument to cc must begin with either a '@' or a '#'"})
        {:errors errors}))))