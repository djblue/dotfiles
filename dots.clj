(ns dots.core
  (:require [clojure.tools.cli :refer [parse-opts]]
            [clojure.tools.nrepl.server :as nrepl]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.core.async :as a]
            [hawk.core :as hawk]
            [org.httpkit.server :as http]
            [digest :refer [sha-1]])
  (:import (java.util Base64)
           (java.io StringReader ByteArrayOutputStream)
           (org.apache.batik.transcoder TranscoderInput TranscoderOutput)
           (org.apache.batik.transcoder.image PNGTranscoder)))

(def db
  {:config/restarts
   {".xmonad/xmonad.hs"
    [:do
     [:xmonad "--recompile"]
     [:xmonad "--restart"]
     [:sleep 0.1]]
    ".xmobarrc"
    [:do
     [:xmonad "--restart"]
     [:sleep 0.1]]
    ".Xdefaults"
    [:xrdb "-merge" "$HOME/.Xdefaults"]
    ".vimrc"
    [:pipe
     [:echo]
     [:redirect
      [:vim "+PlugInstall" "+qall"]
      "/dev/null"]]
    ".wallpaper/arch.png"
    [:feh "--bg-fill" "$HOME/.wallpaper/arch.png"]}
   :config/profiles
   {:default
    {:theme/dpi 96
     :theme/font-size 18
     :system/has-sound? true
     :theme/font-name "Inconsolata"
     :theme/font-alt "Inconsolata for Powerline"}
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

(defn get-config [db theme profiles]
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

(def svg->png
  (memoize
   (fn [svg]
     (with-open [out (ByteArrayOutputStream.)]
       (.transcode (PNGTranscoder.)
                   (TranscoderInput. (StringReader. svg))
                   (TranscoderOutput. out))
       (.toByteArray out)))))

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

(defn git-clone [url path]
  [:if [:not [:dir path]] [:git "clone" url path]])

(defn encode [s]
  (.encodeToString (Base64/getEncoder) (if (string? s) (.getBytes s) s)))

(def cwd (.toPath (.getAbsoluteFile (io/file "src"))))

(defn rename [file]
  (str "." (.toString (.relativize cwd (.toPath (.getAbsoluteFile file))))))

(defn install-file [config file]
  (let [contents (render-string config (slurp file))
        contents (if (re-matches #".*\.svg$" (.getName file))
                   (svg->png contents)
                   contents)
        sha-1 (sha-1 contents)
        path  (rename file)
        path (str/replace path #"\.svg$" ".png")
        restarts (get-in db [:config/restarts path])
        path (str "$HOME/" path)
        exec? (.canExecute file)]
    [:do
     [:mkdir "-p" [:eval [:dirname path]]]
     [:touch path]
     [:if [:not
           [:equals [:eval
                     [:pipe
                      [:sha1sum path]
                      [:cut "-d" " " "-f" 1]]] sha-1]]
      [:do
       [:redirect [:pipe
                   [:echo "-n" (encode contents)]
                   [:base64 "--decode"]] path]
       (if exec? [:chmod "+x" path])
       [:echo "  -> wrote" path]
       (if restarts
         [:do
          [:echo "  -> performing restarts"]
          restarts])]
      [:echo "  -> skipping" path]]]))

(defn install-host [files machine]
  (let [{:keys [host config/theme config/profiles]} machine
        config (get-config db theme profiles)
        install-files (map #(install-file config %) files)]
    [:if [:equals [:eval [:hostname]] (name host)]
     [:do
      [:echo (str "==> detected " (name host))]
      (cons :do install-files)]]))

(defn dots-script [files]
  [:do
   [:set "-e"]
   (git-clone
    "https://github.com/VundleVim/Vundle.vim.git"
    "$HOME/.vim/bundle/Vundle.vim")
   (cons :do (map #(install-host files %) machines))
   [:echo "==> successfully installed dotfiles"]])

(defn hoist
  ([script]
   (let [{:keys [vars script]} (hoist script {})]
     [:do
      (cons :do (map (fn [[v k]] [:def k v]) vars))
      script]))
  ([script vars]
   (cond
     (and (string? script) (> (count script) 100))
     (if-let [var (get vars script)]
       {:vars vars :script [:ref var]}
       (let [var (gensym)]
         {:vars (assoc vars script var) :script [:ref var]}))
     (or (vector? script) (seq? script))
     (reduce
      #(merge-with conj %1 (hoist %2 (:vars %1)))
      {:vars vars :script [(first script)]}
      (rest script))
     :else {:vars vars :script script})))

(defn bash [script]
  (cond
    (string? script) (str "\"" script "\"")
    (or (vector? script) (seq? script))
    (let [[op & args] script
          args (map bash (filter some? args))
          [arg1 arg2 arg3] args]
      (case op
        :do         (str/join "\n" args)
        :if         (str "if [[ " arg1 " ]]; then\n"
                         arg2
                         (if arg3 (str "\nelse\n" arg3))
                         "\nfi")
        :not        (str "! " arg1)
        :dir        (str "-d " arg1)
        :eval       (str "$(" arg1 ")")
        :pipe       (str/join " | " args)
        :equals     (str arg1 " == " arg2)
        :redirect   (str arg1 " > " arg2)
        :def        (str arg1 "=" arg2)
        :ref        (str "$" arg1)
        (str (name op) " " (str/join " " args))))
    :else script))

(defn exit [code & msg]
  (apply println msg)
  (System/exit code))

(defn get-sources []
  (->> "src/" io/file file-seq (filter #(.isFile %))))

(def chans (atom #{}))

(defn app [req]
  (if (= (:uri req) "/stream")
    (http/with-channel req ch
      (http/on-close ch (fn [_] (swap! chans disj ch)))
      (swap! chans conj ch))
    {:statue 200
     :headers {"Content-Type" "text/plain"}
     :body (bash (hoist (dots-script (get-sources))))}))

(defn edit-dots []
  (let [files (a/chan (a/dropping-buffer 1))
        runtime (Runtime/getRuntime)
        p (.start (.inheritIO (ProcessBuilder. ["vim" "dots.clj"])))]
    (hawk/watch! [{:paths ["src"]
                   :filter hawk/file?
                   :handler #(a/put! files (:file %2))}])
    (.addShutdownHook runtime (Thread. #(.destroy p)))
    (->> (nrepl/start-server) :port (spit ".nrepl-port"))
    (http/run-server #(app %) {:host "0.0.0.0" :port 8080})
    (a/go-loop []
      (let [file (a/<! files)]
        (a/<! (a/timeout 100))
        (a/take! files identity)
        (let [script (str (bash (dots-script [file])) "\n")]
          (doseq [ch @chans] (http/send! ch script false)))
        (recur)))
    (exit (.waitFor p))))

(def cli-options
  [["-s" "--script" "only output script"]
   ["-h" "--help" "output usage information"]])

(defn help [options]
  (->> ["Edit my dotfiles."
        ""
        "Usage: dots [options]"
        ""
        "Options:"
        ""
        options
        ""]
       (str/join \newline)))

(defn main [& args]
  (let [{:keys [options arguments errors summary]}
        (parse-opts args cli-options)]
    (cond
      (:help options)     (exit 0 (help summary))
      errors              (exit 1 (str (first errors) "\nSee \"dots --help\""))
      (:script options)   (print (bash (hoist (dots-script (get-sources)))))
      :else               (edit-dots))))

(apply main *command-line-args*)
