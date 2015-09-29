from paver.easy import *
from paver.virtual import virtualenv

supervisord_conf = 'conf/supervisord.conf'
supervisord = 'supervisord -c "{}"'.format(supervisord_conf)
supervisorctl = 'supervisorctl -c "{}"'.format(supervisord_conf)

@task
def start():
    """
    Start all services using supervisord
    """
    sh(supervisord)

@task
def stop():
    """
    Shutdown all services using supervisorctl
    """
    sh('{} shutdown'.format(supervisorctl))

@task
def status():
    """
    Show service status
    """
    sh('{} status'.format(supervisorctl))

@task
def restart():
    """
    Restart all services
    """
    sh("{} restart all".format(supervisorctl))

@task
def update():
    """
    Re-read supervisord.conf and start/stop services as necessary
    """
    sh("{} update".format(supervisorctl))
    
@task
def deployupdate():
    """
    Pull updates with mercurial, update the code, sync the database, and 
    redeploy the static files.
    """
    sh("hg pull")
    sh("hg update")
    sh("python manage.py syncdb")
    sh("python manage.py collectstatic")
    print "Restart Apache if using mod_wsgi"

