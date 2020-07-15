(ns atomist.commands.gh
  (:require [atomist.shell :as shell]
            [cljs.core.async :refer [<!] :refer-macros [go]]
            [atomist.commands :refer [run]]
            [atomist.cljs-log :as log]
            [atomist.github :as github]))

(defmethod run "pr" [{{:command/keys [args token repo]
                       :push/keys [branch]} :command}]
  (go
   {:exitCode 0}))