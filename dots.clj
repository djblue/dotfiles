(ns dots
  (:require [clojure.tools.nrepl.server :as nrepl]
            [clojure.pprint :refer [pprint]]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.java.shell :refer [sh]]
            [clojure.test :refer [is deftest] :as t]
            [hawk.core :as hawk]
            [neovim-client.1.api :as api]
            [neovim-client.nvim :as nvim])
  (:import (java.util Base64)
           (java.io StringReader ByteArrayOutputStream)
           (org.apache.batik.transcoder TranscoderInput TranscoderOutput)
           (org.apache.batik.transcoder.image PNGTranscoder)))

#_(->> (-main) with-out-str (spit "install.sh"))

(defn xmobar [config]
  (str
   " %UnsafeStdinReader% }{ "
   (->> [[:system/has-wireless?  "%wlp4s0wi%"]
         [:system/has-backlight? "%bright%"]
         [:system/has-battery?   "%battery%"]
         [:system/has-sound?     "%date%"]]
        (filter (fn [[k]] (k config)))
        (map second)
        (str/join " | "))
   " "))

(def db
  {:config/profiles
   {:default
    {:theme/dpi 96
     :theme/font-size 18
     :theme/font-name "Inconsolata"
     :theme/font-alt "Inconsolata for Powerline"
     :xmobar/height 32
     :xmonad/border-width 2
     :xmobar/template (xmobar #{:system/has-sound?})}
    :hidpi
    {:theme/dpi 196
     :theme/font-size 38
     :xmonad/border-width 4
     :xmobar/height 64}
    :laptop
    {:xmobar/template
     (xmobar #{:system/has-sound?
               :system/has-battery?
               :system/has-wireless?
               :system/has-backlight?})}}
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

(defn encode [s]
  (.encodeToString (Base64/getEncoder) (if (string? s) (.getBytes s) s)))

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
          (vector? expr)  (get-in config expr)))))))

(def svg->png
  (memoize
   (fn [svg]
     (with-open [out (ByteArrayOutputStream.)]
       (.transcode (PNGTranscoder.)
                   (TranscoderInput. (StringReader. svg))
                   (TranscoderOutput. out))
       (.toByteArray out)))))

(defn echo [path value]
  [:do
   [:printf (str path " ")]
   (cond
     (or (keyword? value)
         (boolean? value))
     [:echo (str value)]
     :else [:do
            [:echo "-n" "\""]
            [:pipe value [:tr "-d" "\\n"]]
            [:echo "\""]])])

(defn setup-vundle []
  (let [url "https://github.com/VundleVim/Vundle.vim.git"
        path [:str "$HOME/.vim/bundle/Vundle.vim"]]
    [:if [:not [:dir path]]
     [:do
      (echo [:vundle/init?] false)
      [:redirect [:git "clone" url path] "/dev/null" :all]]
     (echo [:vundle/init?] true)]))

(defn parse [out]
  (->> (str "[" out "]")
       (read-string)
       (partition 2)
       (reduce
        (fn [acc [path value]] (assoc-in acc path value)) {})))

(declare bash)

