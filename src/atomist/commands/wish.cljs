(ns atomist.commands.wish
  (:require [cljs.core.async :refer [<!] :refer-macros [go]]
            [atomist.commands :refer [run]]
            [goog.string :as gstring]
            [goog.string.format]
            [atomist.api :as api]))

(defmethod run "wish" [{{:command/keys [args]} :command :as request}]
  (go
    (<! (-> request
            (api/user "slimslenderslacks")
            (api/simple-message (gstring/format "```%s```" args))))))
