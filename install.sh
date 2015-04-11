#!/bin/bash
# Script to easily install dotfiles.

# custom log function.
log () {
  echo "==> $@"
}

# will echo line piped it with 4 spaces.
tab () {
  while read line; do echo "    $line"; done
}

IFS=$'\n' # set the for each delimiter to be the new line

dots="https://github.com/djblue/dotfiles.git"   # git repository
dir="$HOME/dotfiles"                            # install directory
olddir="$dir-old"                               # backup directory
files="$(ls $dir)"                              # list of files/folders
install=$(cat .INSTALL)                         # install manifest

# check if the dotfiles repo is already
if [ ! -d $dir ]; then
  log "cloning $dots"
  git clone $dots $dir | tab
fi

# always pull the most up-to-date dotfiles
log "pulling $dots"
cd $dir
git pull | tab

# go through all the install repos and install/update all of them
for line in $install; do

  # the first column is the repo url
  method="$(echo $line | tr -s ' ' | cut -f1 -d' ')"
  url="$(echo $line | tr -s ' ' | cut -f2 -d' ')"
  # the scond column is the destination relative to $dir
  dest="$dir/$(echo $line | tr -s ' ' | cut -f3 -d' ')"

  if [ "$method" == "git" ]; then
    # the repo doesn't exist, installing
    if [ ! -d $dest ]; then
      log "installing $url"
      git clone $url $dest | tab
    # the repo does exist, updating
    else
      log "updating $url"
      cd $dest
      git pull | tab
    fi
  elif [ "$method" == "curl" ]; then
    log "installing $url"
    curl -sL $url > $dest
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
            log "Moving .$file"
            mv ~/.$file $olddir/
        fi
        log "Symlink: $file"
        ln -s $dir/$file ~/.$file
    else # dotfile already exits as link
        log "Updating Symlink: $file"
        ln -nsf $dir/$file ~/.$file
    fi

done
