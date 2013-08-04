# dotfiles

A simple repository for all my configuration files.

## usage

To apply all of the configurations, simply run the commands:

    git clone https://github.com/djblue/dotfiles.git

and,

    cd dotfiles && ./install.sh

This will copy down all of the configuration files and update all of the
vim plug-ins. Configuration files are backed up into dotfiles-old and
symlinks are created in their place that point into the configuration
files in this repository.

## vim plug-ins

The current plug-ins are listed in vim.sh. All plugins are treated as git
submodules, which makes it very easy to upgrade.

### Adding

To add plug-ins add an entry into the vim.sh script and run the script:

    ./vim.sh

### Removing

To remove plug-ins, simply delete the folder in the vim/bundle directory
and remove the entry in the vim.sh script.

### Upgrading

All plug-ins are upgraded as when you run the install.sh script. So to
update again, simply run the script again.
