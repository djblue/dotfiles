(ns dots.core
  (:require [clojure.tools.cli :refer [parse-opts]]
            [clojure.tools.nrepl.server :as nrepl]
            [clojure.pprint :refer [pprint]]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.java.shell :refer [sh]]
            [clojure.test :refer [is deftest] :as t]
            [hawk.core :as hawk]
            [org.httpkit.server :as http]
            [digest :refer [sha-1]])
  (:import (java.util Base64)
           (java.net InetAddress)
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
    {:theme/name "solarized"
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
                   "#fdf6e3"]}
    :solarized-light
    {:theme/name "solarized"
     :theme/url "http://ethanschoonover.com/solarized"
     :theme/vim-plugin "altercation/vim-colors-solarized"
     :theme/type "light"
     :theme/foreground "#657b83"
     :theme/background "#fdf6e3"
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
                   "#fdf6e3"]}
    :monokai
    {:theme/name "monokai"
     :theme/vim-plugin "crusoexia/vim-monokai"
     :theme/type "dark"
     :theme/foreground "#cccccc"
     :theme/background "#1b1d1e"
     :theme/color ["#1b1d1e"
                   "#ff0044"
                   "#82b414"
                   "#fd971f"
                   "#266c98"
                   "#ac0cb1"
                   "#ae81ff"
                   "#cccccc"
                   "#808080"
                   "#f92672"
                   "#a6e22e"
                   "#e6db74"
                   "#7070f0"
                   "#d63ae1"
                   "#66d9ef"
                   "#f8f8f2"]}}})

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
  {:red-machine
   {:config/profiles [:default]
    :config/theme :nord}
   :archy
   {:config/profiles [:default]
    :config/theme :nord}
   :osx
   {:config/profiles [:default :laptop :hidpi]
    :config/theme :nord}
   :badahdah
   {:config/profiles [:default]
    :config/theme :nord
    :editor ["mvim" "-f"]}})

(defn git-clone [url path]
  [:if [:not [:dir path]] [:git "clone" url path]])

(defn encode [s]
  (.encodeToString (Base64/getEncoder) (if (string? s) (.getBytes s) s)))

(def cwd (.toPath (.getAbsoluteFile (io/file "src"))))

(defn rename [file]
  (str "." (.toString (.relativize cwd (.toPath (.getAbsoluteFile file))))))

(defn echo [path value]
  [:do
   [:pipe
    [:echo "-n" (encode (str path))]
    [:base64 "--decode"]]
   (cond
     (string? value) [:do
                      [:echo "-n" " \\\""]
                      [:echo "-n" value]
                      [:echo "\\\""]]
     :else [:echo "" value])])

(defn parse [out]
  (->> (str "[" out "]")
       (read-string)
       (partition 2)
       (reduce
        (fn [acc [path value]] (assoc-in acc path value))
        {:dots/files []})))

(defn install-file [idx config file]
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
     (echo [:dots/files idx :file/path] path)
     (echo [:dots/files idx :file/sha-1] sha-1)
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
       (echo [:dots/files idx :file/exec?] exec?)
       (if restarts
         [:if [:not [:zero "$SKIP_RESTARTS"]]
          (echo [:dots/files idx :file/skip-restarts?] true)
          [:do
           (echo [:dots/files idx :file/skip-restarts?] false)
           restarts]])]
      (echo [:dots/files idx :file/skip-install?] true)]]))

(defn install-host [files [host machine]]
  (let [{:keys [config/theme config/profiles]} machine
        config (get-config db theme profiles)
        install-files (map-indexed (fn [idx itm]
                                     (install-file idx config itm)) files)]
    [(name host)
     [:do
      (cons :do install-files)]]))

