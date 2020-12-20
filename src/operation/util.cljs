(ns operation.util
  (:require
    [cljs.core.async :as async :refer [<! >! chan put!]]
    ["fs" :as fs])
  (:require-macros
    [cljs.core.async.macros :refer [go]]))


(def ^:private TOKEN_PATH "token.json")


(defn read-file
  [in-ch out-ch ex-ch]
  (go (let [file (<! in-ch)]
        (.readFile fs
                   file
                   (fn [err content]
                     (if (nil? err)
                       (put! out-ch content)
                       (put! ex-ch err)))))))


(defn write-file
  [file-name content]
  (.writeFile fs file-name content (fn [err]
                                     (if (nil? err)
                                       (println (str "Token stored to " TOKEN_PATH))
                                       (.error js/console err)))))


(defn write-log
  [file-name content]
  (.appendFile fs file-name content (fn [err]
                                      (when (nil? err)
                                        (.error js/console err)))))

