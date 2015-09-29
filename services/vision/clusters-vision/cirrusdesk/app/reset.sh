#!/bin/bash

rm db.sqlite3
rm -rf ./core/migrations/
./manage.py makemigrations --empty core
./manage.py migrate
./manage.py makemigrations 
./manage.py migrate
./manage.py syncdb --noinput
./utils/load_test_data.py


