# to use the ORM models outside of the django server, import this to set things up right 
import os
os.environ["DJANGO_SETTINGS_MODULE"] =  "app.server.settings"
import django
django.setup()