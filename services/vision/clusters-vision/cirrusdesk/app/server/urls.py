from django.conf.urls import patterns, include, url

# Uncomment the next two lines to enable the admin:
from django.contrib import admin
admin.autodiscover()

urlpatterns = patterns('',
    url(r'^/*', include('webclient.urls')),
    url(r'^accounts/', include('registration.backends.default.urls')),
    url(r'^admin/', include(admin.site.urls)),    
)
