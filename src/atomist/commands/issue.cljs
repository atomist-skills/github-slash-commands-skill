(ns atomist.commands.issue
  (:require [atomist.shell :as shell]
            [cljs.core.async :refer [<!]]
            [atomist.github :as github]
            [goog.string.format]
            [atomist.commands :refer [run]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn- create-issue [{{:command/keys [repo message]} :command :as request} {:keys [labels title assignees]}]
  (github/create-issue request (:owner repo) (:name repo) {:title title :body message :labels labels :assignees assignees}))

(defn- close-issue [{{:command/keys [repo]} :command :as request} number]
  (github/patch-issue request (:owner repo) (:name repo) number {:state "closed"}))

(defn- args->issue-number [args]
  (->> (some #(re-find #"#(\d+)" %) args)
       second))

;; /issue create
;; /issue close --number 45
;; /issue close #45
;; these make sense in Commit messages
;; they can be called from Issue/PR comments as well
(defmethod run "issue" [{{:command/keys [args]
                          :label/keys [number]} :command :as request}]
  (go
    (let [{options :options errors :errors just-args :arguments}
          (shell/raw-message->options {:raw_message args}
                                      [["-l" "--label LABEL"
                                        :id :labels
                                        :default []
                                        :assoc-fn (fn [m k v] (update-in m [k] conj v))]
                                       [nil "--title TITLE"]
                                       ["-n" "--number NUMBER"]
                                       [nil "--assignee ASSIGNEE"
                                        :id :assignees
                                        :default []
                                        :assoc-fn (fn [m k v] (update-in m [k] conj v))]
                                       [nil "--project PROJECT"]])]
      (if (empty? errors)
        (cond
          ;; create an issue using labels, title, and assignees from args
          (some #{"create"} just-args)
          (<! (create-issue request options))

          ;; close an issue when you can parse a #45 out of an issue
          (if-let [issue-number (args->issue-number just-args)]
            (and (some #{"close"} just-args) issue-number)) (<! (close-issue request (int (args->issue-number just-args))))
          ;; close an issue when a --number argument is explicitly given
          (and (some #{"close"} just-args) (:number options)) (<! (close-issue request (:number options)))
          ;; close an issue when you're making a comment on that issue, and the number has been extracted from the event
          (and (some #{"close"} just-args) number) (<! (close-issue request number))
          :else {:errors ["/issue must specify either 'close' or 'create'"]})
        {:errors errors}))))
