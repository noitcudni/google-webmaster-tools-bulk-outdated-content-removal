(ns google-webmaster-tools-bulk-outdated-content-removal-clj.background.storage
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [cljs.core.async :refer [<! >! chan]]
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
      (let [[_ err] (<! (storage-area/get local-storage url))]
        (if err
          (error (str "fetching " url ":") err)
          (storage-area/set local-storage (clj->js {url {"submit-ts" (tc/to-long (t/now))
                                                         "remove-ts" nil
                                                         "supplementary-arg" supplementary-arg
                                                         "idx" idx}
                                                    })))
        (recur more (inc idx))
        ))))


(defn print-victims []
  (let [local-storage (storage/get-local)]
    (go
      (let [[[items] error] (<! (storage-area/get local-storage))]
        (prn (js->clj items))
        ))
    ))
