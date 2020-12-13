(ns operation.core
  (:require
    [cljs.core.async :as async :refer [<! >! chan put!]]
    ["fs" :as fs]
    ["googleapis" :refer [google]]
    ["readline" :as readline])
  (:require-macros
    [cljs.core.async.macros :refer [go]]))

;; jsオブジェクトのmethodがないかもしれないという警告を黙らせる
(set! *warn-on-infer* false)


(def SCOPES ["https://www.googleapis.com/auth/spreadsheets"])

(def TOKEN_PATH "token.json")
(def CREDENTIAL_PATH "credentials.json")


(def SHEET
  {:spreadsheetId "1chKCceg5UejYfEEZ7KSGIlYcb9-EjHWF0dbEMuEaie4"
   :range "request!A3:E"})


(defn ss-range
  [sheet range])


(def alphabet
  (map #((comp clojure.string/upper-case char) %) (range 97 (+ 97 26))))


(defn twenty-six-base
  [n]
  (loop [num n
         result ()]
    (if (not= num 0)
      (recur (quot num 26) (conj result (dec (mod num 26))))
      result)))


(defn get-column
  [num]
  (clojure.string/join (map #(nth alphabet %) (twenty-six-base num))))


(nth alphabet 25)


(map first (filter (fn [[idx num]] (even? num)) (map-indexed vector (range 10 20))))


(defn get-range
  ([start-column start-row end-column]
   (let [sc (get-column start-column)
         ec (get-column end-column)]
     (str sc start-row ":" ec)))
  ([start-column start-row end-column end-row]
   (let [sc (get-column start-column)
         ec (get-column end-column)]
     (str sc start-row ":" ec end-row))))


(get-range 1 10 10)


(defn js->clj-key
  [js-obj]
  (js->clj js-obj :keywordize-keys true))


(defn read-file
  [file out-chan]
  (.readFile fs
             file
             (fn [err content]
               (if (nil? err)
                 (go (>! out-chan content))
                 (put! out-chan "{\"error\": true}")))))


(defn write-file
  [file-name content]
  (.writeFile fs file-name content, (fn [err]
                                      (if (nil? err)
                                        (println (str "Token stored to " TOKEN_PATH))
                                        (.error js/console err)))))


(defn authorize
  [authenticated-client token-chan]
  (let [ch (chan)]
    (go (let [token (<! token-chan)]
          (.setCredentials authenticated-client token)
          (>! ch authenticated-client)))
    ch))


(defn mk-auth-client
  [credential]
  (let [{:keys [client_secret client_id redirect_uris]} (:installed credential)
        authenticated-client (new (.-OAuth2 (.-auth google))
                                  client_id
                                  client_secret
                                  (nth redirect_uris 0))]
    authenticated-client))
;

(defn generate-url
  [authClient]
  (.generateAuthUrl authClient (clj->js {:access_type "offline" :scope SCOPES})))


(defn read-cmdline
  [authClient]
  (let [ch (chan)
        auth-url (.generateAuthUrl authClient (clj->js {:access_type "offline" :scope SCOPES}))
        rl (.createInterface readline (clj->js {:input (.-stdin js/process)
                                                :output (.-stdout js/process)}))]
    (println (str "Authorize this app by visiting this url:" auth-url))
    (.question rl "Enter the code from that page here: "
               (fn [code]
                 (.close rl)
                 (println "OK?")
                 (put! ch code)))
    ch))


(defn token-file?
  [file]
  "token fileが存在するかどうか"
  (let [ch (chan)]
    (.stat fs file (fn [err stat] (put! ch (nil? err))))
    ch))


(defn get-new-token
  [authClient cmd]
  (let [ch (chan)]
    (.getToken authClient cmd (fn [err token]
                                (write-file TOKEN_PATH (.stringify js/JSON token))
                                (put! ch token)))
    ch))


(defn get-token
  [authClient file]
  (go (if (<! (token-file? file))
        (<! (read-file file (chan 1 (map (.-parse js/JSON)))))
        (<! (get-new-token authClient (<! (read-cmdline authClient)))))))


(defn get-sheet-value
  [auth-chan params]
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
                    (println err))))))
    ch))


(rem 10 10)


(defn output-chan
  [in-ch]
  (go (let [res (<! in-ch)
            values (get-in res [:data :values])]
        (mapv (fn [row] (prn row)) values))))


(defn main
  []
  ;; (go (let [cred (<! (read-file CREDENTIAL_PATH (chan 1 (map (comp js->clj-key (.-parse js/JSON))))))
  ;;           authClient (mk-auth-client cred)
  ;;           token-ch (get-token authClient TOKEN_PATH)
  ;;           auth-chan (authorize authClient token-ch)
  ;;           ]
  ;;       (output-chan (get-sheet-value auth-chan SHEET))
  ;;       ))
  )


