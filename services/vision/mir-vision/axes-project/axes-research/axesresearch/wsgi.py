# Apache mod_wsgi setup file
#
# Author: Kevin McGuinness <kevin.mcguinness@dcu.ie>
#
import os
import sys
import site

old_sys_path = list(sys.path)
base_dir = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
py_version = '%d.%d' % (sys.version_info[0], sys.version_info[1])

# Setup virtual environment
venv_path = os.path.join(base_dir, 
  'venv/lib/python%s/site-packages' % py_version)
print >> sys.stderr, 'virtual env path:', venv_path
site.addsitedir(venv_path)

# Setup python path
sys.path.append(base_dir)

# Reorder sys path 
new_sys_path = [p for p in sys.path if p not in old_sys_path]
for item in new_sys_path:
  sys.path.remove(item)
sys.path[:0] = new_sys_path

# Set location of the settings module
os.environ["DJANGO_SETTINGS_MODULE"] = "axesresearch.settings"

# Launch django WSGI application handler
from django.core.wsgi import get_wsgi_application
application = get_wsgi_application()

# ex:sw=2:ts=2:et:
