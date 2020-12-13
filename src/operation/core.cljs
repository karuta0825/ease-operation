(ns operation.core
  (:require
    [cljs.core.async :as async :refer [chan put!]]
    ["command-line-args" :as cla]
    [operation.usecase :as srv]))


(def option-definitions
  [{:name "method" :alias "m" :type js/String :defaultValue "get"}
   {:name "date" :alias "d" :type js/String}
   {:name "id" :alias "i" :type js/String}])


(defn main
  []
  (let [cmd (js->clj (cla (clj->js option-definitions)) :keywordize-keys true)
        process (chan)
        req-get (chan)
        req-update (chan)
        pub (async/pub process :method)]

    (async/sub pub "get" req-get)
    (async/sub pub "update" req-update)

    (srv/show-spread-sheet req-get)
    (srv/update-spread-sheet req-update)

    (put! process cmd)))

