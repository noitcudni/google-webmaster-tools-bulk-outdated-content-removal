(ns google-webmaster-tools-bulk-outdated-content-removal-clj.content-script.core
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [cljs.core.async :refer [<! >! chan] :as async]
            [chromex.logging :refer-macros [log info warn error group group-end]]
            [chromex.protocols.chrome-port :refer [post-message!]]
            [chromex.ext.runtime :as runtime :refer-macros [connect]]
            [google-webmaster-tools-bulk-outdated-content-removal-clj.content-script.common :as common]
            [domina :refer [single-node nodes style styles]]
            [domina.xpath :refer [xpath]]
            [dommy.core :refer-macros [sel sel1] :as dommy]
            ))

(defn sync-node-helper
  "This is unfortunate. alts! doens't close other channels"
  [dom-fn & xpath-strs]
  (go-loop []
    (let [n (->> xpath-strs
                 (map (fn [xpath-str]
                        (dom-fn (xpath xpath-str))
                        ))
                 (filter #(some? %))
                 first)]
      (if (nil? n)
        (do (<! (async/timeout 300))
            (recur))
        n)
      )))

(def sync-single-node (partial sync-node-helper single-node))
(def sync-nodes (partial sync-node-helper nodes))

(defn sync-single-node-visible [& xpath-strs]
  (go
    (let [target-el (<! (apply sync-single-node xpath-strs))
          _ (prn target-el)
          ]
      (loop []
        (prn "target-el: " (-> target-el
                               js/window.getComputedStyle
                               (aget "visibility"))) ;xxx
        (cond (= (-> target-el
                     js/window.getComputedStyle
                     (aget "visibility")) "visible")
              target-el
              :else (do
                      (<! (async/timeout 300))
                      (prn "sync-single-node waiting...")
                      (recur))
              )))))

(defn exec-removal-request
  [victim-url supplementary-arg]
  (let [ch (chan)]
    (go
      (let [
            ;; Fill out the input field
            _ (domina/set-value! (-> "//input[contains(@class,'gwt-TextBox')]" xpath single-node)
                                 victim-url)
            ;; click on the Request Removal button
            _ (-> "//button/div[contains(text(), 'Request Removal')]" xpath single-node .click)
            dialog (<! (sync-single-node
                        "//div[@role='dialog']//*[contains(text(), 'Now you can submit your temporary removal request.')]"
                        "//div[@role='dialog']//*[contains(text(), 'The URL you want to remove is:')]"
                        "//div[@role='dialog']//*[contains(text(), 'The image you want to remove is:')]"))]
        (cond
          ;; case 1
          (-> "//div[@role='dialog']//*[contains(text(), 'Now you can submit your temporary removal request.')]"
              xpath
              single-node)
          (do (prn "Now you can submit your temporary removal request.")
              (.click (<! (sync-single-node "//div[@role='dialog']//button//div[contains(text(), 'Request Removal')]")))
              (.click (<! (sync-single-node "//div[@role='dialog']//button//div[contains(text(), 'OK')]")))
              (>! ch :success))

          ;; case 2
          (-> "//div[@role='dialog']//*[contains(text(), 'The URL you want to remove is:')]"
              xpath
              single-node)
          (do (prn "The URL you want to remove is")
              (.click (<! (sync-single-node "//div[@role='dialog']//button//div[contains(text(), 'Next')]")))
              (.click (<! (sync-single-node "//div[@role='dialog']//button//div[contains(text(), 'Next')]")))
              (<! (sync-single-node "//div[@role='dialog']//input[@placeholder='Enter one word here...']"))
              ;; TODO: how to wait for user action. Does the page refresh?
              (when-not (empty? supplementary-arg)
                (-> "//div[@role='dialog']//input[@placeholder='Enter one word here...']"
                    xpath
                    single-node
                    (domina/set-value! supplementary-arg))
                (.click (<! (sync-single-node "//div[@role='dialog']//button//div[contains(text(), 'Request Removal')]")))
                (.click (<! (sync-single-node "//div[@role='dialog']//button//div[contains(text(), 'OK')]")))
                (>! ch :success)
                ))

          ;; case 3
          (-> "//div[@role='dialog']//*[contains(text(), 'The image you want to remove is:')]"
              xpath
              single-node)
          (do (prn  "The image you want to remove is:")
              (.click (<! (sync-single-node "//div[@role='dialog']//button//div[contains(text(), 'Next')]")))
              (<! (sync-single-node "//div[@role='dialog']//input[@placeholder='Example URL: https://www.google.com/url?url=http://www.example.com/oldpage']"))
              (when-not (empty? supplementary-arg)
                ;; TODO: The button isn't lit up!
                (let [key-evt (js/document.createEvent "KeyboardEvent")
                      evt (js/document.createEvent "HTMLEvents")
                      _ (.initEvent evt "keyup" true false)
                      _ (aset evt "keyCode" 13)
                      input-el (-> "//div[@role='dialog']//input[@placeholder='Example URL: https://www.google.com/url?url=http://www.example.com/oldpage']"
                                   xpath
                                   single-node)]
                  (domina/set-value! input-el supplementary-arg)
                  ;; need to simulate a keyup event in order to make the Request Removal button clickable.
                  (.dispatchEvent input-el evt))

                (.click (<! (sync-single-node "//div[@role='dialog']//button//div[contains(text(), 'Request Removal')]")))
                (.click (<! (sync-single-node "//div[@role='dialog']//button//div[contains(text(), 'OK')]")))
                (>! ch :success)
                ))
          )
        ))))

; -- a message loop ---------------------------------------------------------------------------------------------------------

(defn process-message! [chan message]
  (let [_ (log "CONTENT SCRIPT: got message:" message)
        {:keys [type victim supplementary-arg] :as whole-msg} (common/unmarshall message)]
    (cond (= type :done-init-victims) (post-message! chan (common/marshall {:type :next-victim}))
          (= type :remove-url) (do (prn "handling removel-url") ;;xxx
                                   (exec-removal-request victim supplementary-arg)
                                   )
          )))

; -- main entry point -------------------------------------------------------------------------------------------------------

(defn init! []
  (let [_ (log "CONTENT SCRIPT: init")
        background-port (runtime/connect)]

    (common/connect-to-background-page! background-port process-message!)))
