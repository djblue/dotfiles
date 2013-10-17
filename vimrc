" Automatric reloading of .vimrc
autocmd! bufwritepost .vimrc source %

" always show that status line
set laststatus=2

" Set the boolean number option to true set number set number
set number

" Highlight current line
set cursorline 

" Set the text width option to '74'
set tw=74 wrap linebreak

" Set the tab options
set smarttab
set autoindent
set tabstop=4
set shiftwidth=4
set expandtab

 
" Set incremental search
set incsearch
set ignorecase 
set smartcase
" Highlighting
set hlsearch

" wildmode
set wildmode=longest,list

" Plugins
call pathogen#infect()

" Folding settings
set foldmethod=marker
set foldnestmax=10
set nofoldenable
set foldlevel=0

" Spelling
set spell
set spelllang=en_us

" Disable swap files
set noswapfile
set nowritebackup 
" Disable backup copy
set backupcopy=yes

" Set the default color theme
set t_Co=256

" Colors
call togglebg#map("<F5>")
syntax enable
"let g:solarized_termtrans=0.85
"let g:solarized_termcolors=256
set background=dark
colorscheme solarized

let g:Powerline_colorscheme='solarized16_dark'

"Disable ex-mode
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

" Sort function
vnoremap <leader>r :sort<CR>

" Open javascript syntax snippets
noremap <leader>f :tabnew ~/.vim/bundle/snipmate.vim/snippets/javascript.snippets<CR>

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

" Open NERDTree file navigator
noremap <leader>t :NERDTree<CR>

" Open the .vimrc file
command! V :e ~/.vimrc
noremap <leader>v :tabnew ~/.vimrc <CR>

" save and upload file
"command U :w | :! ./server push
