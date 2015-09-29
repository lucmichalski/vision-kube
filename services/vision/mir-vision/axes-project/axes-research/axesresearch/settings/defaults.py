# Django settings for axesresearch project.
#
# Author: Kevin McGuinness <kevin.mcguinness@dcu.ie>
#
from os import path
import sys


BASE_DIR = path.abspath(path.join(path.dirname(__file__), '..'))
ROOT_DIR = path.abspath(path.join(BASE_DIR, '..'))
VERSION = "0.7.5"

ADMINS = (
   ('Admin', 'admin@site.com'),
)

MANAGERS = ADMINS

DATABASES = {
  'default': {
    'ENGINE': 'django.db.backends.sqlite3', 
    'NAME': path.join(ROOT_DIR, 'db/axesresearch.db'), 
    'USER': '',
    'PASSWORD': '', 
    'HOST': '', 
    'PORT': '',
  }
}

# Mongo DB database name
DATABASE_NAME = 'axesresearch'

# Hosts/domain names that are valid for this site; required if DEBUG is False
# See https://docs.djangoproject.com/en/1.4/ref/settings/#allowed-hosts
ALLOWED_HOSTS = ['*']

TIME_ZONE = 'Europe/Dublin'
LANGUAGE_CODE = 'en-us'
SITE_ID = 1
USE_I18N = True
USE_L10N = True
MEDIA_ROOT = path.join(ROOT_DIR, 'media/')
MEDIA_URL = '/media/'
STATIC_ROOT = path.join(ROOT_DIR, 'static/')
STATIC_URL = '/static/'
ADMIN_MEDIA_PREFIX = '/static/admin/'
LOGIN_REDIRECT_URL = 'index'
LOGIN_URL = 'login'

STATICFILES_DIRS = (
    path.join(BASE_DIR, 'ui/static'),
)

STATICFILES_FINDERS = (
    'django.contrib.staticfiles.finders.FileSystemFinder',
    'django.contrib.staticfiles.finders.AppDirectoriesFinder',
#   'django.contrib.staticfiles.finders.DefaultStorageFinder',
)

# List of callables that know how to import templates from various sources.
TEMPLATE_LOADERS = (
    'django.template.loaders.filesystem.Loader',
    'django.template.loaders.app_directories.Loader',
#   'django.template.loaders.eggs.Loader',
)

MIDDLEWARE_CLASSES = (
    'django.middleware.common.CommonMiddleware',
    'django.contrib.sessions.middleware.SessionMiddleware',
    'django.middleware.csrf.CsrfViewMiddleware',
    'django.contrib.auth.middleware.AuthenticationMiddleware',
    'django.contrib.messages.middleware.MessageMiddleware',
    'axesresearch.middleware.ConsoleExceptionMiddleware',
)

APPEND_SLASH = True

ROOT_URLCONF = 'axesresearch.urls'

WSGI_APPLICATION = 'axesresearch.wsgi.application'

TEMPLATE_DIRS = (
)

INSTALLED_APPS = (
    'django.contrib.auth',
    'django.contrib.contenttypes',
    'django.contrib.sessions',
    'django.contrib.sites',
    'django.contrib.messages',
    'django.contrib.staticfiles',
    'axesresearch.ui',
    'axesresearch.api',
    'django.contrib.admin',
    'django.contrib.admindocs',
    'djcelery'
)

AUTHENTICATION_BACKENDS = (
    'django.contrib.auth.backends.ModelBackend',
)

LOG_FILE = path.join(ROOT_DIR, 'logs', 'axesresearch.log')

LOGGING = {
  'version': 1,
  'disable_existing_loggers': False,
  'formatters': {
    'verbose': {
      'format': '%(levelname)s %(asctime)s %(module)s %(process)d %(thread)d %(message)s'
    },
    'simple': {
      'format': '%(levelname)s %(message)s'
    },
    'multiline': {
      'format': '\n%(asctime)s %(asctime)s:: \n%(message)s\n'
    }
  },
  'handlers': { 
    'console': {
      'level':'INFO',
      'class':'logging.StreamHandler',
      'formatter': 'simple',
     },
     'file': {
       'level':'DEBUG',
       'class':'logging.FileHandler',
       'formatter': 'verbose',
       'filename': LOG_FILE
     },
  },
  'loggers': { 
    'axesresearch': {
      'handlers': ['console', 'file'],
      'level': 'DEBUG',
    },
  },
}

DEFAULT_COLLECTION = 'abc'

DEFAULT_USER_PREFERENCES = {
    'showFeedbackButtons': True,
    'defaultResultView': 'Detailed',
    'defaultResultType': 'Segment',
    'resultsPerPage': 50
}

import djcelery
djcelery.setup_loader()

BROKER_URL = 'mongodb://localhost:27017/celery_broker'
CELERY_RESULT_BACKEND = "mongodb"

LIMAS_PREPEND_URI_SLASH = True
LIMAS_CACHE_ENABLED = True

LIMAS_STATS = {
    'entities':100, 
    'contributors':100, 
    'Keywords':100, 
    'Genre':100, 
    'publicationYear':100,
    'persons':100
}

LIMAS_RESPONSE_POSTPROCESSING_RULES = { }

UI_FILTERS = [
  ('Keywords', 'Keywords', 'selectedKeywords', 'customKeywordsText', 'commonKeywords'),
  ('Names', 'entities', 'selectedEntities', 'customEntitiesText', 'commonEntities'),
  ('Categories/Genres', 'Genre', 'selectedGenres', 'customGenreText', 'commonGenres'),
]

UI_ENABLE_MAGIC_SEARCH = True
UI_TEXT_SEARCH_TYPES = ['meta', 'speech', 'meta speech']
UI_VISUAL_SEARCH_TYPES = ['category-t', 'face-t', 'instance-t']
UI_IMAGE_SEARCH_TYPES = ['instance-i', 'face-i']

