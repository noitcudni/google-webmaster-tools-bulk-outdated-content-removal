(ns google-webmaster-tools-bulk-outdated-content-removal-clj.background
  (:require-macros [chromex.support :refer [runonce]])
  (:require [google-webmaster-tools-bulk-outdated-content-removal-clj.background.core :as core]))

(runonce
  (core/init!))
