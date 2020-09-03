(ns atomist.commands.pr
  (:require [atomist.shell :as shell]
            [cljs.core.async :refer [<!]]
            [atomist.commands :refer [run try-user-then-installation commit-error]]
            [atomist.cljs-log :as log]
            [atomist.github :as github]
            [atomist.local-runner :as lr]
            [goog.string :as gstring]
            [goog.string.format]
            [atomist.api :as api])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defmethod run "pr" [{{:command/keys [args token repo login]
                       :push/keys [branch]
                       :label/keys [number]} :command :as request}]
  (go
    (let [{{:keys [title base labels draft]} :options
           errors :errors
           just-args :arguments}
          (shell/raw-message->options {:raw_message args}
                                      [[nil "--title TITLE" "Pull Request Title"]
                                       [nil "--approve" "Approve Pull Request"]
                                       [nil "--request-changes" "request changes"]
                                       [nil "--draft"]
                                       [nil "--base BASE" "base branch ref"
                                        :default "master"]
                                       ["-l" "--label LABEL" "add labels"
                                        :id :labels
                                        :default []
                                        :assoc-fn (fn [m k v] (update-in m [k] conj v))]
                                       ["-r" "--reviewer REVIEWER" "assign reviewers"
                                        :id :reviewers
                                        :default []
                                        :assoc-fn (fn [m k v] (update-in m [k] conj v))]])]
      (if (empty? errors)
        (let [p {:token token :owner (:owner repo) :repo (:name repo)}]
          (cond

            ;; create when /pr --title --base and our current commit points at a branch (Commit message only)
            (and branch base title)
            (<! (try-user-then-installation request login
                                            (fn [_ {:keys [token]}]
                                              (go
                                                (let [response (<! (github/post-pr
                                                                    {:token token :owner (:owner repo) :repo (:name repo)}
                                                                    (merge {:title title
                                                                            :body "submitted from git-chatops-skill"
                                                                            :head branch
                                                                            :base base}
                                                                           (if draft {:draft true}))
                                                                    labels))]
                                                  (cond (= "Bad credentials" (-> response :body :message))
                                                        (do
                                                          (<! (commit-error request ["Bad credentials"]))
                                                          response)
                                                        :else response))))))

            ;; close a PR from a comment
            (and (some #{"close"} just-args) number)
            (<!
             (try-user-then-installation request login
                                         (fn [_ {:keys [token]}]
                                           (github/patch-pr-state (assoc p :token token) number "closed"))))

            ;; close a PR from a commit to it's branch
            (and (some #{"close"} just-args) branch)
            (<!
             (try-user-then-installation request login
                                         (fn [_ {:keys [token]}]
                                           (github/close-pr (assoc p :token token) branch))))

            ;; mark a PR READY from a Comment
            (and (some #{"ready"} just-args) number)
            (<!
             (try-user-then-installation request login
                                         (fn [_ {:keys [person token]}]
                                           (go
                                             (if person
                                               (<! (github/pr-is-ready-by-number (assoc p :token token) number))
                                               (let [errors {:errors ["github authorization required for marking PRs ready for review"]}]
                                                 (log/warn "can not mark PRs ready with an installation token")
                                                 (<! (commit-error request (:errors errors)))
                                                 errors))))))

            ;; mark a PR READY from a Commit to it's head branch
            (and (some #{"ready"} just-args) branch)
            (<!
             (try-user-then-installation
              request
              login
              (fn [_ {:keys [person token]}]
                (go
                  (if person
                    (<! (github/pr-is-ready-by-branch (assoc p :token token) branch))
                    (let [errors {:errors ["github authorization required for marking PRs ready for review"]}]
                      (log/warn "can not mark PRs ready with an installation token")
                      (<! (commit-error request (:errors errors)))
                      errors))))))

            ;(and (some #{"draft"} just-args) number)
            ;(<! (github/patch-pr-draft-state p number true))
            ;(and (some #{"draft"} just-args) branch)
            ;(<! (github/draft-pr p branch true))

            ;; TODO can we do anything with reviews?

            :else
            (let [errors {:errors [(gstring/format "bad arguments to /pr")]}]
              (<! (commit-error request (:errors errors)))
              errors)))

        {:errors errors}))))

;; create --title --label --reviewer --project --base
;; close --repo number | branch | url
;; ready --repo number | branch | url
;; review --approve --body --comment --request-changes
