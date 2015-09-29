# -*- coding: utf-8 -*-
from __future__ import unicode_literals, print_function
from .base import *

# Database
DATABASES = {
    'default': env.db(default='sqlite:///%s' % os.path.join(BASE_DIR.root, 'db.sqlite3'))
}

# Allowed hosts
ALLOWED_HOSTS = [
    '*',
]


# Kong
KONG_ADMIN_URL = env('KONG_ADMIN_URL')
KONG_ADMIN_SIMULATOR = env('KONG_ADMIN_SIMULATOR', default=False)

# Cache
REDIS_CACHE_URL = env('REDIS_CACHE_URL', default=None)
if REDIS_CACHE_URL is not None:
    CACHES = {
        "default": {
            "BACKEND": "django_redis.cache.RedisCache",
            "LOCATION": REDIS_CACHE_URL,
            "OPTIONS": {
                "CLIENT_CLASS": "django_redis.client.DefaultClient",
            }
        }
    }

    # Session Cache
    SESSION_ENGINE = "django.contrib.sessions.backends.cache"
    SESSION_CACHE_ALIAS = "default"

# Static Files
STATIC_ROOT = env('STATIC_ROOT', default='/data/static')
STATIC_URL = env('STATIC_URL', default='/static/')

# Logging
LOGGING = {
    'version': 1,
    'disable_existing_loggers': False,
    'handlers': {
        'console': {
            'level': 'DEBUG',
            'class': 'logging.StreamHandler',
            'formatter': 'verbose',
        },
    },
    'formatters': {
        'verbose': {
            'format': (
                '%(asctime)s [%(process)d] [%(levelname)s] ' +
                'pathname=%(pathname)s lineno=%(lineno)s ' +
                'funcname=%(funcName)s %(message)s'),
            'datefmt': '%Y-%m-%d %H:%M:%S'
        },
        'simple': {
            'format': '%(levelname)s %(message)s',
        },
    },
    'loggers': {
        'django': {
            'handlers': ['console'],
        }
    }
}
