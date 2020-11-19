(ns google-webmaster-tools-bulk-outdated-content-removal-clj.content-script.core
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [cljs.core.async :refer [<! >! chan alts!] :as async]
            [chromex.logging :refer-macros [log]]
            [chromex.protocols.chrome-port :refer [post-message!]]
            [chromex.ext.runtime :as runtime :refer-macros [connect]]
            [google-webmaster-tools-bulk-outdated-content-removal-clj.content-script.common :as common]
            [domina :refer [single-node nodes style styles]]
            [domina.xpath :refer [xpath]]
            [dommy.core :as dommy]
            [clojure.string]
            [cemerick.url :refer [url]]
            [google-webmaster-tools-bulk-outdated-content-removal-clj.background.storage :refer [update-storage clear-victims!]]
            ))

(defn sync-node-helper
  "This is unfortunate. alts! doens't close other channels"
  [dom-fn pred-fn & xpath-strs]
  (go-loop []
    (let [n (->> xpath-strs
                 (map (fn [xpath-str]
                        (dom-fn (xpath xpath-str))
                        ))
                 (filter #(some? %))
                 first)]
      (if (or (nil? n)
              (and (not (nil? pred-fn)) (pred-fn n)))
        (do (<! (async/timeout 300))
            (recur))
        n)
      )))

(defn sync-disappeared-node-helper
  [dom-fn & xpath-strs]
  (go-loop []
    (let [n (->> xpath-strs
                 (map (fn [xpath-str]
                        (dom-fn (xpath xpath-str))
                        ))
                 (filter #(some? %))
                 first)]
      (if (not (nil? n))
        (do (<! (async/timeout 300))
            (recur))
        true)
      )))

(def sync-enabled-single-node (partial sync-node-helper single-node #(.-disabled %)))
(def sync-single-node (partial sync-node-helper single-node nil))
(def sync-nodes (partial sync-node-helper nodes nil))
(def sync-disappeared-single-node (partial sync-disappeared-node-helper single-node))

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

(defn scrape-xhr-data!
  "grab the xhr injected data and clean up the extra dom"
  []
  (go
    (let [injected-dom (<! (sync-single-node "//div[@id='__interceptedData']"))]
      (dommy/text injected-dom))))

(defn cleanup-xhr-data! []
  (go
    (doseq [n (<! (sync-nodes  "//div[@id='__interceptedData']"))]
      (.remove n))))


(defn exec-removal-request-v2
  [victim-url supplementary-arg]
  (when-not (nil? victim-url)
    (let [ch (chan)]
      (go
        (let [;; click on the "New request" button
              _ (.click (<! (sync-single-node "//button/span[contains(text(), 'New request')]")))

              ;; wait for the modal dialog to show
              ;; The new request dialog has a Page tab and an Image tab.
              _ (<! (sync-single-node "//button/span/span[contains(text(), 'Page')]"))
              _ (prn "modal dialog showed up")

              ]
          ;; Simulate user actions
          (prn "victim-url: " victim-url)
          (prn "(empty? supplementary-arg) : " (empty? supplementary-arg))
          (if (empty? supplementary-arg)
            (if (clojure.string/includes? victim-url "https://www.google.com/imgres")
              ;; google image url
              (do
                (prn "google image url: " victim-url)
                (.click (<! (sync-single-node "//button/span/span[contains(text(), 'Image')]")))
                ;; click on the radio button
                (.click (<! (sync-single-node "//label[contains(text(), 'Enter \"Copy link address\" URL from Image Search results')]/..//input")))
                (domina/set-value! (-> "//input[@placeholder='Search results URL']" xpath single-node) victim-url)
                (.click (<! (sync-enabled-single-node "//span[contains(text(), 'Submit')]/.."))))
              ;; Page tab
              (do
                (domina/set-value! (-> "//input[@placeholder='Enter page URL']" xpath single-node) victim-url)
                ;; toggle between the tabs to get the submit butotn to show
                (.click (<! (sync-single-node "//button/span/span[contains(text(), 'Image')]")))
                (<! (async/timeout 1000))
                (-> "//button/span/span[contains(text(), 'Page')]" xpath single-node .click)
                (.click (<! (sync-enabled-single-node "//span[contains(text(), 'Submit')]/.."))))
              )
            (if (clojure.string/starts-with? supplementary-arg "http")
              ;; Image tab
             (do
               (.click (<! (sync-single-node "//button/span/span[contains(text(), 'Image')]")))
               ;; click on the radio button
               (.click (<! (sync-single-node "//label[contains(text(), 'Enter image and containing page URLs')]/..//input")))
               (domina/set-value! (-> "//input[@placeholder='Image URL']" xpath single-node) victim-url)
               (domina/set-value! (-> "//input[@placeholder='Containing page URL']" xpath single-node) supplementary-arg)
               (.click (<! (sync-enabled-single-node "//span[contains(text(), 'Submit')]/..")))
               )
             ;; Page tab
             ;; Note: same as the previous page tab block
             (do
               (domina/set-value! (-> "//input[@placeholder='Enter page URL']" xpath single-node) victim-url)
               ;; toggle between the tabs to get the submit butotn to show
               (.click (<! (sync-single-node "//button/span/span[contains(text(), 'Image')]")))
               (<! (async/timeout 1000))
               (-> "//button/span/span[contains(text(), 'Page')]" xpath single-node .click)
               (.click (<! (sync-enabled-single-node "//span[contains(text(), 'Submit')]/.."))))
             ))
            )


          ;; Handle Ajax returned response
          (do
            (<! (cleanup-xhr-data!))
            (let [_ (prn "about to scrape-xhr-data")
                  xhr-data (<! (scrape-xhr-data!))
                  _ (prn "xhr-data: " xhr-data) ;;xxx
                  invalid-xpath "//div[contains(text(), 'Invalid page URL')]"
                  success-xpath "//div[contains(text(), 'Your request has been submitted')]"
                  image-still-exists-xpath "//div[@role='dialog']//h2[contains(text(), 'The image URL still exists')]"
                  page-still-exists-xpath "//div[@role='dialog']//h2[contains(text(), 'This page still exist')]"
                  invalid-ch (sync-single-node invalid-xpath)
                  valid-ch (sync-single-node success-xpath)
                  image-still-exists-ch (sync-single-node image-still-exists-xpath)
                  page-still-exists-ch (sync-single-node page-still-exists-xpath)

                  [result-modal-node win-ch] (alts! [invalid-ch valid-ch image-still-exists-ch page-still-exists-ch])
                  _ (async/close! invalid-ch)
                  _ (async/close! valid-ch)
                  _ (async/close! image-still-exists-ch)
                  _ (async/close! page-still-exists-ch)
                  _ (prn ">> text: " (dommy/text result-modal-node)) ;;xxx
                  ]
              (cond (= (dommy/text result-modal-node) "Invalid page URL")
                    (do
                      (prn "Encountered an invalid page url. About to click on cancel")
                      (-> "//div[@role='alertdialog']//span[contains(text(), 'Cancel')]" xpath single-node .click)
                      (<! (sync-disappeared-single-node invalid-xpath))
                      (>! ch :invalid-page-url))
                    (= (dommy/text result-modal-node) "The image URL still exists")
                    (do
                      (prn "Encountered 'The image URL still exists'. About to click on 'Submit Request'")
                      (-> "//div[@role='dialog']//span[contains(text(), 'Submit request')]" xpath single-node .click)
                      (<! (sync-disappeared-single-node image-still-exists-xpath))
                      (<! (sync-single-node success-xpath))
                      (-> "//div[@role='alertdialog']//span[contains(text(), 'Ok')]" xpath single-node .click)
                      (<! (sync-disappeared-single-node success-xpath))
                      (>! ch :success)
                      )
                    (= (dommy/text result-modal-node) "This page still exist")
                    (do
                      (prn "Encountered This page still exist")
                      (-> "//span[contains(text(), 'Enter word')]/../../../input" xpath single-node .click)
                      (domina/set-value! (-> "//span[contains(text(), 'Enter word')]/../../../input" xpath single-node) supplementary-arg)
                      ;; re-enable the submit button. Not sure why it remains disabled.
                      (-> "//div[@role='dialog']//span[contains(text(), 'Submit request')]/.." xpath single-node .-disabled (set! false))
                      (-> "//div[@role='dialog']//span[contains(text(), 'Submit request')]" xpath single-node .click)
                      (<! (sync-disappeared-single-node page-still-exists-xpath))
                      (<! (sync-single-node success-xpath))
                      (-> "//div[@role='alertdialog']//span[contains(text(), 'Ok')]" xpath single-node .click)
                      (<! (sync-disappeared-single-node success-xpath))
                      (>! ch :success)
                      )
                    :else
                    (do
                      (prn "Success..click on OK")
                      (-> "//div[@role='alertdialog']//span[contains(text(), 'Ok')]" xpath single-node .click)
                      (<! (sync-disappeared-single-node success-xpath))
                      (>! ch :success)
                      ))
              ))
          )
      ch)))

; -- a message loop ---------------------------------------------------------------------------------------------------------

(defn process-message! [chan message]
  (let [_ (log "CONTENT SCRIPT: got message:" message)
        {:keys [type victim supplementary-arg] :as whole-msg} (common/unmarshall message)]
    (cond (= type :done-init-victims) (go
                                        (<! (cleanup-xhr-data!))
                                        (post-message! chan (common/marshall {:type :next-victim})))
          (= type :remove-url) (when-not (or (nil? victim) (= victim "poison-pill"))
                                 (go
                                   (let [request-status (<! (exec-removal-request-v2 victim supplementary-arg))]
                                     ;; encounter an error go to the next victim
                                     (if (= :success request-status)
                                       (post-message! chan (common/marshall {:type :success
                                                                             :url victim}))
                                       (post-message! chan (common/marshall {:type :skip-error
                                                                             :reason request-status
                                                                             :url victim}))
                                       )
                                     )))
          (= type :reload) (do (prn "reloading..")
                               #_(.reload js/location)
                               ;; TODO don't reload. skip to the next victim
                               )
          (= type :done ) (do (js/alert "All done!")
                              (.reload js/location))
          )))

(defn ensure-english-setting []
  (let [url-parts (url (.. js/window -location -href))]
    (when-not (= "en" (get-in url-parts [:query "hl"]))
      (js/alert "Bulk URL Removal extension works properly only in English. Press OK to set the language to English.")
      (set! (.. js/window -location -href) (str (assoc-in url-parts [:query "hl"] "en")))
      )))

; -- main entry point -------------------------------------------------------------------------------------------------------

(defn init! []
  (let [_ (log "CONTENT SCRIPT: init")
        background-port (runtime/connect)]
    (go
      (ensure-english-setting)
      (common/connect-to-background-page! background-port process-message!))
    ))
