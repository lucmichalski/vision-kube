# Author: Kevin McGuinness <kevin.mcguinness@dcu.ie>
#
from django.http import HttpResponse
from functools import wraps
from django.conf.urls import url, patterns
from django.views.decorators.csrf import csrf_exempt
from django.http import HttpResponse
from django.http import Http404
from django.contrib.auth import authenticate
from django.contrib.auth.models import AnonymousUser
from serializer import serialize
from functools import wraps

import json
import binascii
import logging
import jsonrpclib

log = logging.getLogger('axesresearch')

class DjangoAuthentication(object):
    def __init__(self, realm='API'):
        self.realm = realm
    
    def is_authenticated(self, request):
        if request.user.is_authenticated():
            return True
        return self.http_basic_authenticate(request)
        
    def http_basic_authenticate(self, request):
        auth_string = request.META.get('HTTP_AUTHORIZATION', None)
        if not auth_string:
            return False
        try:
            authmeth, auth = auth_string.split(" ", 1)
            if not authmeth.lower() == 'basic':
                return False
            auth = auth.strip().decode('base64')
            username, password = auth.split(':', 1)
        except (ValueError, binascii.Error):
            return False
        request.user = authenticate(
            username=username, 
            password=password) or AnonymousUser()
        return not request.user in (False, None, AnonymousUser())
    
    def challenge(self):
        resp = HttpResponse("Authorization Required")
        resp['WWW-Authenticate'] = 'Basic realm="%s"' % self.realm
        resp.status_code = 401
        return resp

    def __repr__(self):
        return u'<DjangoAuthentication: realm=%s>' % self.realm
    
class Endpoint(object):
    def __init__(self, handler, name=None, pattern=None, method='GET',
        authentication = DjangoAuthentication()):
        self.handler = handler
        if name is None:
            name = self.handler.__name__
        if pattern is None:
            pattern = r'^{}/$'.format(name.lower())
        self.pattern = pattern
        self.name = name
        self.method = method
        self.authentication = authentication
    
    def __call__(self, request, *args, **kwargs):        
        if request.method != self.method:
            raise Http404
        if self.authentication:
            if not self.authentication.is_authenticated(request):
                return self.authentication.challenge()
        if request.method == 'POST':
            data = json.loads(request.body)
            result = self.handler(request, data, *args, **kwargs)
        else:
            result = self.handler(request, *args, **kwargs)
            
        status_code = 200
        if result is not None:
            if isinstance(result, tuple):
                status_code, result_data = result
            else:
                result_data = result
            data = serialize(result_data)
        else:
            data = {'status': "OK"}
        response = HttpResponse(data, status=status_code, 
            content_type='application/json')
        response['Content-Length'] = len(data)
        return response    

class Api(object):
    instance = None
    
    def __init__(self, prefix = ''):
        self.prefix = prefix
        self.endpoints = {}
        self.instance = self
        
    def register(self, endpoint):
        self.endpoints[endpoint.name] = endpoint
    
    @csrf_exempt
    def route(self, request, *args, **kwargs):
        endpoint = self.endpoints[request.resolver_match.url_name]
        return endpoint(request, *args, **kwargs)
    
    @property
    def patterns(self):
        from django.conf.urls import patterns as djangopatterns
        urls = [url(ep.pattern, self.route, name=ep.name) 
            for ep in self.endpoints.values()]
        return djangopatterns(self.prefix, *urls)
        
    def endpoint(self, *args, **kwargs):
        def decorator(func):
            self.register(Endpoint(func, *args, **kwargs))
            return func
        return decorator
        
    def resource(self, resource):
        funcs = [
            ('list', 'GET', r'^{}/list/$'),
            ('get', 'GET', r'^{}/get/(?P<id>[^/]+)/$'),
            ('create', 'POST', r'^{}/create/$'),
            ('update', 'POST', r'^{}/update/$'),
            ('delete', 'GET', r'^{}/delete/(?P<id>[^/]+)/$'),
        ]
        instance = resource()
        for func_name, method, p in funcs:
            try:
                func = getattr(instance, func_name)
            except AttributeError:
                print func_name
                continue
            name = instance.name + '.' + func_name
            pattern = p.format(instance.name)
            endpoint = Endpoint(func, name, pattern, method)
            self.register(endpoint)
        return resource

def dict2obj(d):
    top = type('new', (object,), d)
    seqs = tuple, list, set, frozenset
    for i, j in d.iteritems():
        if isinstance(j, dict):
            setattr(top, i, dict2obj(j))
        elif isinstance(j, seqs):
            setattr(top, i, 
                type(j)(dict2obj(sj) if isinstance(sj, dict) else sj for sj in j))
        else:
            setattr(top, i, j)
    setattr(top, 'as_dict', d)
    return top
    
def log_exceptions(func):
    """
    Decorator for logging exceptions before propagating them
    """
    def get_message(exception):
        # jsonrpclib puts the interesting part in a weird place
        if isinstance(exception, jsonrpclib.ProtocolError):
            return exception.args[0][1]
        return str(exception)
    
    def log_exception(func, args, kwargs, exception):
        exception_type = exception.__class__.__name__
        func_name = func.__name__
        log.error('%s in %s()', exception_type, func_name)
        log.error('%s args: %s', func_name, args)
        log.error('%s kwargs: %s', func_name, kwargs)
        log.error('%s message: %s', exception_type, get_message(exception))  
    
    @wraps(func)
    def wrapper(*args, **kwargs):
        try:
            return func(*args, **kwargs)
        except Exception, e:
            log_exception(func, args, kwargs, e)
            raise
    return wrapper

def user_info(request):
    if request.user and request.user.is_authenticated():
        return  {
            'username': request.user.username, 
            'email': request.user.email, 
            'firstName': request.user.first_name,
            'lastName': request.user.last_name,
            'lastLogin': request.user.last_login,
            'staff': request.user.is_staff
        } 
    return None