(defn transact [script message]
  (let [dir (gensym)
        commit (gensym)
        git (fn [& args]
              (concat [:git [:str "--git-dir=$HOME/.dots"]] args))
        git-home (fn [& args] (apply git [:str "--work-tree=$HOME"] args))
        git-temp (fn [& args] (apply git [:str (str "--work-tree=$" dir)] args))
        silent (fn [& cmds]
                 (->> cmds
                      (map (fn [cmd] [:redirect cmd "/dev/null" :all]))
                      (cons :do)))]
    [:do
     [:set "-e"]
     [:if [:not [:dir [:str "$HOME/.dots"]]]
      [:do
       (silent [:git :init "--bare" [:str "$HOME/.dots"]]
               (git :config "--local" :user.email "a.s.badahdah@gmail.com")
               (git :config "--local" :user.name "Chris Badahdah")
               (git :config "--local" :status.showUntrackedFiles :no)
               (git-home :commit "--allow-empty" "--message" "Initializing dots repo"))
       (echo [:dots/init?] false)]
      (echo [:dots/init?] true)]

     [:def dir [:eval [:mktemp "-d"]]]
     [:trap
      [:str
       (bash [:block
              (silent (git-temp :checkout :master "-f"))
              [:rm "-r" [:ref dir]]])]
      :EXIT]

     [:or (silent (git-home :diff "--no-ext-diff" "--exit-code"))
      [:block
       [:if [:zero [:ref :FORCE_INSTALL]]
        [:do
         (echo [:dots/status] :dots/dirty)
         [:exit 1]]]]]

     (silent (git-temp :checkout "--detach"))

     (silent [:pushd [:ref dir]])
     script
     (silent [:popd])

     (silent (git-temp :add "."))
     [:or
      (silent (git-temp :commit "--message" message))
      [:block
       [:if [:zero [:ref :FORCE_INSTALL]]
        [:do
         (echo [:dots/status] :dots/unchanged)
         [:exit 0]]]]]

     [:def commit [:eval (git-temp :rev-parse :HEAD)]]

     (silent (git-temp :checkout :master "-f")
             (git-home :status))
     [:or
      (silent (git-home :reset
                        [:eval [:if [:zero [:ref :FORCE_INSTALL]]
                                [:echo "--merge"]
                                [:echo "--hard"]]]
                        [:ref commit]))
      [:block
       (echo [:dots/status] :dots/file-exists)
       [:exit 1]]]

     [:printf (str [:dots/files-changed] " [ ")]
     [:pipe
      (git-home :diff "--name-only" "HEAD~1")
      [:sed "-E" "s/(.*)/\"\\1\"/"]
      [:tr "\\n" " "]]
     [:echo "]"]

     (echo [:dots/status] :dots/success)]))

(defn get-file [ctx filename]
  (-> ctx :dots/files (get filename)))

