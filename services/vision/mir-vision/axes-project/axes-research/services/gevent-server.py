#!/usr/bin/env python
"""
Django gevent server for axes research
"""

import os, sys

def setup_paths():
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
    
def setup_gevent():
    from gevent import monkey
    monkey.patch_all()
    
def setup_app(debug):
    if debug:
        os.environ['DJANGO_SETTINGS_MODULE'] = 'axesresearch.settings'
    else:
        os.environ['DJANGO_SETTINGS_MODULE'] = 'axesresearch.settings.production'
    
def start_server(port, host=''):
    from gevent.wsgi import WSGIServer
    from django.core.handlers.wsgi import WSGIHandler
    WSGIServer((host, port), WSGIHandler()).serve_forever()
    
def parse_args():
    import argparse
    p = argparse.ArgumentParser(description=__doc__.strip())
    p.add_argument('-p', '--port', type=int, default=8088, help='TCP port')
    p.add_argument('-d', '--debug', action='store_true')
    return p.parse_args()

def main():
    setup_paths()
    setup_gevent()
    args = parse_args()
    setup_app(args.debug)
    servertype = 'debug' if args.debug else 'production'
    print 'Starting', servertype, 'server on port', args.port
    start_server(args.port)

if __name__ == '__main__':
    main()    





