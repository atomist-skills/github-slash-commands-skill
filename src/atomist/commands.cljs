(ns atomist.commands
  (:require [cljs.spec.alpha :as s]
            [atomist.github :as github]
            [atomist.api :as api]
            [atomist.cljs-log :as log]
            [goog.string :as gstring]
            [goog.string.format]
            [cljs.core.async :refer [<!] :refer-macros [go]]))

(def command-prefixes #{"pr" "label" "cc" "issue"})

(defmulti run (comp :command/command :command))

(defn authorization-link [{:keys [team-id resource-provider-id redirect-uri state]}]
  (gstring/format
   "https://api.atomist.com/v2/auth/teams/%s/resource-providers/%s/token?redirect-uri=%s&state=%s"
   team-id
   resource-provider-id
   (js/encodeURIComponent (or redirect-uri "https://www.atomist.com/success"))
   (or state "state")))

(defn consider-authorizing-your-user [{{:command/keys [token repo command source] sha :push/sha number :label/number} :command :as request}]
  (let [m {:owner (:owner repo) :repo (:name repo) :token token}
        comment-body (gstring/format
                      "running %s with installation token - consider authorizing your user so that slash commands run as you:  [Authorization link](%s)"
                      command
                      (authorization-link
                       {:team-id (-> request :team :id)
                        :resource-provider-id (-> request :provider :id)}))]
    (case source
      :command.source/commit-message
      (github/post-commit-comment m sha comment-body)
      :command.source/issue-comment
      (github/post-pr-comment m number comment-body))))

(defn try-user-then-installation [request login h]
  (let [installation-token (:token request)]
    ((-> (fn [{:keys [token person]}]
           (go
             (if person
               (log/info "using user token")
               (do
                 (<! (consider-authorizing-your-user request))
                 (log/info "using installation token")))
             (if (and token person)
               (<! (h request {:person person
                               :token token}))
               (<! (h request {:token installation-token})))))
         (api/extract-github-user-token-by-github-login))
     (assoc request :login login))))

(defn commit-error [{{:command/keys [token repo command source] sha :push/sha number :label/number} :command :as request} [code message]]
  (let [m {:owner (:owner repo) :repo (:name repo) :token token}
        comment-body
        (case code
          :bad-creds (gstring/format
                      "Authorization failure running `/%s`. You can re-authorize your GitHub user from [this authorization link](%s)"
                      command (authorization-link
                               {:team-id (-> request :team :id)
                                :resource-provider-id (-> request :provider :id)}))
          :user-auth-only (gstring/format
                           "The `%s` command can only be run with User authorization.  You can authorize your GitHub user from [this authorization link](%s)"
                           command (authorization-link
                                    {:team-id (-> request :team :id)
                                     :resource-provider-id (-> request :provider :id)}))
          :client-error (gstring/format "unable to run `/%s`:\n\n%s" command message)
          (gstring/format "unable to run `/%s`:\n\n%s" command message))]
    (log/info "set error on " (or sha number))
    (case source
      :command.source/commit-message
      (github/post-commit-comment m sha comment-body)
      :command.source/issue-comment
      (github/post-pr-comment m number comment-body))))

;; will only have the sha on Commit messages
(s/def :push/sha string?)
(s/def :push/branch string?)
;; will only have the number for PR and Issue Comments
(s/def :label/number integer?)
(s/def :label/default-color string?)
;; whether it's an Issue/PR Comment or a Commit message, we'll have the following data
;; we'll pass args un processed to each command handler
(s/def :command/args string?)
(s/def :repo/owner string?)
(s/def :repo/name string?)
(s/def :command/repo (s/keys :req-un [:repo/owner :repo/name]))
(s/def :command/token string?)
;; the message refers to either the Commit message or the Pr/Issue Comment
(s/def :command/message string?)
(s/def :command/login string?)
(s/def :command/source #{:command.source/commit-message :command.source/issue-comment :command.source/commit-comment})
(s/def :command/base (s/keys :req [:command/command :command/args :command/token :command/repo :command/message :command/login :command/source]))

(s/def :command/command command-prefixes)
(defmulti command-type :command/command)
(defmethod command-type "pr" [_] (s/merge :command/base (s/keys :opt [:push/branch :label/number :push/sha])))
(defmethod command-type "label" [_] (s/merge :command/base (s/keys :req [:label/number :label/default-color])))
(defmethod command-type "cc" [_] (s/merge :command/base (s/keys :opt [:label/number :push/branch :push/sha])))
(defmethod command-type "issue" [_] :command/base)
(s/def :command/spec (s/multi-spec command-type :command/command))
