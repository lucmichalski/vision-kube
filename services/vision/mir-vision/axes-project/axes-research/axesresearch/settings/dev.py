# Development settings for axesresearch project.
#
# Author: Kevin McGuinness <kevin.mcguinness@dcu.ie>
#
from defaults import *

DEBUG = True
TEMPLATE_DEBUG = DEBUG

# Import site specific configuration, if available
try:
    from local import *
except ImportError:
    pass
