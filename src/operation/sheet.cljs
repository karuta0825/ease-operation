(ns operation.sheet
  (:require
    [cljs.core.async :as async :refer [<! >! chan put!]]
    [clojure.string :as str]
    ["googleapis" :refer [google]])
  (:require-macros
    [cljs.core.async.macros :refer [go]]))


(def alphabet
  (map #((comp str/upper-case char) %) (range 97 (+ 97 26))))


(defn twenty-six-base
  "A-Zの26進数をえる"
  [n]
  (loop [num (quot n 26)
         result (list (rem n 26))]
    (if (not= num 0)
      (recur (quot num 26) (conj result (dec (rem num 26))))
      result)))


(defn num->column
  "列番号からエクセルの列名を取得する"
  [num]
  (str/join (map #(nth alphabet %) (twenty-six-base num))))


(defn column->num
  "エクセルの列名から列番号を取得する"
  [name])


(defn char->num
  [char]
  (->> (map-indexed vector alphabet)
       (filter (fn [[i c]] (= c char)))
       (first)
       (first)))


;; (->> (seq "AC")
;;      (map char->num)
;;      (reverse)
;;      (map-indexed vector)
;; ;     (map (fn [[base value]] (* value (Math/pow 26 base))))
;;      )


(defn get-range
  "エクセル表記のrangeを得る"
  ([start-column start-row end-column]
   (let [sc (num->column start-column)
         ec (num->column end-column)]
     (str sc start-row ":" ec)))
  ([start-column start-row end-column end-row]
   (let [sc (num->column start-column)
         ec (num->column end-column)]
     (str sc start-row ":" ec end-row))))


(defn update-sheet
  [oAuthClient params]
  (go (let [args (clj->js {:version "v4" :auth oAuthClient})
            sheets (.sheets google args)]

        (.update (.-values (.-spreadsheets sheets))
                 (clj->js params)
                 (fn [res err]
                   (println err)
                   (println res))))))


(defn get-sheet
  [oAuthClient params]
  (let [ch (chan)]
    (go (let [args (clj->js {:version "v4" :auth oAuthClient})
              sheets (.sheets google args)]
          (.get (.-values (.-spreadsheets sheets))
                (clj->js params)
                (fn [err content]
                  (if (nil? err)
                    (put! ch (get-in (js->clj content :keywordize-keys true) [:data :values]))
                    (println err))))))
    ch))
