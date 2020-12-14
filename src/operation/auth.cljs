(ns operation.auth
  (:require
    [cljs.core.async :as async :refer [<! >! chan put!]]
    ["fs" :as fs]
    ["googleapis" :refer [google]]
    ["readline" :as readline])
  (:require-macros
    [cljs.core.async.macros :refer [go]]))


(set! *warn-on-infer* false)
(def SCOPES ["https://www.googleapis.com/auth/spreadsheets"])
(def TOKEN_PATH "token.json")


(defn js->clj-key
  [js-obj]
  (js->clj js-obj :keywordize-keys true))


(defn- read-file
  [in-ch out-ch ex-ch]
  (go (let [file (<! in-ch)]
        (.readFile fs
                   file
                   (fn [err content]
                     (if (nil? err)
                       (put! out-ch content)
                       (put! ex-ch err)))))))


(defn- write-file
  [file-name content]
  (.writeFile fs file-name content, (fn [err]
                                      (if (nil? err)
                                        (println (str "Token stored to " TOKEN_PATH))
                                        (.error js/console err)))))


(defn- set-token
  [auth-client-ch token-ch output-ch]
  (go (let [authenticated-client (<! auth-client-ch)
            token (<! token-ch)]
        (.setCredentials authenticated-client token)
        (>! output-ch authenticated-client))))


(defn- make-new-token
  [auth-ch passcode-ch token-ch]
  (go (let [authClient (<! auth-ch)
            passcode (<! passcode-ch)]
        (.getToken authClient passcode (fn [err token]
                                         (write-file TOKEN_PATH (.stringify js/JSON token))
                                         (put! token-ch (.stringify js/JSON token)))))))


(defn- ask-passcode
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


(defn- make-auth-client
  [credential]
  (let [{:keys [client_secret client_id redirect_uris]} (:installed credential)
        authenticated-client (new (.-OAuth2 (.-auth google))
                                  client_id
                                  client_secret
                                  (nth redirect_uris 0))]
    authenticated-client))


(defn generate-url
  [authClient]
  (.generateAuthUrl authClient (clj->js {:access_type "offline" :scope SCOPES})))


(defn authorize
  [c t]
  (let [credential-path (chan)
        credential (chan 1 (map (comp make-auth-client js->clj-key (.-parse js/JSON))))
        auth-url (chan 1 (map generate-url))
        req-auth (chan)
        token-path (chan)
        req-token (chan)
        tmp (chan)
        token (chan 1 (map (.-parse js/JSON)))
        passcode (chan)
        authed-client (chan)
        m-auth (async/mult credential)
        exception (chan)]

    (async/tap m-auth auth-url)
    (async/tap m-auth req-auth)
    (async/tap m-auth tmp)

    (read-file credential-path credential exception)
    (read-file token-path token req-token)

    (ask-passcode req-token auth-url passcode)
    (make-new-token tmp passcode token)

    (set-token req-auth token authed-client)

    (go (>! credential-path c))
    (go (>! token-path t))

    authed-client))
