(ns google-webmaster-tools-bulk-outdated-content-removal-clj.background.core
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [goog.string :as gstring]
            [goog.string.format]
            [cljs.core.async :refer [<! chan]]
            [chromex.logging :refer-macros [log info warn error group group-end]]
            [chromex.chrome-event-channel :refer [make-chrome-event-channel]]
            [chromex.protocols.chrome-port :refer [on-disconnect! post-message! get-sender]]
            [chromex.ext.tabs :as tabs]
            [chromex.ext.runtime :as runtime]
            [chromex.ext.browser-action :refer-macros [set-badge-text set-badge-background-color]]
            [google-webmaster-tools-bulk-outdated-content-removal-clj.content-script.common :as common]
            [google-webmaster-tools-bulk-outdated-content-removal-clj.background.storage :refer [update-storage store-victims!
                                                                                                 next-victim
                                                                                                 print-victims
                                                                                                 get-bad-victims]]))

(def clients (atom []))

; -- clients manipulation ---------------------------------------------------------------------------------------------------

(defn add-client! [client]
  (prn "BACKGROUND: client connected" (get-sender client))
  (on-disconnect! client (fn []
                           ;; https://github.com/binaryage/chromex/blob/master/src/lib/chromex/protocols/chrome_port.cljs
                           (prn "on disconnect callback !!!")
                           ;; cleanup
                           (swap! clients (fn [curr c] (->> curr (remove #(= % c)))) client)))
  (swap! clients conj client))

(defn remove-client! [client]
  (prn "BACKGROUND: client disconnected" (get-sender client))
  (let [remove-item (fn [coll item] (remove #(identical? item %) coll))]
    (swap! clients remove-item client)))


(defn popup-predicate [client]
  (re-find #"popup.html" (-> client
                             get-sender
                             js->clj
                             (get "url"))))

(defn get-popup-client []
  (->> @clients
       (filter popup-predicate)
       first ;;this should only be one popup
       ))

(defn get-content-client []
  (->> @clients
       (filter (complement popup-predicate))
       first ;;this should only be one popup
       ))

(defn fetch-next-victim [client]
  (go
    (let [[victim-url victim-entry] (<! (next-victim))
          _ (prn "fetch-next-victim: victim-url: " victim-url)
          _ (prn "fetch-next-victim: victim-entry: " victim-entry)]
      ;; TODO what to do with victim-entry
      (post-message! client
                     (common/marshall {:type :remove-url
                                       :victim victim-url
                                       :supplementary-arg (get victim-entry "supplementary-arg")
                                       }))
      )))

; -- client event loop ------------------------------------------------------------------------------------------------------

(defn run-client-message-loop! [client]
  (prn "BACKGROUND: starting event loop for client:")
  (go-loop []
    (when-some [message (<! client)]
      (prn "BACKGROUND: got client message:" message "from" (get-sender client))
      (let [{:keys [type url reason] :as whole-edn} (common/unmarshall message)]
        (cond (= type :init-victims) (do
                                       (prn "background: inside :init-victims")
                                       (<! (store-victims! whole-edn))
                                       (<! (fetch-next-victim (get-content-client)))
                                       )
              (= type :next-victim) (do
                                      (prn "background: inside :next-victim")
                                      (<! (fetch-next-victim (get-content-client)))
                                      )
              (= type :skip-error) (do
                                     (let [updated-error-entry (<! (update-storage url
                                                                                   "status" "error"
                                                                                   "error-reason" reason))
                                           error-cnt (->> (<! (get-bad-victims)) count str)
                                           _ (prn "calling get-bad-victims: " error-cnt)]
                                       (set-badge-text (clj->js {"text" error-cnt}))
                                       (set-badge-background-color #js{"color" "#F00"})

                                       ;; ask the content page to reload
                                       (post-message! (get-content-client)
                                                      (common/marshall {:type :reload}))
                                       ))
              (= type :fetch-initial-errors) (let [_ (prn "inside :fetch-initial-errors:")
                                                   bad-victims (<! (get-bad-victims))]
                                               (post-message! client (common/marshall {:type :init-errors
                                                                                       :bad-victims bad-victims})))
              ))
      (recur))
    (prn "BACKGROUND: leaving event loop for client:" (get-sender client))
    (remove-client! client)))

; -- event handlers ---------------------------------------------------------------------------------------------------------

(defn handle-client-connection! [client]
  (add-client! client)
  (run-client-message-loop! client))

;; (defn tell-clients-about-new-tab! []
;;   (doseq [client @clients]
;;     (post-message! client "a new tab was created")))

; -- main event loop --------------------------------------------------------------------------------------------------------

(defn process-chrome-event [event-num event]
  (log (gstring/format "BACKGROUND: got chrome event (%05d)" event-num) event)
  (let [[event-id event-args] event
        _ (prn "event-id: " event-id)
        ]
    (case event-id
      ::runtime/on-connect (apply handle-client-connection! event-args)
      ;; ::tabs/on-created (tell-clients-about-new-tab!)
      nil)))

(defn run-chrome-event-loop! [chrome-event-channel]
  (prn "BACKGROUND: starting main event loop...")
  (go-loop [event-num 1]
    (when-some [event (<! chrome-event-channel)]
      (process-chrome-event event-num event)
      (recur (inc event-num)))
    (prn "BACKGROUND: leaving main event loop")))

(defn boot-chrome-event-loop! []
  (let [chrome-event-channel (make-chrome-event-channel (chan))]
    (tabs/tap-all-events chrome-event-channel)
    (runtime/tap-all-events chrome-event-channel)
    (run-chrome-event-loop! chrome-event-channel)))

; -- main entry point -------------------------------------------------------------------------------------------------------

(defn init! []
  (prn "BACKGROUND: init")
  (boot-chrome-event-loop!))
