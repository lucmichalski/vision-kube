AXES Research Search System
===========================

:Version: 0.7.5

This is the front end to the `AXES <http://www.axes-project.eu/>`_ research
system. The software is a single page web application written in Python using
the `Django <https://www.djangoproject.com/>`_ framework. The front end part is
developed in HTML5, Javascript (with `jQuery <http://jquery.com>`_), and CSS3,
and uses the `KnockoutJS <http://knockoutjs.com>`_ and `SammyJS
<http://sammyjs.org>`_ frameworks. It should be compatible with modern versions
of Safari, Chrome, Firefox, and Opera.

This software was developed as part of the `AXES EU FP7 project
<http://www.axes-project.eu/>`_. This front end needs to be used in conjunction
with the link management and search system developed by AXES.

This software is licensed under the Apache 2 license.


Dependencies
------------

Non-python dependencies (install in advance):

* `mongodb <http://www.mongodb.org/>`_

Python dependencies (installed by ``boostrap.sh``):

* `django <https://www.djangoproject.com/>`_ 1.5.x or 1.6.x
* `pymongo <http://api.mongodb.org/python/current/>`_
* `requests <http://docs.python-requests.org/en/latest/>`_
* `jsonrpclib <https://github.com/joshmarshall/jsonrpclib>`_
* `PIL <http://www.pythonware.com/products/pil/>`_
* `pyyaml <http://pyyaml.org/>`_
* `ffmpeg <http://www.ffmpeg.org/>`_ (for the video cutter service)
* `celery <http://celeryproject.org/>`_
* `django-celery <https://pypi.python.org/pypi/django-celery>`_
* `supervisor <http://supervisord.org/>`_
* `paver <http://paver.github.io/paver/>`_ (optional)


Installation
============

Setup your local settings::

  (venv)$ cp axesresearch/settings/local.py.tmpl axesresearch/settings/local.py
  (venv)$ vi axesresearch/settings/local.py

Bootstrap the virtual environment and install dependencies::

  $ ./boostrap.sh

Create folders for media and static files::

  (venv)$ mkdir -p /var/www/axes-research/media
  (venv)$ mkdir -p /var/www/axes-research/static
  (venv)$ chmod 777 /var/www/axes-research/media

Deploy the static files::

  (venv)$ python manage.py collectstatic

Edit supervisor configuration::

  (venv)$ cp conf/supervisord.conf.tmpl conf/supervisord.conf
  (venv)$ vi conf/supervisord.conf


Starting the services
=====================

The UI relies on several services, such as celery for performing background
tasks and mongodb as a document store. The recommended way of starting these
services is to use `supervisor <http://supervisord.org/>`_. Several `paver
<http://paver.github.io/paver/>`_ tasks are provided to simplify calling
``supervisord`` and ``supervisorctl``. Type ``paver help`` for more info.

To start the services using ``paver``::

  (venv)$ paver start

The above is just an shortcut for using ``supervisord`` directly::

  (venv)$ supervisord -c conf/supervisord.conf

Unless you're using `Apache mod_wsgi <http://code.google.com/p/modwsgi/>`_,
you'll probably want supervisor to start the WSGI application for you too. In
that case, read the options below about how to do this.

Deploying the UI
================

There are several options for running the django user interface. In all cases,
on production deployments, you'll need to host all media and static files using
a standard web server like Apache. The code itself is a Python `WSGI <http://wsgi.readthedocs.org/en/latest/>`_ app, so can
be deployed using any server capable of hosting WSGI applications. The
following gives instructions for three alternatives for deployment

1. `Gunicorn <http://gunicorn.org>`_
2. The `gevent <http://www.gevent.org>`_ WSGI server 
3. `Apache mod_wsgi <http://code.google.com/p/modwsgi/>`_

If you choose to deploy using gunicorn or the gevent WSGI server, it's best to
setup a HTTP proxy server for the services. If using Apache, a proxy server can
be setup using `mod_proxy
<http://httpd.apache.org/docs/2.2/mod/mod_proxy.html>`_. Otherwise, a
standalone one like `nginx <http://www.nginx.org/>`_ can be used. An example
proxy setup for gunicorn can be found `here <http://gunicorn.org/#deployment>`_.
  
Gunicorn
--------

