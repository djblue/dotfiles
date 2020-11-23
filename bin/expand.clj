(ns expand
  (:require [clojure.java.io :as io]
            [clojure.java.shell :refer [sh]]
            [clojure.string :as str]))

(defn- xmobar [config]
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
   :config/files
   {:dots #{"bin/expand.clj"}
    :shell #{".aliases"
             ".bashrc"
             ".gitconfig"
             ".profile"
             ".tmux.conf"
             ".zshrc"}
    :vim #{".gvimrc" ".ideavimrc" ".vimrc" ".config/nvim/init.vim"}
    :clojure #{".clojure/deps.edn"}
    :emacs #{".emacs.d/init.el" ".emacs.d/settings.org"}
    :atom #{".atom/config.cson"
            ".atom/keymap.cson"
            ".atom/packages.cson"
            ".atom/styles.less"}
    :wallpaper #{"wallpaper/arch.svg"}
    :xmond #{".xmonad/xmonad.hs"
             ".xmonad/lib/XMonad/Layout/EqualSpacing.hs"
             ".xmobarrc"
             ".dunstrc"
             ".Xdefaults"
             ".xinitrc"}}
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

(defn- get-config [db theme profiles]
  (->> profiles
       (map #(get-in db [:config/profiles %]))
       (cons (get-in db [:config/themes theme]))
       (apply merge)))

(defn- render-string [config template]
  (str/replace
   template
   #"(?sm)\{\{((?:(?!\}\}).)*)\}\}"
   (fn [[_ string]]
     (let [expr (read-string string)]
       (str
        (cond
          (keyword? expr) (expr config)
          (vector? expr)  (get-in config expr)))))))

(defn- get-files []
  (str/split (:out (sh "git" "ls-files")) #"\n"))

(defn- sync-files []
  (let [config (get-config db :nord [:default])
        keep?  (->> #{:dots :shell :vim :atom :clojure :wallpaper}
                    (mapcat (:config/files db))
                    (into #{}))]
    (doseq [file (get-files)]
      (let [original (slurp file)
            expanded (render-string config original)]
        (cond
          (not (keep? file))
          (io/delete-file file)

          (not= original expanded)
          (spit file expanded))))))

(defn- git-dirty? []
  (not (zero? (:exit (sh "git" "diff" "--quiet")))))

(defn -main []
  (when (git-dirty?)
    (println "git is dirty")
    (System/exit 1))
  (sync-files)
  (sh "git" "add" ".")
  (print (:out (sh "git" "commit" "-m" ":dots/expand"))))

(-main)