(defn dots-script [files]
  [:do
   [:set "-e"]
   [:if [:zero "$HOME"]
    [:do
     (echo [:dots/status] :dots/unknown-home)
     [:exit 1]]]
   (git-clone
    "https://github.com/VundleVim/Vundle.vim.git"
    "$HOME/.vim/bundle/Vundle.vim")
   [:if [:zero "$HOST"]
    [:do
     [:def :HOST [:eval [:hostname]]]
     (echo [:system/host-set?] false)]
    (echo [:system/host-set?] true)]
   (echo [:system/host] "$HOST")
   (-> [:case "$HOST"]
       (into (mapcat #(install-host files %) machines))
       (conj [:do
              (echo [:dots/status] :dots/unknown-host)
              [:exit 1]]))
   (echo [:dots/status] :dots/success)])

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
        :case       (str "case " arg1 " in\n"
                         (->> (rest args)
                              (partition-all 2)
                              (map
                               (fn [[c e]]
                                 (if e
                                   (str c ")\n" e "\n;;")
                                   (str "*)\n" c))))
                              (str/join "\n"))
                         "\nesac")
        :not        (str "! " arg1)
        :dir        (str "-d " arg1)
        :zero       (str "-z " arg1)
        :eval       (str "$(" arg1 ")")
        :pipe       (str/join " | " args)
        :equals     (str arg1 " == " arg2)
        :redirect   (str arg1 " > " arg2)
        :def        (str (name arg1) "=" arg2)
        :ref        (str "$" arg1)
        (str (name op) " " (str/join " " args))))
    :else script))

(defn exit [code & msg]
  (apply println msg)
  (System/exit code))

(defn get-sources []
  (->> "src/" io/file file-seq (filter #(.isFile %))))

(defn app [req]
  {:statue 200
   :headers {"Content-Type" "text/plain"}
   :body (bash (hoist (dots-script (get-sources))))})

(defn spawn [editor]
  (let [runtime (Runtime/getRuntime)
        command (concat editor ["--servername" "dots" "dots.clj"])
        process (.. (ProcessBuilder. command) inheritIO start)]
    (.addShutdownHook runtime (Thread. #(.destroy process)))
    process))

(defn send-msg! [editor msg]
  (let [msg (-> msg str/trim (str/escape {\" "\\\"" \newline "\\n"}))
        command (concat [(first editor)]
                        ["--servername"
                         "dots"
                         "--remote-send"
                         (str ":echo \"" msg "\"<CR>")])]
    (.. (ProcessBuilder. command) start waitFor)))

(defn hostname []
  (or
   (System/getenv "HOST")
   (.getHostName (InetAddress/getLocalHost))))

(defn handle-edit [editor file]
  (let [run (sh "bash" :in (bash (dots-script [file])))]
    (send-msg! editor (-> run :out parse pprint with-out-str))))

(defn edit-dots []
  (let [host (keyword (hostname))
        editor (get-in machines [host :editor] ["vim"])
        process (spawn editor)]
    (hawk/watch! [{:paths ["src"]
                   :filter hawk/file?
                   :handler #(handle-edit editor (:file %2))}])
    (->> (nrepl/start-server) :port (spit ".nrepl-port"))
    (http/run-server #(app %) {:host "0.0.0.0" :port 8080})
    (exit (.waitFor process))))

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
      (:script options)   (do
                            (let [results (binding [t/*test-out* *err*]
                                            (t/with-test-out (t/run-tests *ns*)))]
                              (if-not (= (+ (:fail results) (:error results)) 0)
                                (exit 1)
                                (println (bash (hoist (dots-script (get-sources))))))
                              (exit 0)))
      :else               (edit-dots))))

(defn with-tmp-home [script]
  (let [var (gensym)]
    [:do
     [:def var [:eval [:mktemp "-d"]]]
     [:def :HOME [:ref var]]
     [:trap (str "{ rm -r $" var "; }") 'EXIT]
     script]))

(defn run-install [sources env]
  (sh "bash"
      :env (merge
            {:SKIP_RESTARTS 1} env)
      :in (bash ((if (contains? env :HOME)
                   identity
                   with-tmp-home) (dots-script sources)))))

(deftest install-known-host
  (let [sources (get-sources)
        process (run-install sources {:HOST "archy"})
        output (-> process :out parse)]
    (is (= (:exit process) 0))
    (is (= (:dots/status output) :dots/success))
    (is (= (:system/host-set? output) true))
    (is (= (-> output :dots/files count) (count sources)))))

(deftest install-unknown-host
  (let [process (run-install [] {:HOST "unknown"})
        output (-> process :out parse)]
    (is (= (:exit process) 1))
    (is (= (:dots/status output) :dots/unknown-host))
    (is (= (:system/host-set? output) true))))

(deftest install-unknown-home
  (let [process (run-install [] {:HOME nil :HOST "archy"})
        output (-> process :out parse)]
    (is (= (:exit process) 1))
    (is (= (:dots/status output) :dots/unknown-home))))

(apply main *command-line-args*)
