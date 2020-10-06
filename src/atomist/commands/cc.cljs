;; Copyright © 2020 Atomist, Inc.
;;
;; Licensed under the Apache License, Version 2.0 (the "License");
;; you may not use this file except in compliance with the License.
;; You may obtain a copy of the License at
;;
;;     http://www.apache.org/licenses/LICENSE-2.0
;;
;; Unless required by applicable law or agreed to in writing, software
;; distributed under the License is distributed on an "AS IS" BASIS,
;; WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
;; See the License for the specific language governing permissions and
;; limitations under the License.

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
                                                             :text (gstring/format "```%s```" (apply str (take 400 message)))}}
                                                     {:type "divider"}
                                                     {:type "context"
                                                      :elements [{:type "image"
                                                                  :image_url "https://images.atomist.com/logo/atomist-black-mark-xsmall.png"
                                                                  :alt_text "Atomist icon"}
                                                                 {:type "mrkdwn"
                                                                  :text (gstring/format "%s/%s \u00B7 <https://go.atomist.com/%s/manage/skills/configure/%s/%s|Configure>"
                                                                                        (-> request :skill :namespace)
                                                                                        (-> request :skill :name)
                                                                                        (-> request :team :id)
                                                                                        (-> request :skill :id)
                                                                                        (-> request :configuration :name))}]}])))]
            response)
          {:errors "argument to cc must begin with either a '@' or a '#'"})
        {:errors errors}))))