Install gevent and green unicorn::

  (venv)$ pip install gevent
  (venv)$ pip install gunicorn
  
Edit the ``supervisord.conf`` to enable auto-starting the gunicorn server. If 
supervisor is already running, you need to tell it to re-read the config
file and start the services. Using ``paver``::

  (venv)$ paver update

Or directly, using ``supervisorctl``::

  (venv)$ supervisorctl -c conf/supervisord.conf update

Note that it also possible to run green unicorn with synchronous non-gevent
based workers. To do this, edit ``supervisord.conf`` and remove ``-k gevent``
on the gunicorn_django command line.


The gevent WSGI server
----------------------

Install gevent::

  (venv)$ pip install gevent

Edit the ``supervisord.conf`` to enable auto-starting the gevent server. If 
supervisor is already running, you need to tell it to re-read the config
file and start the services. Using ``paver``::

  (venv)$ paver update

Or directly, using ``supervisorctl``::

  (venv)$ supervisorctl -c conf/supervisord.conf update


Apache mod_wsgi
---------------

Create the configuration::

  (venv)$ cp conf/httpd.conf.tmpl conf/httpd.conf
  (venv)$ vi conf/httpd.conf

Enable the web application. E.g. on Ubuntu, you can use ``sites-enabled``::

  (venv)$ ln -s /path/to/axes-research/conf/httpd.conf \
      /etc/apache2/sites-enabled/axes-research

Then restart Apache.


Proxying with nginx
-------------------

If deploying the server using gunicorn, you probably want to proxy it using
Apache or nginx for security and static file hosting. This is relatively
straightforward if deploying the application under the web root path ('/'). If
you need to deploy the application under a non-standard path, you'll need to
make sure that the proxy server sets the ``SCRIPT_NAME`` HTTP header to equal
the root path, as django uses this to figure out things like redirect URLs.

Here's an example of an nginx configuration for a gunicorn based deployment on
port 8002. This goes in the server section::

  location /axes-research-nisvpro/ {
      root /var/www;
  }
  
  location /axes/research/nisvpro/ {
      proxy_pass http://localhost:8002/axes/research/nisvpro/;
      
      proxy_set_header Host $host;
      proxy_set_header X-Real-IP $remote_addr;
      proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
      proxy_set_header X-Forwarded-Protocol $scheme;
      proxy_set_header SCRIPT_NAME /axes/research/nisvpro;
  }

The above assumes that you want to mount the application under
``/axes/research/nisvpro`` and that the ``STATIC_ROOT`` is
``/var/www/axes-research-nisvpro/static/`` and ``STATIC_URL`` is
``/axes-research-nisvpro/static/``.

Note that the script name is appended to the ``proxy_pass`` line above: for
gunicorn based servers, the ``SCRIPT_NAME`` must be a prefix of the path sent
to the application.


Notes
=====

Using newer versions of Apache
-----------------------------

Note that newer versions of Apache requires a different ``httpd.conf``, where ``Allow from all`` is replaced by ``Require all granted``.

Clearing the LIMAS cache
------------------------

You can clear all caches using::

  (venv)$ python manage.py clearcaches


Disabling the LIMAS cache
-------------------------

Add the following to ``axesresearch/settings/local.py``::

  LIMAS_CACHE_ENABLED = False

Running multiple instances of the UI on the same server
-------------------------------------------------------

To run multiple instances of the UI on the same server using different data
collections, you'll need to use different MongoDB database names for each
instance. By default, the database name is set to 'axesresearch'. To change
this, set the DATABASE_NAME in ``axesresearch/settings/local.py``. E.g.::

  DATABASE_NAME = 'axesresearch_nisvpro'

Also, note that if you're using supervisor to launch the services, you'll need
to edit ``supervisord.conf`` so that it doesn't try to autostart multiple mongod
instances.

Post-processing responses from LIMAS
------------------------------------

The setting ``LIMAS_RESPONSE_POSTPROCESSING_RULES`` can be used to apply regular
expression post-processors to the responses returned from LIMAS. For example, the
following rules re-write the thumbnail URL to redirect it to the thumbnailer 
service::

  LIMAS_RESPONSE_POSTPROCESSING_RULES = {
      'thumbnailUrl': [
          (r'^(.*)$', r'http://<server>/thumbs/thumbnail?image=\1')
      ]
  }
  