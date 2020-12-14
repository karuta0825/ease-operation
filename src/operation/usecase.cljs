(ns operation.usecase
  (:require
    [cljs.core.async :as async :refer [<! >! chan put!]]
    [operation.auth :as auth]
    [operation.sheet :as s])
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
  [id sheet-values]
  (->> sheet-values
       (map-indexed vector)
       (filter (fn [[idx row]] (= (nth row 0) (str id))))
       (map first)
       (first)))


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
            target-row (+ offset-row (search-row no (<! (s/get-sheet oAuthClient params))))
            target-column 6]
        (s/update-sheet oAuthClient (make-update-params spread-sheet-id sheet-name target-row target-column value)))))
