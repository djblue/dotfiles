(ns dots
  (:require [clojure.tools.nrepl.server :as nrepl]
            [clojure.pprint :refer [pprint]]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.java.shell :refer [sh]]
            [clojure.test :refer [is deftest] :as t]
            [hawk.core :as hawk]
            [neovim-client.1.api :as api]
            [neovim-client.nvim :as nvim]
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
        sha1 (sha-1 contents)
        contents (encode contents)]
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
       [:pipe
        [:echo (kv-get-in [:dots :files id :contents])]
        [:base64 "--decode"]
        [:diff "-u" "-" path [:bash "1>&2"]]]
       [:exit 1]]]
     [:do
      [:mkdir "-p" [:eval [:dirname path]]]
      [:redirect [:pipe
                  [:printf contents] [:base64 "--decode"]] path]
      (echo [:dots/files id :file/path] path)
      (echo [:dots/files id :file/sha1] sha1)
      (kv-set-in [:dots :files id :sha1] sha1)
      (kv-set-in [:dots :files id :path] path)
      (kv-set-in [:dots :files id :contents] contents)]]))

(defn get-file [ctx filename]
  (-> ctx :dots/files (get filename)))

(defn install-dotfile [ctx & {:keys [prefix? exec?]
                              :or {prefix? true exec? false}}]
  (fn [filename]
    (when-let [file (get-file ctx filename)]
      (let [contents (render-string (dissoc ctx :dots/files)
                                    (slurp file))
            path (str "$HOME/" (if prefix? "." "") filename)]
        [:do
         (install-file contents path)
         (if exec? [:chmod "+x" path])]))))

(defn setup-bin [ctx]
  [:do
   (->> ["bin/vim-wrap"]
        (map (install-dotfile ctx :prefix? false :exec? true))
        (cons :do))])

(defn setup-shell [ctx]
  [:do
   (->> ["aliases" "bashrc" "profile" "tmux.conf" "zshrc" "gitconfig"]
        (map (install-dotfile ctx))
        (cons :do))])

(defn setup-vim [ctx]
  [:do
   (git-clone "https://github.com/VundleVim/Vundle.vim.git"
              "$HOME/.vim/bundle/Vundle.vim")
   (->> ["gvimrc" "ideavimrc" "vimrc" "config/nvim/init.vim"]
        (map (install-dotfile ctx))
        (cons :do))])

(defn setup-cli [ctx]
  [:do (setup-bin ctx) (setup-shell ctx) (setup-vim ctx)])

(defn setup-wallpaper [ctx]
  (when-let  [file (get-file ctx "wallpaper/arch.svg")]
    (let [contents (->> file slurp svg->png)
          path (str "$HOME/wallpaper/arch.png")]
      [:do
       (install-file contents path)
       (case (:system/platform ctx)
         :linux [:feh "--bg-fill" path]
         nil)])))

(defn setup-xmonad [ctx]
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

(defn set-path [& paths]
  [:do
   [:def :PATH ""]
   (->> paths
        (map #(-> [:if [:dir %] [:def :PATH (str % ":$PATH")]]))
        (cons :do))
   (echo [:system/path] "$PATH")])

(defn dump-info []
  [:do
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
     (set-path "/bin"
               "/sbin"
               "/usr/bin"
               "/usr/local/bin"
               "/usr/local/sbin"
               "/usr/sbin")
     (dump-info)
     [:case "$HOST"
      [:bash "red-machine|archy"]
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
      [:bash "badahdah|badahdah.local"]
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
        :bash       (second script)
        (str (name op) " " (str/join " " args))))
    :else script))

(def cwd (.toPath (.getAbsoluteFile (io/file "src"))))

(defn get-relative-name [file]
  (.toString (.relativize cwd (.toPath (.getAbsoluteFile file)))))

(defn get-sources
  ([]
   (->> "src/"
        io/file
        file-seq
        (filter #(.isFile %))
        get-sources))
  ([files]
   (->> files
        (map #(-> [(get-relative-name %) %]))
        (into {}))))

(defn send-msg! [nvim msg]
  (let [msg (-> msg str/trim (str/escape {\" "\\\"" \newline "\\n"}))
        command (str ":echo \"" msg "\"")]
    (api/command nvim command)))

(defn handle-edit [nvim file]
  (let [dots (dots-script (get-sources [file]))
        run (sh "bash" :in (bash dots))
        result (->> run :out parse)]
    (send-msg!
     nvim
     (if (zero? (:exit run))
       (->> result
            :dots/files
            (map #(str "Updated `" (-> % second :file/path) "` successfully!"))
            (str/join "\n"))
       (str
        (->> result pprint with-out-str)
        (:err run))))))

(defn edit-dots []
  (let [nvim (nvim/new 1 "127.0.0.1" 7777)]
    (hawk/watch! {:watcher :polling}
                 [{:paths ["src"]
                   :filter hawk/file?
                   :handler #(handle-edit nvim (:file %2))}])
    (->> (nrepl/start-server) :port (spit ".nrepl-port"))
    (send-msg! nvim "Started dev server successfully!")))

(defn -main [& args]
  (case (first args)
    "server" (edit-dots)
    "test"
    (let [results (t/run-tests 'dots)]
      (shutdown-agents)
      (when-not (zero? (+ (:fail results) (:error results)))
        (System/exit 1)))
    (println (bash (hoist (dots-script (get-sources)))))))

(defn deploy-host!
  ([host script] (deploy-host! host {} script))
  ([host env script]
   (let [env (->> env
                  (map (fn [[k v]]
                         [:def (symbol (name k)) v]))
                  (cons :do))
         script [:do (if (zero? (count env)) nil env) script]]
     (->> (bash script)
          (sh "ssh" (name host) :in) :out parse))))

(defn deploy-all! []
  (let [script (hoist (dots-script (get-sources)))]
    (->> [:mac :red]
         (map #(-> [% (deploy-host! % script)]))
         (into {})
         pprint)))

(comment (deploy-all!))

(defn with-tmp-home [script]
  (let [var (gensym)]
    [:do
     [:def var [:eval [:mktemp "-d"]]]
     [:def :HOME [:ref var]]
     [:trap (str "{ rm -r $" var "; }") 'EXIT]
     script]))

(defn run-install [env & setup]
  (sh "bash"
      :env env
      :in (bash ((if (contains? env :HOME)
                   identity
                   with-tmp-home)
                 [:do (cons :do setup) (dots-script (get-sources))]))))

(deftest install-known-host
  (let [process (run-install {:HOST "badahdah"})
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
  (let [process (run-install {:HOME nil :HOST "badahdah"})
        output (-> process :out parse)]
    (is (= (:exit process) 1))
    (is (= (:dots/status output) :dots/unknown-home))))

(deftest install-existing-file
  (let [process (run-install {:HOST "badahdah"}
                             [:do
                              [:mkdir "-p" "$HOME/bin"]
                              [:touch "$HOME/bin/vim-wrap"]])
        output (-> process :out parse)]
    (is (= (:exit process) 1))
    (is (= (:dots/status output) :dots/dirty))
    (is (str/ends-with? (:dots/dirty-file output) "bin/vim-wrap"))))

(deftest install-force-install
  (let [process (run-install {:HOST "badahdah"
                              :FORCE_INSTALL 1}
                             [:do
                              [:mkdir "-p" "$HOME/bin"]
                              [:touch "$HOME/bin/vim-wrap"]])
        output (-> process :out parse)]
    (is (= (:exit process) 0))
    (is (= (:dots/status output) :dots/success))))
