(ns atomist.commands.wish
  (:require [atomist.shell :as shell]
            [clojure.string :as string]
            [cljs.core.async :refer [<!] :as async]
            [atomist.commands :refer [run]]
            [atomist.cljs-log :as log]
            [goog.string :as gstring]
            [goog.string.format]
            [atomist.api :as api])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defmethod run "wish" [{{:command/keys [args]} :command :as request}]
  (go
    (<! (-> request
            (api/user "slimslenderslacks")
            (api/simple-message (gstring/format "```%s```" args))))))
