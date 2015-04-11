#!/bin/bash
# Script to easily install dotfiles.

# script variables
cd $(dirname $0)
dir="$(pwd)"        # dotfiles directory
olddir="$dir-old"   # old dotfiles backup directory
files="$(ls $dir)"  # list of files/folders

IFS=$'\n'

install=$(cat .INSTALL)

script_dir=$(dirname `which $0`);

for line in $install; do
  repo="$(echo $line | cut -f1 -d' ')"
  dest="$script_dir/$(echo $line | tr -s ' ' | cut -f2 -d' ')"
  if [ ! -d $dest ]; then
    echo "cloning $repo to $dest"
    git clone $repo $dest
  else
    echo "pulling $repo to $dest"
    cd $dest
    git pull
  fi
done

# excludes
exclude="$0 README.md"

for file in $files; do

    # skip files in the exclude list
    echo $exclude | grep $file > /dev/null && continue

    # backup old files; not old symlinks
    if [ ! -L ~/.$file ]; then

        mkdir -p $olddir

        if [[ -f ~/.$file || -d ~/.$file ]]; then
            echo "Moving .$file"
            mv ~/.$file $olddir/
        fi

        echo "Symlink: $file"
        ln -s $dir/$file ~/.$file
     
    else # dotfile already exits as link

        echo "Updating Symlink: $file"
        ln -nsf $dir/$file ~/.$file

    fi

done
