(ns atomist.commands
  (:require [cljs.spec.alpha :as s]
            [atomist.cljs-log :as log]
            [atomist.shell :as shell]
            [cljs.core.async :refer [<!]]
            [clojure.string :as string]
            [goog.string :as gstring]
            [goog.string.format]
            [atomist.github :as github])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defmulti run :command/command)

(s/def :command/spec (s/or :label :label/label
                           :pr :pr/pr))
(s/def :label/number integer?)
(s/def :label/label (s/merge :command/base (s/keys :req [:label/number])))
(s/def :pr/pr (s/merge :command/base (s/keys :req [:pr/branch])))
(s/def :command/command #{"pr" "label"})
(s/def :command/args string?)
(s/def :repo/owner string?)
(s/def :repo/name string?)
(s/def :command/repo (s/keys :req-un [:repo/owner :repo/name]))
(s/def :command/token string?)
(s/def :command/message string?)
(s/def :command/base (s/keys :req [:command/command :command/args :command/token :command/repo :command/message]))
(s/def :command/pr (s/keys :req []))

(defmethod run "pr" [{:command/keys [args token repo message]
                      :pr/keys [branch]}]
  (go
    (let [{{:keys [title base]} :options errors :errors}
          (shell/raw-message->options {:raw_message args}
                                      [[nil "--title TITLE" "Pull Request Title"]
                                       [nil "--base BASE" "base branch ref"
                                        :default "master"]])]
      (if (empty? errors)
        (let [response (<! (atomist.github/post-pr
                            {:token token :owner (:owner repo) :repo (:name repo)}
                            title "submitted from git-chatops-skill" branch base))]
          (log/info response)
          (:status response))
        {:errors errors}))))

(defmethod run "label" [{:command/keys [args token repo message]
                         :label/keys [number]}]
  (go
    (let [labels (-> args
                     (string/split #",")
                     (->>
                      (map string/trim)
                      (map (fn [label] (if (or (< (count label) 3)
                                               (re-find #"[\.\#]" label))
                                         :error
                                         label)))
                      (into [])))]
      (log/info "labels to create:  " (pr-str labels))
      (if (some #(= :error %) labels)
        {:errors [(gstring/format "invalid label names %s" labels)]}
        (let [{{:keys [rm]} :options errors :errors}
              (shell/raw-message->options {:raw_message args}
                                          [[nil "--rm"]])
              request {:ref {:owner (:owner repo) :repo (:name repo)}
                       :token token}]
          (if rm
            (let [response (<! (github/rm-label request (first labels)))]
              response)
            (let [response (<! (github/get-label request (first labels)))]
              (if response
                (log/info "label is present")
                (log/info "label is not present"))
              (if (not response)
                (<! (github/add-label request {:name (first labels)
                                                       :color "f29513" ;; TODO default
                                                       :description "chatops"})))
              (let [response (<! (github/put-label
                                  {:token token :owner (:owner repo) :repo (:name repo)
                                   :labels labels :number number}))]
                (log/info response)
                response))))))))
