(ns operation.core
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require ["googleapis" :refer [google]])
  (:require ["fs" :as fs])
  (:require ["readline" :as readline])
  (:require [cljs.core.async :refer [<! chan >! put! take! timeout] :as async]))

(def SCOPES ["https://www.googleapis.com/auth/spreadsheets"])

(def TOKEN_PATH "token.json")

(def SHEET {:spreadsheetId "1XRKKdU4rTlK4Tiz-HPHp8_CAgBdbskh54s0_QoJv1Xs"
            :range "tasks!A1:E13"})


(defn js->clj-key [js-obj]
  (js->clj js-obj :keywordize-keys true))

(defn read-file [file out-chan]
  (.readFile fs
             file
             (fn [err content]
               (if (nil? err)
                 (go (>! out-chan content))
                 (go (>! out-chan "err")))))
  out-chan)

(defn write-file [file-name content]
  (.writeFile fs file-name content, (fn [err]
                                      (if (nil? err)
                                        (println (str "Token stored to " TOKEN_PATH))
                                        (.error js/console err)))))

(defn authorize [credentail-chan token-chan]
  (let [ch (chan)]
    (go (let [credential (<! credentail-chan)
              token (<! token-chan)
              {:keys [client_secret client_id redirect_uris]} (:installed credential)
              oAuthClient (new (.-OAuth2 (.-auth google)) client_id client_secret (nth redirect_uris 0))]

          (.setCredentials oAuthClient token)

          (>! ch oAuthClient)))
    ch))

(defn mk-auth-client [credentail]
  (let [{:keys [client_secret client_id redirect_uris]} (:installed credential)
        oAuthClient (new (.-OAuth2 (.-auth google)) client_id client_secret (nth redirect_uris 0))]
    oAuthClient))

;(defn )
; in: oAuthcClient
; out: authedClient

(defn get-token [authClient file]
  (let [content (read-file file (chan))]
    (go (if (= (<! content) "err")
       (get-new-token authClient (read-cmdline authClient))
       content))))

(defn read-cmdline [authClient]
  (let [ch (chan)
        auth-url (.generateAuthUrl authClient (clj->js {:access_type "offline" :scope SCOPES}))
        rl (.createInterface readline (clj->js {:input (.-stdin js/process)
                                                :output (.-stdout js/process)}))]
    (println (str "Authorize this app by visiting this url:"))
    (.question rl "Enter the code from that page here: "
               (fn [code]
                 (.close rl)
                 (put! ch code)))
    ch))

(defn get-new-token [authClient cmd-chan]
  (let [ch (chan)]
    (go (let [cmd (<! cmd-chan)]
          (.getToken authClient cmd (fn [err token]
                                      (put! ch token)
                                      (write-file TOKEN_PATH token)))))
    ch))

(defn get-sheet-value [auth-chan params]
  (let [ch (chan)]
    (go (let [auth (<! auth-chan)
              args (clj->js {:version "v4" :auth auth})
              sheets (.sheets google args)]

          ; .-keyはないもしない .keyでvalueの評価もする
          (.get (.-values (.-spreadsheets sheets))
                (clj->js params)
                (fn [err content]
                  (if (nil? err)
                    (put! ch (js->clj content :keywordize-keys true))
                    (println err))))
          ))
    ch))

(defn update-sheet-value [auth-chan params]
  (let [ch (chan)]
    (go (let [args (clj->js {:version "v4" :auth (<! auth-chan)})
              sheets (.sheets google args)]

          (.update (.-values (.-spreadsheets sheets))
                (clj->js params)
                (fn [res err]
                  (println err)
                  (println res)))))
    ch))


(defn output-chan [in-ch]
  (go (let [res (<! in-ch)
            values (get-in res [:data :values])]
        (mapv (fn [row] (println row)) values))))

(defn main []
  (go (let [cred-ch (read-file "credentials.json" (chan 1 (map (comp js->clj-key (.-parse js/JSON)))))
            token-ch (read-file "token.json" (chan 1 (map (.-parse js/JSON))))
            auth-chan (authorize cred-ch token-ch)
            ]

       (output-chan (get-sheet-value auth-chan SHEET))
;       (println (<! (read-cmdline)))
;        (println (<! auth-chan))
    ))
)

;; (go (let [token-ch (read-file "a" (chan))]
;;       (println (<! token-ch))))

;; (let [input-chan (chan)
;;       our-pub (async/pub input-chan :msg-type)
;;       output-chan (chan)]

;;   (async/sub our-pub :greeting output-chan)

;;   (go-loop []
;;     (let [{:keys [text]} (<! output-chan)]
;;       (println text)
;;       (recur)))

;;   (put! input-chan {:msg-type :greeting :text "hi"}))


;; (let [ch (chan)
;;       new-ch (chan)
;;       up-ch (chan)
;;       de-ch (chan)
;;       ; どのkeyで分岐するかきめる
;;       pub (async/pub ch :msg-type)]

;;   ; pub-chanのkeyのどの値のときにどこのチャンネルに送るのかをきめる
;;   (async/sub pub :new new-ch)
;;   (async/sub pub :upd up-ch)
;;   (async/sub pub :del de-ch)

;;   (go (println (<! new-ch)))
;;   (go (println (<! up-ch)))
;;   (go (println (<! de-ch)))

;;   ;; (go-loop []
;;   ;;   (let [n (<! new-ch)]
;;   ;;     (println (str "n" n))
;;   ;;     (recur)))

;;   ;; (go-loop []
;;   ;;   (let [u (<! up-ch)]
;;   ;;     (println (str "u" u))
;;   ;;     (recur)))

;;   ;; (go-loop []
;;   ;;   (let [u (<! de-ch)]
;;   ;;     (println (str "u" u))
;;   ;;     (recur)))

;;   (put! ch {:msg-type :del :text "hiiiiiiiiiiiiiiiiiii"})
;; )
