(ns google-webmaster-tools-bulk-outdated-content-removal-clj.popup
  (:require-macros [chromex.support :refer [runonce]])
  (:require [google-webmaster-tools-bulk-outdated-content-removal-clj.popup.core :as core]))

(runonce
  (core/init!))