(defn install-dotfile [ctx & {:keys [prefix? exec?]
                              :or {prefix? true exec? false}}]
  (fn [filename]
    (when-let [file (get-file ctx filename)]
      (let [contents (encode (render-string ctx (slurp file)))
            path (str (if prefix? "." "") filename)
            parent (butlast (str/split filename #"/"))]
        [:do
         (when-not (zero? (count parent))
           [:mkdir "-p" (str (if prefix? "." "")
                             (str/join "/" parent))])
         [:redirect
          [:pipe
           [:printf contents]
           [:base64 "--decode"]]
          path]
         (if exec? [:chmod "+x" path])]))))

(defn setup-bin [ctx]
  (->> ["bin/vim-wrap"]
       (map (install-dotfile ctx :prefix? false :exec? true))
       (cons :do)))

(defn setup-shell [ctx]
  (->> ["aliases" "bashrc" "profile" "tmux.conf" "zshrc" "gitconfig"]
       (map (install-dotfile ctx))
       (cons :do)))

(defn setup-vim [ctx]
  (->> ["gvimrc" "ideavimrc" "vimrc" "config/nvim/init.vim"]
       (map (install-dotfile ctx))
       (cons :do)))

(defn setup-clojure [ctx]
  (->> ["clojure/deps.edn"]
       (map (install-dotfile ctx))
       (cons :do)))

(defn setup-cli [ctx]
  [:do (setup-bin ctx) (setup-shell ctx) (setup-vim ctx) (setup-clojure ctx)])

(defn setup-wallpaper [ctx]
  (when-let  [file (get-file ctx "wallpaper/arch.svg")]
    (let [contents (->> file slurp svg->png)
          path "wallpaper/arch.png"]
      [:do
       [:mkdir "wallpaper"]
       [:redirect
        [:pipe
         [:printf (encode contents)]
         [:base64 "--decode"]]
        path]
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
   [:xrdb "-merge" [:str "$HOME/.Xdefaults"]]])

(defn set-path [& paths]
  [:def :PATH (str/join ":" paths)])

(defn dump-info []
  [:do
   (echo [:dots/install-time]
         [:date "-u" "+%Y-%m-%dT%H:%M:%SZ"])
   (echo [:system/shell] [:printf [:ref :0]])
   (echo [:system/kernel-name] [:uname "-s"])
   (echo [:system/kernel-release] [:uname "-r"])
   (echo [:system/machine] [:uname "-m"])])

(defn setup-host [config]
  (let [ctx (merge
             config
             (get-config db (:config/theme config)
                         (concat [:default] (:config/profiles config))))]
    [:do
     (setup-cli ctx)
     (when (:config/setup-wallpaper? config) (setup-wallpaper ctx))
     (when (:config/setup-xmonad? config) (setup-xmonad ctx))]))

(defn dots-script
  ([files] (dots-script files "Ran install.sh"))
  ([files message]
   [:do
    [:set "-e"]
    (set-path "/bin"
              "/sbin"
              "/usr/bin"
              "/usr/local/bin"
              "/usr/local/sbin"
              "/usr/sbin")
    [:if [:zero [:ref :HOME]]
     [:do
      (echo [:dots/status] :dots/unknown-home)
      [:exit 1]]
     (echo [:system/home] [:printf [:ref :HOME]])]
    [:if [:zero [:ref :HOST]]
     [:do
      [:def :HOST [:eval [:hostname]]]
      (echo [:system/host-set?] false)]
     (echo [:system/host-set?] true)]
    (echo [:system/host] [:printf [:ref :HOST]])
    (dump-info)
    (setup-vundle)
    (transact
     [:case [:ref :HOST]
      :red-machine
      (setup-host
       {:dots/files files
        :config/theme :nord})
      :archy
      (setup-host
       {:dots/files files
        :config/theme :nord})
      :archlinux
      (setup-host
       {:dots/files files
        :config/theme :nord})
      :osx
      (setup-host
       {:dots/files files
        :config/theme :nord
        :config/profiles [:laptop :hidpi]})
      (setup-host
       {:dots/files files
        :config/theme :nord})]
     message)]))

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
     (and (or (vector? script) (seq? script))
          (not= (first script) :str))
     (reduce
      #(merge-with conj %1 (hoist %2 (:vars %1)))
      {:vars vars :script [(first script)]}
      (rest script))
     :else {:vars vars :script script})))

(defn bash [script]
  (cond
    (string? script) (str "'" script "'")
    (keyword? script) (name script)
    (or (vector? script) (seq? script))
    (let [[op & ops] script
          args (map bash (filter some? ops))
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
        :or         (str/join " || " args)
        :block      (str "{ " (str/join "; " args) "; }")
        :not        (str "! " arg1)
        :dir        (str "-d " arg1)
        :file       (str "-f " arg1)
        :zero       (str "-z " arg1)
        :eval       (str "$(" arg1 ")")
        :pipe       (str/join " | " args)
        :equals     (str arg1 " == " arg2)
        :redirect   (str arg1 (case arg3 "stderr" " 2>" "all" " &> " " > ") arg2)
        :append     (str arg1 " >> " arg2)
        :def        (str (name arg1) "=" arg2)
        :ref        (str "$" (name arg1))
        :str        (str "\"" (first ops) "\"")
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
  (let [dots (dots-script (get-sources) (str "Updated " (.getName file)))
        run (sh "bash" :in (bash dots))
        result (->> run :out parse)]
    (send-msg!
     nvim
     (if (zero? (:exit run))
       (case (:dots/status result)
         :dots/unchanged "No changed files"
         (->> result
              :dots/files-changed
              (map #(str "Updated `" % "` successfully"))
              (str/join "\n")
              pprint
              with-out-str))
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
  (let [process (run-install {:HOST "archlinux"})
        output (-> process :out parse)]
    (is (= (:exit process) 0))
    (is (= (:dots/status output) :dots/success))
    (is (= (:system/host-set? output) true))))

(deftest install-unknown-home
  (let [process (run-install {:HOME nil :HOST "archlinux"})
        output (-> process :out parse)]
    (is (= (:exit process) 1))
    (is (= (:dots/status output) :dots/unknown-home))))

(deftest install-existing-file
  (let [process (run-install {:HOST "archlinux"}
                             [:do
                              [:mkdir "-p" [:str "$HOME/bin"]]
                              [:touch [:str "$HOME/bin/vim-wrap"]]])
        output (-> process :out parse)]
    (is (= (:exit process) 1))
    (is (= (:dots/status output) :dots/file-exists))))

(deftest install-force-install
  (let [process (run-install {:HOST "archlinux"
                              :FORCE_INSTALL 1}
                             [:do
                              [:mkdir "-p" [:str "$HOME/bin"]]
                              [:touch [:str "$HOME/bin/vim-wrap"]]])
        output (-> process :out parse)]
    (is (= (:exit process) 0))
    (is (= (:dots/status output) :dots/success))))
