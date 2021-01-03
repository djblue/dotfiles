" Automatric reloading of .vimrc
autocmd! bufwritepost .vimrc source %

set nocompatible              " be iMproved, required
filetype off                  " required
set modeline
set modelines=2
set hidden

" set the runtime path to include Vundle and initialize
set rtp+=~/.vim/bundle/Vundle.vim
call vundle#begin()

" let Vundle manage Vundle, required
Plugin 'VundleVim/Vundle.vim'

Plugin 'scrooloose/nerdtree'
Plugin 'tpope/vim-fugitive'
Plugin 'airblade/vim-gitgutter'
Plugin 'jelera/vim-javascript-syntax'
Plugin 'tpope/vim-markdown'
Plugin 'vim-airline/vim-airline'
Plugin 'vim-airline/vim-airline-themes'
Plugin 'tpope/vim-surround'
Plugin 'ctrlpvim/ctrlp.vim'
Plugin 'ConradIrwin/vim-bracketed-paste'
Plugin 'jpalardy/vim-slime'
Plugin 'luochen1990/rainbow'
Plugin 'vim-scripts/paredit.vim'
Plugin 'tpope/vim-fireplace'
"Plugin 'Olical/conjure', { 'tag': 'v4.3.1' }
Plugin 'leafgarland/typescript-vim'
Plugin 'Quramy/tsuquyomi'
Plugin 'ervandew/supertab'
Plugin '{{:theme/vim-plugin}}'
Plugin 'masukomi/vim-markdown-folding'
Plugin 'dense-analysis/ale'

" All of your Plugins must be added before the following line
call vundle#end()            " required
filetype plugin indent on    " required
set omnifunc=syntaxcomplete#Complete
let g:SuperTabDefaultCompletionType = "<C-X><C-O>"

set updatetime=100

let g:rainbow_active = 1
let g:rainbow_conf = {
      \ 'ctermfgs': ['darkred', 'darkblue', 'gray', 'lightblue', 'darkmagenta']
      \ }

function! Cljfmt()
  normal ix
  normal x
  undojoin
  let save_pos = winsaveview()
  %!cljfmt
  call winrestview(save_pos)
endfunction

" clojure code formatting
if executable('cljfmt')
  command! -nargs=0 Cljfmt call Cljfmt()
  " autocmd BufWritePre *.clj,*.cljs,*.cljc,*.edn, call Cljfmt()
  noremap ff :Cljfmt<CR>
endif

let g:ale_linters = {'clojure': ['clj-kondo']}

let g:clojure_syntax_keywords = {
    \ 'clojureDefine': ["def"],
    \ }

" port vim-fireplace behavior to conjure
let g:conjure#mapping#eval_current_form = ["cpp"]
let g:conjure#mapping#eval_replace_form = ["c!!"]
let g:conjure#log#botright = v:true
command! -nargs=0 Require normal <localleader>ef
command! -nargs=0 Last normal <localleader>lv
command! -nargs=1 CljEval execute ":ConjureEval" <f-args>

let g:slime_target = "tmux"

let g:ctrlp_user_command =
  \ ['.git', 'cd %s && git ls-files -co --exclude-standard']

" The Silver Searcher
if executable('ag')
  " Use ag over grep
  set grepprg=ag\ --nogroup\ --nocolor

  " Use ag in CtrlP for listing files. Lightning fast and respects .gitignore
  let g:ctrlp_user_command = 'ag %s -l --nocolor -g ""'

  " " ag is fast enough that CtrlP doesn't need to cache
  let g:ctrlp_use_caching = 0
endif

let g:ctrlp_cmd='CtrlP :pwd'
let g:ctrlp_max_files=0
let g:ctrlp_max_depth=40
noremap <C-f> :CtrlPLine<CR>

" markdown folding
if has("autocmd")
  filetype plugin indent on
endif
autocmd FileType markdown set foldexpr=NestedMarkdownFolds()

