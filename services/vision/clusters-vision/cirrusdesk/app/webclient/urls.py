from django.conf.urls import include, patterns, url
from webclient import views
from webclient import forms

urlpatterns = patterns('',
    url(r'^$', views.Index, name='index'),
    url(r'^setup_credentials/', views.SetupAwsCredentials, name='setup_credentials'),
    url(r'^workstations/', views.Workstations, name='workstations'),
    url(r'^create/', views.CreateWorkstation, name='create_workstation' ),
    url(r'^stop/(?P<instance_id>i-[0-9a-fA-F]+)', views.Stop, name='stop'),    
    url(r'^start/(?P<instance_id>i-[0-9a-fA-F]+)', views.Start, name='start'),
    url(r'^connect/(?P<instance_id>i-[0-9a-fA-F]+)', views.Connect, name='connect'),
    url(r'^add_storage/(?P<instance_id>i-[0-9a-fA-F]+)', views.AddStorage, name='add_storage'),    
    url(r'^destroy/(?P<instance_id>i-[0-9a-fA-F]+)', views.Destroy, name='destroy_workstation'),
    url(r'^accounts/login/', 'django.contrib.auth.views.login', {'authentication_form': forms.FormLogin}),
    
)