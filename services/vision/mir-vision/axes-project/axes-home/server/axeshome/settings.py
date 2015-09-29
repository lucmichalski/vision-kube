#
# Default configuration for AXES Home
# 
# Values in here can be overridden using the file pointed to by the
# AXESHOME_SETTINGS environment variable.
#
# (c) Copyright 2015 Kevin McGuinness. All Rights Reserved. 
#

# App mode
DEBUG = True
TESTING = False

# Development secret key (edit or override this in settings.cfg!)
SECRET_KEY = 'uatXVUWIFrUcel2abRv9d98WF6uANzBAQrIgcQeoYk68AfLZ'

# MongoDB config
MONGO_HOST = 'localhost'
MONGO_PORT = 27017
MONGO_DBNAME = 'axeshome'

# Location for user media
MEDIA_URL = '/axes/home/media/'
MEDIA_PATH = '../client/media/'

# LIMAS JSON RPC service
SERVICE_URL = 'http://localhost:8091/json-rpc'
LIMAS_PREPEND_URI_SLASH = True

# Dataset info
DATASET_INFO = {
    'id': 'cAXES',
    'name': 'AXES',
    'description': 'The AXES home dataset comprises 2700 hours.',
    'lengthHours': 2714,
    'videoCount': 3520,
    'shotCount': 1647935,
    'keyframeCount': 4608387,
}

# Rules for transforming responses from limas
LIMAS_RESPONSE_POSTPROCESSING_RULES = {
    # 'videoSources.url': [
    #     (r'^http://<server>/collections(.*)$',
    #      r'/collections\1')
    # ]
}

# Logging config
LOGGING = {
    'version': 1,
    'disable_existing_loggers': False,
    'formatters': {
        'simple': {
            'format': '%(levelname)s %(message)s'
        },
        'userlog': {
            'format': '%(asctime)s %(message)s',
            'datefmt': '%Y-%m-%dT%H:%M:%S'
        }
    },
    'handlers': {
        'default': {
            'level':'DEBUG',    
            'class':'logging.StreamHandler',
        },  
        'userlog': {
            'level':'DEBUG',    
            'class': 'logging.FileHandler',
            'filename': 'user.log',
            'formatter': 'userlog'
        },
    },
    'loggers': {
        '': {                  
            'handlers': ['default'],        
            'level': 'INFO',  
            'propagate': True  
        },
        'axeshome': {
            'handlers': ['default'],        
            'level': 'INFO',  
            'propagate': False
        },
        'axeshome.userlog': {
            'handlers': ['userlog'],        
            'level': 'INFO',  
            'propagate': False
        }
    }
}
