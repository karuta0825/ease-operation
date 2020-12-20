(ns operation.usecase
  (:require
    [cljs.core.async :as async :refer [<! >! chan put!]]
    [clojure.string :refer [join]]
    [operation.auth :as auth]
    [operation.sheet :as s]
    [operation.util :as ut])
  (:require-macros
    [cljs.core.async.macros :refer [go]]))


(def token-path "token.json")
(def credential-path "credentials.json")
(def offset-row 2)
(def start-column "A")
(def end-column "F")
(def sheet-name "request")
(def spread-sheet-id "1chKCceg5UejYfEEZ7KSGIlYcb9-EjHWF0dbEMuEaie4")


(defn- format-org
  [row]
  (let [no (nth row 0)
        date (nth row 1)
        group (nth row 2)
        name (nth row 3)
        content (nth row 4)]
    (str
      "** TODO 依頼" no "
   :PROPERTIES:
   :type:  operation
   :id:    " no "
   :date:  " date "
   :group: " group "
   :name:  " name "
   :END:

   [内容]
   " content "
   ")))


(defn- make-update-params
  [ssId sheet row column value]
  {:spreadsheetId ssId
   :range (str sheet "!" start-column row)
   :valueInputOption "USER_ENTERED"
   :resource {:values [(conj (into [] (take (dec column) (repeat nil))) value)]}})


(defn- search-row
  "検索した行を取得する"
  [id sheet-values]
  (->> sheet-values
       (map-indexed vector)
       (filter (fn [[idx row]] (= (nth row 0 nil) (str id))))
       (map (fn [v] {:row (first v) :values (second v)}))))


(defn show-spread-sheet
  [req]
  (go (let [_ (<! req)
            params {:spreadsheetId spread-sheet-id :range (str sheet-name "!" start-column offset-row ":" end-column)}
            oAuthClient (<! (auth/authorize credential-path token-path))
            data (<! (s/get-sheet oAuthClient params))]

        (->> data
             (filter (fn [v] (< (count v) 6)))
             (mapv (comp println format-org))))))


(defn update-spread-sheet
  [req]
  (go (let [{no :id value :date} (<! req)
            params {:spreadsheetId spread-sheet-id :range (str sheet-name "!" start-column offset-row ":" end-column)}
            oAuthClient (<! (auth/authorize credential-path token-path))
            data (<! (s/get-sheet oAuthClient params))
            update-rows (search-row no data)
            target-row (first update-rows)
            target-column 6]


        (if (not= (count update-rows) 1)
          (println (str "更新行が" (count update-rows) "個であるため失敗しました"))
          (do
            (ut/write-log "log" (str (join "," (:values target-row)) "\n"))
            (s/update-sheet oAuthClient (make-update-params
                                          spread-sheet-id
                                          sheet-name
                                          (+ offset-row (:row target-row))
                                          target-column
                                          value)))))))

