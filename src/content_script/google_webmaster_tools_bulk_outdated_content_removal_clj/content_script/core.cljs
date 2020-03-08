(ns google-webmaster-tools-bulk-outdated-content-removal-clj.content-script.core
  (:require-macros [cljs.core.async.macros :refer [go-loop]])
  (:require [cljs.core.async :refer [<!]]
            [chromex.logging :refer-macros [log info warn error group group-end]]
            [chromex.protocols.chrome-port :refer [post-message!]]
            [chromex.ext.runtime :as runtime :refer-macros [connect]]
            [google-webmaster-tools-bulk-outdated-content-removal-clj.content-script.common :as common]
            ))

;; (defn exec-removal-request
;;   [l]

;;   )

; -- a message loop ---------------------------------------------------------------------------------------------------------

(defn process-message! [chan message]
  (let [_ (log "CONTENT SCRIPT: got message:" message)
        {:keys [type] :as whole-msg} (common/unmarshall message)]
    (cond (= type :done-init-victims) (post-message! chan (common/marshall {:type :next-victim}))
          (= type :remove-url) (do (prn "handling removel-url") ;;xxx
                                   (prn "whole-msg")
                                   )
          )))

; -- main entry point -------------------------------------------------------------------------------------------------------

(defn init! []
  (let [_ (log "CONTENT SCRIPT: init")
        background-port (runtime/connect)]

    (common/connect-to-background-page! background-port process-message!)))
