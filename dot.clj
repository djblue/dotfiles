;#!/usr/bin/env clojure
(ns dot.core
  (:require [clojure.tools.cli :refer [parse-opts]]
            [clojure.tools.nrepl.server :as nrepl]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [hawk.core :as hawk]
            [org.httpkit.server :as http])
  (:import java.util.Base64))

(def db
  {:config/restarts
   {".xmonad/xmonad.hs" ["xmonad --recompile;" "xmonad --restart;"]
    ".xmobarrc" ["xmonad --restart;"]
    ".Xdefaults" ["xrdb -merge ~/.Xdefaults;"]
    ".vimrc" ["echo | vim +PlugInstall +qall;"]}
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
  (->> profiles
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

(def cwd (.toPath (.getAbsoluteFile (io/file "src"))))

(defn rename [file]
  (str "." (.toString (.relativize cwd (.toPath (.getAbsoluteFile file))))))

(defn dot [config file]
  {:path (rename file)
   :contents (render-string config (slurp file))})

(defn encode [s]
  (.encodeToString (Base64/getEncoder) (.getBytes s)))

(defn escape [s]
  (str "\"$(/usr/bin/base64 -d <<< '" (encode s) "')\""))

(defn echo [& messages]
  (str "/usr/bin/echo " (str/join " " (map escape messages)) ";"))

(defn notify [title body]
  (str "/usr/bin/notify-send " (escape title) " " (escape body) ";"))

(defn write [path content]
  (str "/usr/bin/mkdir -p \"$(dirname " path ")\";"
       "/usr/bin/echo " (escape content) " > " path ";"
       "/usr/bin/echo wrote " path ";"))

(defn if-host [host code]
  (str "if [[ $(hostname) == " (name host) " ]]; then " code " fi"))

(defn clone [url path]
  (str
   "if [ ! -d " path " ]; then /usr/bin/git clone " url " " path "; fi"))

(def machines
  [{:host :red-machine
    :config/profiles [:default]
    :config/theme :nord}
   {:host :archy
    :config/profiles [:default]
    :config/theme :nord}
   {:host :osx
    :config/profiles [:default :laptop :hidpi]
    :config/theme :nord}])

(defn dots-script [files]
  (str
   "set -e;"
   (clone
    "https://github.com/VundleVim/Vundle.vim.git"
    "$HOME/.vim/bundle/Vundle.vim")
   \newline
   (->> machines
        (map (fn [{:keys [host config/theme config/profiles]}]
               [host (apply get-config (concat [db theme] profiles))]))
        (map (fn [[host config]]
               [host (->> files
                          (map (fn [file] (dot config file)))
                          (map (fn [{:keys [path contents]}]
                                 (str
                                  (write (str "$HOME/" path) contents)
                                  (str/join ""
                                            (get-in db [:config/restarts path] [])))))
                          (str/join \newline))]))
        (map (fn [[host script]]
               (if-host host (str (echo (str "==> detected " (name host)))
                                  script))))
        (str/join \newline))))

(defn exit [code & msg]
  (apply println msg)
  (System/exit code))

(defn get-sources []
  (->> "src/" io/file file-seq (filter #(.isFile %))))

(defn app [req]
  (println req)
  {:statue 200
   :headers {"Content-Type" "text/plain"}
   :body (dots-script (get-sources))})

(defn edit-dots []
  (hawk/watch! [{:paths ["src"]
                 :filter hawk/file?
                 :handler #(println (dots-script [(:file %2)]))}])
  (let [runtime (Runtime/getRuntime)
        p (.start (ProcessBuilder.
                   ["gvim" "-f" "/home/chris/repos/dotfiles/dot.clj"]))]
    (.addShutdownHook runtime (Thread. #(.destroy p)))
    (nrepl/start-server :port 7888)
    (http/run-server app {:host "0.0.0.0" :port 8080})
    (exit (.waitFor p))))

(defn cli-options [{:keys [config/themes config/profiles]}]
  [["-s" "--script" "only output script"]
   ["-h" "--help" "output usage information"]])

(defn help [options]
  (->> ["Edit my dotfiles."
        ""
        "Usage: dot [options]"
        ""
        "Options:"
        ""
        options
        ""]
       (str/join \newline)))

(defn main [& args]
  (let [{:keys [options arguments errors summary]}
        (parse-opts args (cli-options db))]
    (cond
      (:help options)     (exit 0 (help summary))
      errors (exit 1      (str (first errors) "\nSee \"dot --help\""))
      (:script options)   (print (dots-script (get-sources)))
      :else               (edit-dots))))

(apply main *command-line-args*)
; vim: set ft=clojure:
