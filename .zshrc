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
repo="%{$fg[red]%}"

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

# history settings
HISTFILE=~/.histfile
HISTSIZE=5000
SAVEHIST=10000

source ~/.profile
eval "$RUN"
