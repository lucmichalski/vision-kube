#!/bin/sh
#
# Boostrap the virtual environment, install dependencies,
# create folders, initialize the database, and set permissions.
#

fatal() {
    echo; echo >&2 "ERROR: $*"; echo; exit 1
}

install_dependency() {
    pip install $1 || {
        fatal "Installation of $1 failed. Aborting."
    }
}

banner() {
    echo; echo "\033[1m>> $*\033[0m"; echo 
}

check_command() {
    command -v $* >/dev/null 2>&1
}


#
# Ensure virtualenv is installed and available
#
check_command virtualenv || { 
    fatal "Please install virtualenv first"
}

#
# Create virtual environment
#
banner "Creating virtual environment"
virtualenv --no-site-packages venv || {
    fatal "Virtual environment installation failed"
}

#
# Activate virtual environment
#
banner "Activating virtual environment"
. ./venv/bin/activate
echo "activated"

#
# Ensure pip is available
#
banner "Checking for pip"
check_command pip || {
    fatal "pip unavailable. You probably need a newer version of virtualenv."
}
echo "pip found"

#
# Install all dependencies and tools
#
banner "Installing dependencies"
install_dependency ipython
install_dependency "django==1.6.11"
install_dependency gunicorn
install_dependency gevent
install_dependency pymongo
install_dependency requests
install_dependency jsonrpclib
install_dependency pyyaml
install_dependency paver
install_dependency supervisor
install_dependency celery
install_dependency django-celery
install_dependency Pillow

#
# Create folders for log files and database
#
banner "Creating directories"
mkdir -p logs
mkdir -p db
echo "directories created"

#
# Run syncdb to create the initial database
#
banner "Initializing database"
python manage.py syncdb || {
    fatal "Error initializing the database"
}
echo "database initialized"

#
# Setup permissions to allow the Apache process to write to the logs and 
# database. 
#
# Note: this is only needed for a mod_wsgi setup, and a better way of
# doing this would just be to change the group of the files to be one that
# the apache process is a member of and then to change the permissions to
# make these files group writable. But on some systems, you need sudo for
# that, and the name of the group apache is a member of varies from distro
# to distro. This is reliable, but very insecure.
#
#
banner "Setting permissions"
chmod 777 db
chmod a+w db/axesresearch.db 
chmod a+w logs
touch logs/axesresearch.log
chmod a+w logs/axesresearch.log
echo "done"

#
# Echo any additional instructions
#
banner "Setup complete"
cat <<EOF 
Activate the virtual environment with:
$ . ./venv/bin/activate

EOF