(ns google-webmaster-tools-bulk-outdated-content-removal-clj.content-script
  (:require-macros [chromex.support :refer [runonce]])
  (:require [google-webmaster-tools-bulk-outdated-content-removal-clj.content-script.core :as core]))

(runonce
  (core/init!))
