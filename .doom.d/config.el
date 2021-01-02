;;; $DOOMDIR/config.el -*- lexical-binding: t; -*-

;; Place your private configuration here! Remember, you do not need to run 'doom
;; sync' after modifying this file!


;; Some functionality uses this to identify you, e.g. GPG configuration, email
;; clients, file templates and snippets.
(setq user-full-name "John Doe"
      user-mail-address "john@doe.com")

;; Doom exposes five (optional) variables for controlling fonts in Doom. Here
;; are the three important ones:
;;
;; + `doom-font'
;; + `doom-variable-pitch-font'
;; + `doom-big-font' -- used for `doom-big-font-mode'; use this for
;;   presentations or streaming.
;;
;; They all accept either a font-spec, font string ("Input Mono-12"), or xlfd
;; font string. You generally only need these two:
(setq doom-font (font-spec :family "Monaco" :size 18))

;; There are two ways to load a theme. Both assume the theme is installed and
;; available. You can either set `doom-theme' or manually load a theme with the
;; `load-theme' function. This is the default:
(setq doom-theme 'doom-nord)

;; If you use `org' and don't want your org files in the default location below,
;; change `org-directory'. It must be set before org loads!
(setq org-directory "~/org/")

;; This determines the style of line numbers in effect. If set to `nil', line
;; numbers are disabled. For relative line numbers, set this to `relative'.
(setq display-line-numbers-type t)


;; Here are some additional functions/macros that could help you configure Doom:
;;
;; - `load!' for loading external *.el files relative to this one
;; - `use-package' for configuring packages
;; - `after!' for running code after a package has loaded
;; - `add-load-path!' for adding directories to the `load-path', relative to
;;   this file. Emacs searches the `load-path' when you load packages with
;;   `require' or `use-package'.
;; - `map!' for binding new keys
;;
;; To get information about any of these functions/macros, move the cursor over
;; the highlighted symbol at press 'K' (non-evil users must press 'C-c g k').
;; This will open documentation for it, including demos of how they are used.
;;
;; You can also try 'gd' (or 'C-c g d') to jump to their definition and see how
;; they are implemented.

(after! evil-escape
  (setq evil-escape-key-sequence "kj"))

;; ctrl + p compatibility
(map! :n "C-p" #'projectile-find-file
      :n ",q"  #'evil-window-delete
      :n ",c"  #'evil-ex-nohighlight)

(map! :map emacs-lisp-mode-map
      :n "cpp" #'eval-defun)

;; Clojure vim-fireplace compatibility
(map! :map clojure-mode-map
      :n "cpp" #'cider-eval-defun-at-point
      :n "ff"  #'cider-format-defun)

(map! :map (emacs-lisp-mode-map
            clojure-mode-map)
      :n ",W"  #'paredit-wrap-round
      :n ",w(" #'paredit-wrap-round
      :n ",w{" #'paredit-wrap-curly
      :n ",w[" #'paredit-wrap-square
      :n ",>"  #'paredit-forward-slurp-sexp
      :n ",<"  #'paredit-forward-barf-sexp
      :n ",S"  #'paredit-splice-sexp)

(setq cider-clojure-cli-global-options "-A:dev")
(setq cider-repl-pop-to-buffer-on-connect nil)

(defun Require ()
  (interactive)
  (cider-load-buffer))

(add-to-list 'default-frame-alist '(ns-transparent-titlebar . t))
(add-to-list 'default-frame-alist '(ns-appearance . dark))
(add-to-list 'default-frame-alist '(alpha . (97 . 95)))

;; Set transparency of emacs
(defun transparency (value)
  "Sets the transparency of the frame window. 0=transparent/100=opaque"
  (interactive "nTransparency Value 0 - 100 opaque:")
  (set-frame-parameter (selected-frame) 'alpha value))
