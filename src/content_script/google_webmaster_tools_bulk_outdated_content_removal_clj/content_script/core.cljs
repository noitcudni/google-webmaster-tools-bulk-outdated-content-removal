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
            [google-webmaster-tools-bulk-outdated-content-removal-clj.background.storage :refer [update-storage clear-victims!]]
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
  (when-not (nil? victim-url)
    ;; NOTE: in most cases the series of user actions will eventually cause the page to reload.
    ;; However, for error cases, there's no page reload. Hence, the channel is needed to
    ;; communicate back up to the call stack.
    (let [ch (chan)]
      (go
        (let [;; wait for the button to be loaded
              _ (<! (sync-single-node "//button/div[contains(text(), 'Request Removal')]"))
              ;; Fill out the input field

              _ (domina/set-value! (-> "//input[contains(@class,'gwt-TextBox')]" xpath single-node)
                                   victim-url)
              ;; click on the Request Removal button
              _ (-> "//button/div[contains(text(), 'Request Removal')]" xpath single-node .click)
              dialog (<! (sync-single-node
                          "//div[@role='dialog']//*[contains(text(), 'Now you can submit your temporary removal request.')]"
                          "//div[@role='dialog']//*[contains(text(), 'The URL you want to remove is:')]"
                          "//div[@role='dialog']//*[contains(text(), 'The image you want to remove is:')]"
                          "//div[@role='dialog']//*[contains(text(), 'This content is no longer live on the website.')]"
                          "//div[contains(text(), 'Dismiss')]"
                          ))]
          (cond
            ;; case 1
            (-> "//div[@role='dialog']//*[contains(text(), 'Now you can submit your temporary removal request.')]"
                xpath
                single-node)
            (do (prn "Now you can submit your temporary removal request.")
                (.click (<! (sync-single-node "//div[@role='dialog']//button//div[contains(text(), 'Request Removal')]")))
                (.click (<! (sync-single-node "//div[@role='dialog']//button//div[contains(text(), 'OK')]")))
                (<! (update-storage victim-url "status" "removed"))
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
                  (<! (update-storage victim-url "status" "removed"))
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
                  (let [el (<! (sync-single-node "//div[@role='dialog']//button//div[contains(text(), 'OK')]"
                                                 "//div[contains(text(), 'Cancel')]"))]
                    (cond (-> "//div[@role='dialog']//button//div[contains(text(), 'OK')]" xpath single-node) (.click el)
                          (-> "//div[contains(text(), 'Cancel')]" xpath single-node) (do (.click el)
                                                                                         (>! ch :invalid-prefilled-url))
                          ))
                  (<! (update-storage victim-url "status" "removed"))
                  (>! ch :success)
                  ))

            ;;case 4
            (-> "//div[@role='dialog']//*[contains(text(), 'This content is no longer live on the website.')]"
                xpath
                single-node)
            (do
              (prn "case 4: content is no longer live on the website")
              (.click (<! (sync-single-node "//div[@role='dialog']//button//div[contains(text(), 'Request Removal')]")))
              (let [el (<! (sync-single-node "//div[@role='dialog']//button//div[contains(text(), 'OK')]"
                                             "//div[@role='dialog']//div[contains(text(), 'A removal request for this URL has already been made.')]"))]
                (cond
                  ;; success
                  (-> "//div[@role='dialog']//button//div[contains(text(), 'OK')]" xpath single-node)
                  (do (.click el)
                      (<! (update-storage victim-url "status" "removed")))
                  ;; duplicate url case
                  (-> "//div[@role='dialog']//div[contains(text(), 'A removal request for this URL has already been made.')]" xpath single-node)
                  (do (.click (-> "//div[@role='dialog']//button//div[contains(text(), 'Cancel')]" xpath single-node))
                      (<! (update-storage victim-url "status" "removed"))))))

            ;;case 5 : invalid URL entry
            (-> "//div[contains(text(), 'Oops')]"
                xpath
                single-node)
            (when-let [dismiss-btn (-> "//div[contains(text(), 'Dismiss')]" xpath single-node)]
              (.click dismiss-btn)
              (>! ch :invalid-url))
            :else
            (prn "Didn't satisfy any of the above cases!!")
            )))
      ch
      )))

; -- a message loop ---------------------------------------------------------------------------------------------------------

(defn process-message! [chan message]
  (let [_ (log "CONTENT SCRIPT: got message:" message)
        {:keys [type victim supplementary-arg] :as whole-msg} (common/unmarshall message)]
    (cond (= type :remove-url) (do (prn "handling removel-url") ;;xxx
                                   (when-not (or (nil? victim) (= victim "poison-pill"))
                                     (go
                                       (let [request-status (<! (exec-removal-request victim supplementary-arg))]
                                         ;; encounter an error go to the next victim
                                         (when (not= request-status :success)
                                           ;; TODO log error. This should probably happen in the background
                                           (prn ">> whole-msg that causes error: " whole-msg)
                                           ;; reload the page to get to the next victim
                                           ;; once the background is done handling the error, it will fire back a reload message
                                           (post-message! chan (common/marshall {:type :skip-error
                                                                                 :reason request-status
                                                                                 :url victim}))
                                           )
                                         ))))
          (= type :reload) (do (prn "reloading..")
                               (.reload js/location)
                               )
          (= type :done ) (do
                            (go (<! (clear-victims!))
                                (js/alert "All done!")))
          )))

; -- main entry point -------------------------------------------------------------------------------------------------------

(defn init! []
  (let [_ (log "CONTENT SCRIPT: init")
        background-port (runtime/connect)]
    ;; since remove outdated content does a page reload,
    ;; we need to ask the backgrounmd for next victim
    (post-message! background-port (common/marshall {:type :next-victim}))

    (common/connect-to-background-page! background-port process-message!)))
