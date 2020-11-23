set runtimepath^=~/.vim runtimepath+=~/.vim/after
let &packpath = &runtimepath
source ~/.vimrc

" Depend on the latest version via tag.
" Plugin 'Olical/aniseed', { 'tag': 'develop' }

" For Fennel highlighting (based on Clojure).
" Plugin 'bakpakin/fennel.vim'

" Used by the evaluation mappings.
" Plugin 'guns/vim-sexp'

" Highly recommended if you're going to use vim-sexp.
" Plugin 'tpope/vim-sexp-mappings-for-regular-people'

" Initialise all commands and mappings in one go.
" lua require('aniseed.mapping').init()
