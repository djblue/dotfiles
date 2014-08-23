# autocompletion setting
autoload -Uz compinit
compinit
zstyle :compinstall filename '/home/chris/.zshrc'
zstyle ':completion:*' menu select

# prompt settings
autoload -U promptinit
promptinit
autoload -U colors && colors

# colors (supports older versions of zsh)
ret="%{$fg[red]%}"
user="%{$fg[blue]%}"
host="%{$fg[yellow]%}"
dir="%{$fg[green]%}"
repo="%{$fg[magenta]%}"

# reset color
rs="%{$reset_color%}"

function current_branch {
    [[ $(git diff --shortstat 2> /dev/null | tail -n1) != "" ]] && echo -n "*"
    echo $(git rev-parse --abbrev-ref HEAD 2> /dev/null)
}

function precmd {

branch="$(current_branch)"
if [ "$branch" != "" ]; then 
    branch="on ${repo}$branch${rs}"
fi

PROMPT="${ret}%(?..%? )${rs}${user}%n${rs} at ${host}%m${rs} in ${dir}%~${rs} ${branch}
%# "

}

# load aliases
if [ -f ~/.aliases ]; then
    source ~/.aliases
fi

if which ruby >/dev/null && which gem >/dev/null; then
    PATH="$(ruby -rubygems -e 'puts Gem.user_dir')/bin:$PATH"
fi

# history settings
HISTFILE=~/.histfile
HISTSIZE=5000
SAVEHIST=10000

source ~/.profile
source ~/dotfiles/.z/z.sh

###-begin-npm-completion-###
#
# npm command completion script
#
# Installation: npm completion >> ~/.bashrc  (or ~/.zshrc)
# Or, maybe: npm completion > /usr/local/etc/bash_completion.d/npm
#

COMP_WORDBREAKS=${COMP_WORDBREAKS/=/}
COMP_WORDBREAKS=${COMP_WORDBREAKS/@/}
export COMP_WORDBREAKS

if type complete &>/dev/null; then
  _npm_completion () {
    local si="$IFS"
    IFS=$'\n' COMPREPLY=($(COMP_CWORD="$COMP_CWORD" \
                           COMP_LINE="$COMP_LINE" \
                           COMP_POINT="$COMP_POINT" \
                           npm completion -- "${COMP_WORDS[@]}" \
                           2>/dev/null)) || return $?
    IFS="$si"
  }
  complete -F _npm_completion npm
elif type compdef &>/dev/null; then
  _npm_completion() {
    si=$IFS
    compadd -- $(COMP_CWORD=$((CURRENT-1)) \
                 COMP_LINE=$BUFFER \
                 COMP_POINT=0 \
                 npm completion -- "${words[@]}" \
                 2>/dev/null)
    IFS=$si
  }
  compdef _npm_completion npm
elif type compctl &>/dev/null; then
  _npm_completion () {
    local cword line point words si
    read -Ac words
    read -cn cword
    let cword-=1
    read -l line
    read -ln point
    si="$IFS"
    IFS=$'\n' reply=($(COMP_CWORD="$cword" \
                       COMP_LINE="$line" \
                       COMP_POINT="$point" \
                       npm completion -- "${words[@]}" \
                       2>/dev/null)) || return $?
    IFS="$si"
  }
  compctl -K _npm_completion npm
fi
###-end-npm-completion-###
