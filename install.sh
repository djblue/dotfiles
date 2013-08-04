#!/bin/bash
# Script to easily install dotfiles.

# script variables

script="$(realpath $0)"
dir="$(dirname $script)"    # dotfiles directory
olddir="$dir-old"           # old dotfiles backup directory
files="$(ls $dir)"          # list of files/folders
exclude="$0 .git vim.sh"    # exclude .git and install script

for file in $files; do

    # skip files in the exclude list
    echo $exclude | grep $file > /dev/null
    if [ $? -eq 0 ]; then continue; fi

    # backup old files; not old symlinks
    if [ ! -L ~/.$file ]; then

        mkdir -p $olddir

        echo "Moving .$file ~ to $olddir"
        mv ~/.$file $olddir/

        echo "Creating Symlink: $file"
        ln -s $dir/$file ~/.$file
     
    else # dotfile already exits as link

        echo "Updating Symlink: $file"
        ln -sf $dir/$file ~/.$file

    fi

done

# update all vim plugins
git submodule foreach git pull origin master
