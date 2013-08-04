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

"let g:Powerline_theme='short'
let g:Powerline_colorscheme='solarized16_dark'

" Colors
"call togglebg#map("<F5>")
syntax enable
"let g:solarized_termtrans=0.85
"let g:solarized_termcolors=256
set background=dark
colorscheme solarized 

" Quick save command
noremap  <leader>s :update<CR>
vnoremap <leader>s :update<CR>
inoremap <leader>s :update<CR>


" Creating new tabs 
map <leader>n <Esc>:tabnew<CR>
map gn <Esc>:tabnew<CR>
map gl <Esc>:tabnext<CR>
map gh <Esc>:tabprevious<CR>

" Quit
noremap <leader>q :quit<CR>
noremap <leader>Q :quitall<CR>

" Clear highlight searching
noremap <leader>c :set nohlsearch<CR>

" Exit insert mode
inoremap kj <Esc>

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

" It hurts to hit \ all of the time :-(
let mapleader=','

" Quickly wrap a long line
nnoremap <leader>w mkVgq`k 
" Wrap an entire file
noremap <leader>W gg200\w}j
" Quickly unwrap a paragraph
noremap <leader>u mk{jV}kJ`k

" Moving around splits
map <c-j> <c-w>j
map <c-k> <c-w>k
map <c-l> <c-w>l
map <c-h> <c-w>l

" Magnify the current split
noremap <leader>m <C-w>_
" Restore split windows
noremap <leader>r <C-w>=

" Open the .vimrc file
" command V :e ~/.vimrc

" save and upload file
command U :w | :! ./server push
