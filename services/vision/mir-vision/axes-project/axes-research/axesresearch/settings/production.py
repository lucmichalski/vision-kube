# Production settings for axesresearch project.
#
# Author: Kevin McGuinness <kevin.mcguinness@dcu.ie>
#
from defaults import *

DEBUG = False
TEMPLATE_DEBUG = DEBUG

ALLOWED_HOSTS = ['*']

# Import site specific configuration, if available
try:
    from local import *
except ImportError:
    pass