(defn my-read-file
  [in-ch out-ch ex-ch]
  (go (let [file (<! in-ch)]
        (.readFile fs
                   file
                   (fn [err content]
                     (if (nil? err)
                       (put! out-ch content)
                       (put! ex-ch err)))))))


(defn make-auth-client
  [auth-client-ch token-ch output-ch]
  (go (let [authenticated-client (<! auth-client-ch)
            token (<! token-ch)]
        (.setCredentials authenticated-client token)
        (>! output-ch authenticated-client))))


(defn make-new-token
  [auth-ch passcode-ch token-ch]
  (go (let [authClient (<! auth-ch)
            passcode (<! passcode-ch)]
        (.getToken authClient passcode (fn [err token]
                                         (write-file TOKEN_PATH (.stringify js/JSON token))
                                         (put! token-ch (.stringify js/JSON token)))))))


(defn ask-passcode
  [req-token-ch auth-url-ch passcode-ch]
  (go (let [url (<! auth-url-ch)
            _ (<! req-token-ch)
            rl (.createInterface readline (clj->js {:input (.-stdin js/process)
                                                    :output (.-stdout js/process)}))]
        (println (str "Authorize this app by visiting this url:" url))
        (.question rl "Enter the code from that page here: "
                   (fn [code]
                     (.close rl)
                     (put! passcode-ch code))))))


(defn gain-sheet-value
  [auth-ch params output-ch]
  (go (let [args (clj->js {:version "v4" :auth (<! auth-ch)})
            sheets (.sheets google args)]
        (.get (.-values (.-spreadsheets sheets))
              (clj->js params)
              (fn [err content]
                (if (nil? err)
                  (put! output-ch (js->clj content :keywordize-keys true))
                  (println err)))))))


(defn search-update-row-idx
  [id sheet-values]
  (->> sheet-values
       (map-indexed vector)
       (filter (fn [[idx row]] (= (nth row 0) (str id))))
       (map (comp inc first))))


(defn update-sheet-value
  [auth-ch params output-ch]
  (go (let [args (clj->js {:version "v4" :auth (<! auth-ch)})
            sheets (.sheets google args)]

        (.update (.-values (.-spreadsheets sheets))
                 (clj->js params)
                 (fn [res err]
                   (println err)
                   (println res))))))


(defn my-string
  [row]
  (let [no (nth row 0)
        date (nth row 1)
        name (nth row 2)
        content (nth row 3)]
    (str
      "** TODO 依頼" no "
   :PROPERTIES:
   :id:    " no "
   :date:  " date "
   :name:   " name "
   :END:
   
   [内容]
   " content "
   ")))


(go (let [credential-path (chan)
          credential (chan 1 (map (comp mk-auth-client js->clj-key (.-parse js/JSON))))
          auth-url (chan 1 (map generate-url))
          req-auth (chan)
          token-path (chan)
          req-token (chan)
          tmp (chan)
          token (chan 1 (map (.-parse js/JSON)))
          passcode (chan)
          authed-client (chan)
          m-auth (async/mult credential)
          out-ch (chan)
          exception (chan)]

      (async/tap m-auth auth-url)
      (async/tap m-auth req-auth)
      (async/tap m-auth tmp)

      (my-read-file credential-path credential exception)
      (my-read-file token-path token req-token)
      (ask-passcode req-token auth-url passcode)
      (make-new-token tmp passcode token)
      (make-auth-client req-auth token authed-client)
      (gain-sheet-value authed-client SHEET out-ch)

      (go (>! credential-path CREDENTIAL_PATH))
      (go (>! token-path TOKEN_PATH))

      (->> (get-in (<! out-ch) [:data :values])
;           (drop 1)
 ;          (map #(my-string %))
           (search-update-row-idx 3)
           (mapv #(println %)))))


;; (let [service "ac"
;;       no 1
;;       grp "CS"
;;       name "nene"
;;       userId 356184
;;       priority "当日必須（決済）"
;;       deadline "2020/12/10"
;;       wfid "3150-26"
;;       background "test"]
;;   (println
;;     (str
;;       "** TODO " service " " no "
;;    :PROPERTIES:
;;    :id:       " no "
;;    :group:    " grp "
;;    :name:     " name "
;;    :userId:   " userId "
;;    :priority: " priority "
;;    :deadline: " deadline "
;;    :wfid:     " wfid "
;;    :Effort:   00:30
;;    :END:" "

;;    [背景]" background)))


