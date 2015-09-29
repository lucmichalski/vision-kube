# Author: Kevin McGuinness <kevin.mcguinness@dcu.ie>
#
from django.contrib.auth.decorators import login_required
from django.conf import settings

def expose(urlpattern, name=None, login=True):
    def wrapper(func):
        view_name = name or func.__name__
        if login:
            func = login_required(func)
        func.urlpattern = urlpattern
        func.name = view_name
        return func
    return wrapper
    
def exposed_views(module, prefix=''):
    from django.conf.urls import url, patterns
    p = [url(f.urlpattern, f, name=f.name) for f in module.__dict__.values() 
        if hasattr(f, 'urlpattern')]
    return patterns(prefix, *p)

def get_db():
    import pymongo
    mongo_client = pymongo.MongoClient()
    return mongo_client[settings.DATABASE_NAME]

def uri_to_filename(uri, format):
    if uri.startswith('/'):
        uri = uri[1:]
    if uri.endswith('/'):
        uri = uri[:-1]
    fn = uri.replace('/','-')
    return fn + '.' + format
    
def value_to_ascii(v):
    from unicodedata import normalize
    if isinstance(v, list):
        result =  u', '.join(v)
    else:
        result = unicode(v)
    return normalize('NFKD', result).encode('ascii', 'ignore')
    
def format_time(milliseconds):
    from datetime import timedelta
    t = timedelta(milliseconds=milliseconds)
    return str(t)
