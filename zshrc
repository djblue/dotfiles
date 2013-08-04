# autocompletion setting
autoload -Uz compinit
compinit
zstyle :compinstall filename '/home/chris/.zshrc'
zstyle ':completion:*' menu select

# prompt settings
autoload -U promptinit
promptinit
autoload -U colors && colors

# colors
ret='%{%F{red}%}'
user='%{%F{blue}%}'
host='%{%F{yellow}%}'
dir='%{%F{green}%}'
repo='%{%F{magenta}%}'

# reset color
rs='%{%f%}'

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

# aliases
alias ls='ls --color=auto'
alias ll='ls -AlF'
alias la='ls -A'
alias l='ls -CF'

alias vi='vim'

alias grep='grep --color=auto'
alias fgrep='fgrep --color=auto'
alias egrep='egrep --color=auto'

alias s='sudo -E'

# Pacman alias examples
alias pacupg='sudo pacman -Syu'        # Synchronize with repositories before upgrading packages that are out of date on the local system.
alias pacin='sudo pacman -S'           # Install specific package(s) from the repositories
alias pacins='sudo pacman -U'          # Install specific package not from the repositories but from a file 
alias pacre='sudo pacman -R'           # Remove the specified package(s), retaining its configuration(s) and required dependencies
alias pacrem='sudo pacman -Rns'        # Remove the specified package(s), its configuration(s) and unneeded dependencies
alias pacrep='pacman -Si'              # Display information about a given package in the repositories
alias pacreps='pacman -Ss'             # Search for package(s) in the repositories
alias pacloc='pacman -Qi'              # Display information about a given package in the local database
alias paclocs='pacman -Qs'             # Search for package(s) in the local database 

# history settings
HISTFILE=~/.histfile
HISTSIZE=5000
SAVEHIST=10000
