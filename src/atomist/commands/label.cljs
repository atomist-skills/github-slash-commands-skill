(ns atomist.commands.label
  (:require [atomist.shell :as shell]
            [clojure.string :as string]
            [cljs.core.async :refer [<!] :as async]
            [atomist.commands :refer [run]]
            [atomist.cljs-log :as log]
            [goog.string :as gstring]
            [goog.string.format]
            [atomist.github :as github])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn ensure-label [request label default-color]
  (go
    (let [response (<! (github/get-label request label))]
      (log/info "get-label: " response)
      (if (= "Not Found" (:message response))
        (<! (github/add-label request {:name label
                                       :color default-color
                                       :description "added by atomist/git-chatops-skill"}))
        response))))

(defmethod run "label" [{{:command/keys [args token repo]
                          :label/keys [number default-color] :or {default-color "f29513"}} :command}]
  (go
    (let [{{:keys [rm]} :options just-args :arguments}
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
  (run {:command {:command/command "label"
                  :command/args "hey1,hey2"
                  :command/repo {:owner "atomist-skills"
                                 :name "git-chatops-skill"}
                  :command/token "bccf5898b3eaf2f54b71f1538444f92da97737e9"
                  :label/number 15
                  :label/default-color "f29513"}}))