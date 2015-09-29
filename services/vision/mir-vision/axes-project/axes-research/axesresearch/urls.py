# Author: Kevin McGuinness <kevin.mcguinness@dcu.ie>
#
from django.conf.urls import patterns, include, url
from django.contrib.staticfiles.urls import staticfiles_urlpatterns
from django.conf import settings
from django.contrib.auth.views import login, logout
from django.contrib import admin
from axesresearch.utils import exposed_views

import api.urls
import ui.views

admin.autodiscover()

urlpatterns = patterns('',
    url(r'^admin/', include(admin.site.urls)),
    url(r'^api/', include(api.urls)),
    url(r'^login$', login, {'template_name': 'login.html'}, name='login'),
    url(r'^logout$', logout, {'next_page': 'login'}, name='logout'),
)

urlpatterns += exposed_views(ui.views)

def mediafiles_urlpatterns():
    options = { 'document_root': settings.MEDIA_ROOT, 'show_indexes': True }
    media_url = settings.MEDIA_URL
    if media_url.startswith('/'):
        media_url = media_url[1:]
    regex = r'^' + media_url + '(?P<path>.*)$'
    pattern = url(regex, 'django.views.static.serve', options)
    return patterns('', pattern)

if settings.DEBUG:
    # Apache/nginx handles static files in production deployment
    urlpatterns += staticfiles_urlpatterns()
    urlpatterns += mediafiles_urlpatterns()
