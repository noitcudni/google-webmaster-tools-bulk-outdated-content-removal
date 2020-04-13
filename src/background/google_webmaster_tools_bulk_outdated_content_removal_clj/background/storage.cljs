(ns google-webmaster-tools-bulk-outdated-content-removal-clj.background.storage
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [cljs.core.async :refer [<! >! chan close!]]
            [cljs-time.core :as t]
            [cljs-time.coerce :as tc]
            [chromex.logging :refer-macros [log info warn error group group-end]]
            [chromex.protocols.chrome-storage-area :as storage-area]
            [chromex.ext.storage :as storage]))

(def ^:dynamic *DONE-FLAG* "**D0N3-FL@G**")

(defn store-victims!
  [{:keys [data]}]
  (let [local-storage (storage/get-local)
        data (concat data [["poison-pill" *DONE-FLAG*]])]
    (go-loop [[[url supplementary-arg :as curr] & more] data
              idx 0]
      (if (nil? curr)
        (log "DONE storing victims")
        (let [[_ err] (<! (storage-area/get local-storage url))
              _ (prn "storing - url: " url) ;;xxx
              ]
          (if err
            (error (str "fetching " url ":") err)
            (storage-area/set local-storage (clj->js {url {"submit-ts" (tc/to-long (t/now))
                                                           "remove-ts" nil
                                                           "supplementary-arg" supplementary-arg
                                                           "status" "pending"
                                                           "idx" idx}
                                                      })))
          (recur more (inc idx))
          )))))

(defn update-storage [url & args]
  {:pre [(even? (count args))]}
  (let [kv-pairs (partition 2 args)
        local-storage (storage/get-local)
        ch (chan)]
    (go
      (let [[[items] error] (<! (storage-area/get local-storage url))]
        (if error
          (error (str "fetching " url ":") error)
          (let [entry (->> (js->clj items) vals first)
                r {url (->> kv-pairs
                            (reduce (fn [accum [k v]]
                                      (assoc accum k v))
                                    entry))
                   }]
            (storage-area/set local-storage (clj->js r))
            (>! ch r)
            ))))
    ch))

(defn current-removal-attempt
  "NOTE: There should only be one item that's undergoing removal.
  Return nil if not found.
  Return URL if found.
  "
  []
  (let [local-storage (storage/get-local)]
    (go
      (let [[[items] error] (<! (storage-area/get local-storage))]
        (->> items
             js->clj
             (filter (fn [[k v]]
                       (= "removing" (get v "status"))))
             first)
        ))
    ))

(defn fresh-new-victim []
  (let [local-storage (storage/get-local)
        ch (chan)]
    (go
      (let [[[items] error] (<! (storage-area/get local-storage))
            [victim-url victim-entry] (->> (or items '())
                                           js->clj
                                           (filter (fn [[k v]]
                                                     (let [status (get v "status")]
                                                       (= "pending" status))))
                                           (sort-by (fn [[_ v]] (get v "idx")))
                                           first)
            _ (when-not (nil? victim-entry) (<! (update-storage victim-url "status" "removing")))
            victim (<! (current-removal-attempt))]
        (if (nil? victim)
          (close! ch)
          (>! ch victim))
        ))
    ch))

(defn next-victim []
  (let [;;local-storage (storage/get-local)
        ch (chan)]
    (go
      (let [victim (<! (current-removal-attempt))
            _ (prn "victim from current-removal-attempt: " victim) ;;xxx
            victim (if (empty? victim)
                     (<! (fresh-new-victim))
                     victim)
            _ (prn "storage: next-victim: " victim) ;;xxx
            ]
        ;; TODO: maybe consider closing the channel
        (if (nil? victim)
          (close! ch)
          (>! ch victim))
        ))
    ch))

(defn clear-victims! []
  (let [local-storage (storage/get-local)]
    (storage-area/clear local-storage)))

(defn print-victims []
  (let [local-storage (storage/get-local)]
    (go
      (let [[[items] error] (<! (storage-area/get local-storage))]
        (prn (js->clj items))
        ))
    ))

(defn get-bad-victims []
  (let [local-storage (storage/get-local)
        ch (chan)]
    (go
      (let [[[items] error] (<! (storage-area/get local-storage))]
        (>! ch (->> (or items '())
                    js->clj
                    (filter (fn [[k v]]
                              (let [status (get v "status")]
                                (= "error" status))))
                    (sort-by (fn [[_ v]] (get v "idx")))
                    ))))
    ch))
