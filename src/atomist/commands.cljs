(ns atomist.commands
  (:require [cljs.spec.alpha :as s]
            [atomist.cljs-log :as log]
            [atomist.shell :as shell]
            [cljs.core.async :refer [<!] :as async]
            [clojure.string :as string]
            [goog.string :as gstring]
            [goog.string.format]
            [atomist.github :as github])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defmulti run :command/command)

(s/def :command/spec (s/or :label :label/label
                           :pr :pr/pr))
(s/def :label/number integer?)
(s/def :label/default-color string?)
(s/def :label/label (s/merge :command/base (s/keys :req [:label/number :label/default-color])))
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

(defn ensure-label [request label default-color]
  (go
    (let [response (<! (github/get-label request label))]
      (log/info "get-label: " response)
      (if (not response)
        (<! (github/add-label request {:name label
                                       :color default-color
                                       :description "added by atomist/git-chatops-skill"}))
        response))))

(defmethod run "label" [{:command/keys [args token repo message]
                         :label/keys [number default-color]}]
  (go
    (let [{{:keys [rm]} :options errors :errors just-args :arguments}
          (shell/raw-message->options {:raw_message args}
                                      [[nil "--rm"]])
          request {:ref {:owner (:owner repo) :repo (:name repo)}
                   :number number
                   :token token}
          labels (->> just-args
                      (mapcat #(string/split % #","))
                      (map string/trim)
                      (map (fn [label] (if (or (< (count label) 3)
                                               (re-find #"[\.\#]" label))
                                         :error
                                         label)))
                      (into []))]
      (log/info "labels to create:  " (pr-str labels))
      (if (some #(= :error %) labels)
        {:errors [(gstring/format "invalid label names %s" labels)]}
        (if rm
          (let [response (<! (->> (for [label labels] (github/rm-label request label))
                                  (async/merge)
                                  (async/reduce conj [])))]
            (log/debugf "rm-labels: %s" (->> response
                                             (map :status)
                                             (interpose ",")
                                             (apply str))))
          (do
            (let [response (<! (->> (for [label labels] (ensure-label request label default-color))
                                    (async/merge)
                                    (async/reduce conj [])))]
              (log/debugf "ensure-labels:  %s" (->> response
                                                    (map :status)
                                                    (interpose ",")
                                                    (apply str))))
            (log/debugf "put-labels: %s" (:status (<! (github/put-label
                                                       (assoc request
                                                              :labels labels)))))))))))

(comment
  (run {:command/command "label"
        :command/args "hey2,hey3 hey4"
        :command/repo {:owner "atomist-skills"
                       :name "git-chatops-skill"}
        :command/token ""
        :label/number 9
        :label/default-color "f29513"}))
