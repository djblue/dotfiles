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
           (java.time Instant)
           (java.io StringReader ByteArrayOutputStream)
           (org.apache.batik.transcoder TranscoderInput TranscoderOutput)
           (org.apache.batik.transcoder.image PNGTranscoder)))

(def db
  {:config/profiles
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

(defn git-clone [url path]
  [:if [:not [:dir path]] [:git "clone" url path]])

(defn encode [s]
  (.encodeToString (Base64/getEncoder) (if (string? s) (.getBytes s) s)))

(defn echo [path value]
  [:do
   [:pipe
    [:printf (encode (str path))]
    [:base64 "--decode"]]
   (if (string? value)
     [:printf (str " \\\"" value "\\\"\n")]
     [:printf (str " \"" value "\"\n")])])

(defn parse [out]
  (->> (str "[" out "]")
       (read-string)
       (partition 2)
       (reduce
        (fn [acc [path value]] (assoc-in acc path value)) {})))

(declare bash)

(def kv-path "$HOME/.dots")

(defn kv-load []
  [:if [:file kv-path] [:source kv-path]])

(defn kv-get-in [ks]
  [:ref (->> ks (map name) (str/join "_") (str "__kv_") symbol)])

(defn kv-set-in [ks v]
  (let [d [:def (->> ks (map name) (str/join "_") (str "__kv_") symbol)  v]
        e (encode (str (bash d) "\n"))]
    [:do
     d
     [:append [:pipe [:echo e] [:base64 "--decode"]] kv-path]]))

(defn kv-dump []
  [:redirect [:pipe [:set] [:grep "^__kv_"]] kv-path])

(defn install-file [contents path]
  (let [id (-> path sha-1 (subs 0 7))
        sha1 (sha-1 contents)]
    [:do
     [:if [:and
           [:file path]
           [:zero [:ref 'FORCE_INSTALL]]
           [:not [:equals [:eval
                           [:pipe
                            [:sha1sum path]
                            [:cut "-d" " " "-f" 1]]]
                  (kv-get-in [:dots :files id :sha1])]]]
      [:do
       (echo [:dots/dirty-file] path)
       (echo [:dots/status] :dots/dirty)
       [:exit 1]]]
     [:do
      [:mkdir "-p" [:eval [:dirname path]]]
      [:redirect [:pipe
                  [:printf (encode contents)] [:base64 "--decode"]] path]
      (echo [:dots/files id :file/path] path)
      (echo [:dots/files id :file/sha1] sha1)
      (kv-set-in [:dots :files id :sha1] sha1)
      (kv-set-in [:dots :files id :path] path)]]))

(defn get-file [ctx filename]
  (-> ctx :dots/files (get filename)))

(defn install-dotfile [ctx & {:keys [prefix? exec?]
                              :or {prefix? true exec? false}}]
  (fn [filename]
    (let [file (get-file ctx filename)
          contents (render-string (dissoc ctx :dots/files)
                                  (slurp file))
          path (str "$HOME/" (if prefix? "." "") filename)]
      ^:skip
      [:do
       (install-file contents path)
       (if exec? [:chmod "+x" path])])))

(defn setup-bin [ctx]
  ^:skip
  [:do
   (->> ["bin/dots" "bin/dots-reset"]
        (map (install-dotfile ctx :prefix? false :exec? true))
        (cons :do))])

(defn setup-shell [ctx]
  ^:skip
  [:do
   (->> ["aliases" "bashrc" "profile" "tmux.conf" "zshrc" "gitconfig"]
        (map (install-dotfile ctx))
        (cons :do))])

(defn setup-vim [ctx]
  ^:skip
  [:do
   (git-clone "https://github.com/VundleVim/Vundle.vim.git"
              "$HOME/.vim/bundle/Vundle.vim")
   (->> ["gvimrc" "ideavimrc" "vimrc"]
        (map (install-dotfile ctx))
        (cons :do))])

(defn setup-cli [ctx]
  [:do (setup-bin ctx) (setup-shell ctx) (setup-vim ctx)])

(defn setup-wallpaper [ctx]
  (let [path "wallpaper/arch.svg"
        contents (->> path (get-file ctx) slurp svg->png)
        path (str "$HOME/" (str/replace path #"\.svg$" ".png"))]
    ^:skip
    [:do
     (install-file contents path)
     (case (:system/platform ctx)
       :linux [:feh "--bg-fill" path]
       nil)]))

(defn setup-xmonad [ctx]
  ^:skip
  [:do
   (->> ["xmonad/xmonad.hs"
         "xmonad/lib/XMonad/Layout/EqualSpacing.hs"
         "xmobarrc"
         "dunstrc"
         "Xdefaults"
         "xinitrc"]
        (map (install-dotfile ctx))
        (cons :do))
   [:xmonad "--recompile"]
   [:xmonad "--restart"]
   [:xrdb "-merge" "$HOME/.Xdefaults"]
   (setup-wallpaper ctx)])

(defn dump-info []
  ^:skip
  [:do
   (echo [:dots/build-time] (str (Instant/now)))
   (echo [:dots/install-time] "$(date -u +'%Y-%m-%dT%H:%M:%SZ')")
   (echo [:system/shell] "$0")
   (echo [:system/kernel-name] "$(uname -s)")
   (echo [:system/kernel-release] "$(uname -r)")
   (echo [:system/machine] "$(uname -m)")])

(defn dots-script [files]
  (let [ctx {:dots/files files}]
    [:do
     [:set "-e"]
     (kv-load)
     [:if [:zero "$HOME"]
      [:do
       (echo [:dots/status] :dots/unknown-home)
       [:exit 1]]
      (echo [:system/home] "$HOME")]
     [:if [:zero "$HOST"]
      [:do
       [:def :HOST [:eval [:hostname]]]
       (echo [:system/host-set?] false)]
      (echo [:system/host-set?] true)]
     (echo [:system/host] "$HOST")
     (dump-info)
     [:case "$HOST"
      "red-machine"
      (let [ctx (merge ctx
                       (get-config db :nord [:default]))]
        [:do
         (setup-cli ctx)])
      "archy"
      (let [ctx (merge ctx
                       (get-config db :nord [:default]))]
        [:do
         (setup-cli ctx)
         (setup-xmonad ctx)])
      "osx"
      (let [ctx (merge ctx
                       (get-config db :nord [:default :laptop :hidpi]))]
        [:do
         (setup-cli ctx)
         (setup-xmonad ctx)])
      "badahdah"
      (let [ctx (merge ctx
                       (get-config db :nord [:default]))]
        [:do
         (setup-cli ctx)
         (setup-wallpaper ctx)])
      [:do
       (echo [:dots/status] :dots/unknown-host)
       [:exit 1]]]
     (kv-dump)
     (echo [:dots/status] :dots/success)]))

(defn diff-script [a b]
  (cond
    (-> a meta :skip)
    (if (not= a b)
      (diff-script (with-meta a {:skip false}) b))
    (or (vector? a) (seq? a))
    (map-indexed #(diff-script %2 (nth b %1)) a)
    :else a))

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
        :and        (str/join " && " args)
        :not        (str "! " arg1)
        :dir        (str "-d " arg1)
        :file       (str "-f " arg1)
        :zero       (str "-z " arg1)
        :eval       (str "$(" arg1 ")")
        :pipe       (str/join " | " args)
        :equals     (str arg1 " == " arg2)
        :redirect   (str arg1 " > " arg2)
        :append     (str arg1 " >> " arg2)
        :def        (str (name arg1) "=" arg2)
        :ref        (str "$" arg1)
        (str (name op) " " (str/join " " args))))
    :else script))

(defn exit [code & msg]
  (apply println msg)
  (System/exit code))

(def cwd (.toPath (.getAbsoluteFile (io/file "src"))))

(defn get-relative-name [file]
  (.toString (.relativize cwd (.toPath (.getAbsoluteFile file)))))

(defn get-sources []
  (->> "src/"
       io/file
       file-seq
       (filter #(.isFile %))
       (map #(-> [(get-relative-name %) %]))
       (into {})))

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

(def dots-prev (atom nil))

(defn handle-edit [editor _]
  (let [dots-next (dots-script (get-sources))
        diff (diff-script dots-next @dots-prev)
        run (sh "bash" :in (bash diff))]
    (reset! dots-prev dots-next)
    (send-msg! editor (-> run :out parse pprint with-out-str))))

(defn has-bin? [bin]
  (let [file (->> (str/split (System/getenv "PATH") #":")
                  (map io/file)
                  (mapcat file-seq)
                  (filter #(= (.getName %) bin))
                  first)]
    (and (some? file) (.canExecute file))))

(defn edit-dots []
  (let [editor (if (has-bin? "mvim") ["mvim" "-f"] ["vim"])
        process (spawn editor)]
    (reset! dots-prev (dots-script (get-sources)))
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

(defn run-install [env & setup]
  (sh "sh"
      :env env
      :in (bash ((if (contains? env :HOME)
                   identity
                   with-tmp-home)
                 [:do (cons :do setup) (dots-script (get-sources))]))))

(deftest install-known-host
  (let [process (run-install {:HOST "red-machine"})
        output (-> process :out parse)]
    (is (= (:exit process) 0))
    (is (= (:dots/status output) :dots/success))
    (is (= (:system/host-set? output) true))))

(deftest install-unknown-host
  (let [process (run-install {:HOST "unknown"})
        output (-> process :out parse)]
    (is (= (:exit process) 1))
    (is (= (:dots/status output) :dots/unknown-host))
    (is (= (:system/host-set? output) true))))

(deftest install-unknown-home
  (let [process (run-install {:HOME nil :HOST "red-machine"})
        output (-> process :out parse)]
    (is (= (:exit process) 1))
    (is (= (:dots/status output) :dots/unknown-home))))

(deftest install-existing-file
  (let [process (run-install {:HOST "red-machine"}
                             [:do
                              [:mkdir "-p" "$HOME/bin"]
                              [:touch "$HOME/bin/dots"]])
        output (-> process :out parse)]
    (is (= (:exit process) 1))
    (is (= (:dots/status output) :dots/dirty))
    (is (str/ends-with? (:dots/dirty-file output) "bin/dots"))))

(deftest install-force-install
  (let [process (run-install {:HOST "red-machine"
                              :FORCE_INSTALL 1}
                             [:do
                              [:mkdir "-p" "$HOME/bin"]
                              [:touch "$HOME/bin/dots"]])
        output (-> process :out parse)]
    (is (= (:exit process) 0))
    (is (= (:dots/status output) :dots/success))))

(apply main *command-line-args*)
