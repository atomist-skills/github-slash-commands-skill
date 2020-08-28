(ns atomist.commands
  (:require [cljs.spec.alpha :as s]
            [atomist.github :as github]
            [atomist.api :as api]))

(def command-prefixes #{"pr" "label" "cc" "issue"})

(defmulti run (comp :command/command :command))

(defn try-user-then-installation [request login h]
  (let [installation-token (:token request)]
    ((-> (fn [{:keys [token person]}]
           (if (and token person)
             (h request {:person person
                         :token token})
             (h request {:token installation-token})))
         (api/extract-github-user-token-by-github-login))
     (assoc request :login login))))

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
(s/def :command/base (s/keys :req [:command/command :command/args :command/token :command/repo :command/message :command/login]))

(s/def :command/command command-prefixes)
(defmulti command-type :command/command)
(defmethod command-type "pr" [_] (s/merge :command/base (s/keys :req [:push/branch])))
(defmethod command-type "label" [_] (s/merge :command/base (s/keys :req [:label/number :label/default-color])))
(defmethod command-type "cc" [_] (s/merge :command/base (s/keys :opt [:label/number :push/branch :push/sha])))
(defmethod command-type "issue" [_] :command/base)
(s/def :command/spec (s/multi-spec command-type :command/command))
