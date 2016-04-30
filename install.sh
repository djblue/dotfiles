#!/bin/bash
# Script to easily install dotfiles.

set -e # exit on first error

dots="https://github.com/djblue/dotfiles.git"   # git repository
dir="$HOME/dotfiles"                            # install directory
olddir="$dir-old"                               # backup directory

doall() { while read line; do $1 $line; done; }
comment() { echo "    $@"; }
run() { echo "==> $@"; $@ 2>&1 | doall comment; }

symlink() {
  if [ ! -L ~/.$1 ]; then
    [[ -f ~/.$1 ]] && run mv ~/.$1 $olddir/
    run ln -s $dir/$1 ~/.$1
  fi
}

# check if the dotfiles repo is already cloned
[[ ! -d $dir ]] && run git clone $dots $dir

# always pull the most up-to-date dotfiles
cd $dir && run pwd
run git pull

# create backup directory
[[ ! -d $olddir ]] && run mkdir -p $olddir

# symlink all the dotfiles
ls $dir | grep -vE 'README.md|install.sh' | doall symlink

# install vundle to manage vim bundles
if [ ! -d "$HOME/.vim/bundle/Vundle.vim" ]; then
  run git clone https://github.com/VundleVim/Vundle.vim.git ~/.vim/bundle/Vundle.vim
fi
