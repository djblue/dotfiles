;#!/usr/bin/env clojure
; vim: set ft=clojure:
(ns dot.core
  (:require [clojure.tools.cli :refer [parse-opts]]
            [clojure.tools.nrepl.server :as nrepl]
            [clojure.java.shell :as shell]
            [clojure.pprint :as pp]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [hawk.core :as hawk]
            [org.httpkit.server :as http]
            [me.raynes.fs :as fs])
  (:import java.util.Base64))

(defn cli-options [{:keys [config/themes config/profiles]}]
  [["-c" "--color THEME" "the color theme to use"
    :default :nord
    :default-desc "nord"
    :parse-fn keyword
    :validate [#(% themes) (str "theme must be one of " (keys themes))]]
   ["-p" "--profiles PROFILE" "comma separated list of profiles to use"
    :parse-fn #(map keyword (str/split % #","))
    :validate [#(% profiles) (str "profile must be one of " (keys profiles))]]
   ["-s" "--script" "only output script"]
   ["-a" "--app" "serve dot files over http app"]
   ["-h" "--help" "output usage information"]])

(defn help [options {:keys [config/themes config/profiles]}]
  (->> ["Edit my dotfiles."
        ""
        "Usage: dot [options]"
        ""
        "Options:"
        ""
        options
        ""
        "Profiles:"
        ""
        (->> (keys profiles)
             (map name)
             (map #(str "  " %))
             (str/join \newline))
        ""
        "Themes:"
        ""
        (->> (map second themes)
             (map #(str "  " (:theme/name %) " - " (:theme/url %)))
             (str/join \newline))
        ""]
       (str/join \newline)))

(def db
  {:config/restarts
   {#{"xmonad.hs" "xmobarrc"}
    ["xmonad --recompile" "xmonad --restart"]
    #{"Xdefaults"}
    ["xrdb -merge ~/.Xdefaults"]
    #{"vimrc"}
    ["vim +PluginInstall +qall"]}
   :config/profiles
   {:default
    {:theme/dpi 96
     :theme/font-size 18
     :system/has-sound? true
     :theme/font-name "Inconsolata for Powerline"}
    :hidpi
    {:theme/dpi 196
     :theme/font-size 38}
    :laptop
    {:system/has-battery? true
     :system/has-wireless? true
     :system/has-backlight? true}}
   :config/themes
   {:nord
    {:theme/name "nord"
     :theme/url "https://arcticicestudio.github.io/nord"
     :theme/vim-plugin "arcticicestudio/nord-vim"
     :theme/type "dark"
     :theme/foreground "#d8dee9"
     :theme/background "#2e3440"
     :theme/color ["#3b4252"
                   "#bf616a"
                   "#a3be8c"
                   "#ebcb8b"
                   "#81a1c1"
                   "#b48ead"
                   "#88c0d0"
                   "#e5e9f0"
                   "#4c566a"
                   "#bf616a"
                   "#a3be8c"
                   "#ebcb8b"
                   "#81a1c1"
                   "#b48ead"
                   "#8fbcbb"
                   "#eceff4"]}
    :solarized-dark
    {:theme/name "solarized-dark"
     :theme/url "http://ethanschoonover.com/solarized"
     :theme/vim-plugin "altercation/vim-colors-solarized"
     :theme/type "dark"
     :theme/foreground "#839496"
     :theme/background "#002b36"
     :theme/color ["#073642"
                   "#dc322f"
                   "#859900"
                   "#b58900"
                   "#268bd2"
                   "#d33682"
                   "#2aa198"
                   "#eee8d5"
                   "#002b36"
                   "#cb4b16"
                   "#586e75"
                   "#657b83"
                   "#839496"
                   "#6c71c4"
                   "#93a1a1"
                   "#fdf6e3"]}}})

(defn get-config [db theme & profiles]
  (->> (cons :default profiles)
       (map #(get-in db [:config/profiles %]))
       (cons (get-in db [:config/themes theme]))
       (apply merge)))

(defn render-string [config template]
  (str/replace
   template
   #"(?sm)\{\{((?:(?!\}\}).)*)\}\}"
   (fn [[_ string]]
     (let [expr (read-string string)]
       (str
        (cond
          (keyword? expr) (expr config)
          (vector? expr)  (get-in config expr)
          (list? expr)    (eval
                           `(let [~'config ~config] ~expr))))))))

(defn rename [path]
  (let [[_ & relative] (fs/split path)
        [f & r] relative]
    (str/join "/" (cons (str "." f) r))))

(defn dot [config]
  (fn [file]
    (let [path (.getPath file)
          contents (slurp file)]
      {:path (rename path)
       :contents (render-string config contents)})))

(defn encode [s]
  (.encodeToString (Base64/getEncoder) (.getBytes s)))

(defn exec [script]
  (shell/sh "/usr/bin/bash" "-c" script))

(defn escape [s]
  (str "\"$(/usr/bin/echo '" (encode s) "' | /usr/bin/base64 -d)\""))

(defn echo [& messages]
  (str "/usr/bin/echo " (str/join " " (map escape messages)) ";"))

(defn notify [title body]
  (str "/usr/bin/notify-send " (escape title) " " (escape body) ";"))

(defn write [path content]
  (str "/usr/bin/mkdir -p \"$(dirname " path ")\";"
       "/usr/bin/echo " (escape content) " > " path ";"
       "/usr/bin/echo wrote " path ";"))

(defn clone [url path]
  (str
   "if [ ! -d " path " ]; then /usr/bin/git clone " url " " path "; fi;"))

(defn write-dot [file]
  (let [{:keys [path contents]} ((dot (get-config db :nord)) file)]
    (write (str "$HOME/" path) contents)))

(defn emit [file]
  (println (write-dot file)))

(defn dots-script []
  (str
   "set -e;"
   (clone
    "https://github.com/VundleVim/Vundle.vim.git"
    "$HOME/.vim/bundle/Vundle.vim")
   (with-out-str
     (->> (file-seq (io/file "src/"))
          (filter #(.isFile %))
          (map emit)
          doall))))

(defn exit [code & msg]
  (apply println msg)
  (System/exit code))

(defn edit-dots []
  (hawk/watch! [{:paths ["src"]
                 :filter hawk/file?
                 :handler #(emit (:file %2))}])
  (let [runtime (Runtime/getRuntime)
        p (.start (ProcessBuilder. ["gvim" "-f" "/home/chris/repos/dotfiles/dot.clj"]))]
    (.addShutdownHook runtime (Thread. #(.destroy p)))
    (nrepl/start-server :port 7888)
    (exit (.waitFor p))))

(defn app [req]
  (println req)
  {:statue 200
   :headers {"Content-Type" "text/plain"}
   :body (dots-script)})

(defn main [& args]
  (let [{:keys [options arguments errors summary]}
        (parse-opts args (cli-options db))]
    (cond
      (:help options)     (exit 0 (help summary db))
      errors (exit 1      (str (first errors) "\nSee \"dot --help\""))
      (:script options)   (print (dots-script))
      (:app options)      (http/run-server app {:host "0.0.0.0" :port 8080})
      :else               (edit-dots))))

(apply main *command-line-args*)