" snippets
let g:UltiSnipsExpandTrigger="<tab>"
let g:UltiSnipsJumpForwardTrigger="<tab>"
let g:UltiSnipsJumpBackwardTrigger="<s-tab>"

let g:UltiSnipsEditSplit="vertical"

" always show that status line
set laststatus=2

" faster redrawing
set ttyfast

" Set the boolean number option to true set number set number
set number

" Highlight current line
set cursorline

" Set the text width option to '74'
set tw=74 wrap linebreak

" Set the tab options
set smarttab
set autoindent
set tabstop=2
set shiftwidth=2
set expandtab
set visualbell

" normal tabs for Makefile and go
autocmd FileType make setlocal noexpandtab
autocmd FileType go setlocal noexpandtab

" Set incremental search
set incsearch
set ignorecase
set smartcase
" Highlighting
set hlsearch

" wildmode
set wildmode=longest,list

" Folding settings
set foldmethod=marker
set foldnestmax=10
set nofoldenable
set foldlevel=0

" Spelling
set spelllang=en_us

" Disable swap files
set noswapfile
set nowritebackup
" Disable backup copy
set backupcopy=yes

" Colors
set t_Co=256
syntax enable
try
  colorscheme {{:theme/name}}
  set background={{:theme/type}}
catch /^Vim\%((\a\+)\)\=:E185/
  " ohh well
endtry

" enable mouse scrolling
set mouse=a

" fix copy paste in vim for tmux
set clipboard=unnamed

" Disable ex-mode
map Q <Nop>

" It hurts to hit \ all of the time :-(
let mapleader=','

" Quick save command
noremap  <leader>s :update<CR>
"vnoremap <leader>s :update<CR>
"inoremap <leader>s :update<CR>

" Creating new tabs
map <leader>n <Esc>:tabnew<CR>
map gn <Esc>:tabnew<CR>
map gl <Esc>:tabnext<CR>
map gh <Esc>:tabprev<CR>

" Quit
noremap <leader>q :quit<CR>
noremap <leader>Q :quitall<CR>

" Highlight searching
noremap <leader>c :set nohlsearch<CR>
noremap <leader>C :set hlsearch<CR>

" Exit insert mode
inoremap kj <Esc>
inoremap kJ <Esc>
inoremap Kj <Esc>
inoremap KJ <Esc>

" Exit terminal mode
tnoremap <Esc> <C-\><C-n>
tnoremap kj <C-\><C-n>
tnoremap kJ <C-\><C-n>
tnoremap Kj <C-\><C-n>
tnoremap KJ <C-\><C-n>

" Sort function
vnoremap <leader>r :sort<CR>

" Block indenting
vnoremap > >gv
vnoremap < <gv

" Disable arrow keys to be mean
inoremap <Left>     <NOP>
inoremap <Right>    <NOP>
inoremap <Up>       <NOP>
inoremap <Down>     <NOP>

" Quickly wrap a long line
nnoremap <leader>w mkVgq`k
" Wrap an entire file
noremap <leader>W gg200\w}j
" Quickly unwrap a paragraph
noremap <leader>u mk{jV}kJ`k

" Remove annoying whitespace for line
noremap <leader>e :s/\\s*$//<CR>
" Remove annoying whitespace for file
noremap <leader>E :%s/\s*$//<CR>

" Moving around splits
map <c-j> <c-w>j
map <c-k> <c-w>k
map <c-l> <c-w>l
map <c-h> <c-w>h

" Magnify the current split
noremap <leader>m <C-w>_
" Restore split windows
noremap <leader>r <C-w>=

" I can finally paste again.
nnoremap <F2> :set invpaste paste?<CR>
set showmode

" Toggle spelling
imap <Leader>s <C-o>:setlocal spell!<CR>
nmap <Leader>s :setlocal spell!<CR>

" Open NERDTree file navigator
noremap <leader>t :NERDTree<CR>

" Shortcut to rapidly toggle `set list`
nmap <leader>l :set list!<CR>

" Open the .vimrc file
command! V :e ~/.vimrc
noremap <leader>v :tabnew ~/.vimrc <CR>
