#
# (c) Copyright 2015 Kevin McGuinness. All Rights Reserved. 
#
"""
Various utilities.
"""
from flask.ext.restful import abort
from bson.objectid import ObjectId
from base64 import b64decode
from werkzeug.routing import BaseConverter

class AxesURIConverter(BaseConverter):
    """
       Flask routing converter for AXES URIs of the form::
         
         axes:/path
    
       For example:
       
         axes:/cAXES/v20080512_12...e_clips_investigates/s000000120  
       
       The converter strips off the leading 'axes:' part to return the path
    """
    regex = r'axes:.*?'
    
    def to_python(self, value):
        return value[5:]

supported_mimetypes = {
    'image/png': '.png',
    'image/jpeg': '.jpg',
    'image/bmp': '.bmp',
    'image/gif': '.gif',
}

def find_or_404(collection, objectid):
    try:
        objectid = ObjectId(objectid)
    except:
        abort(404, message="invalid object id")
    item = collection.find_one(objectid)
    if not item:
        error = "resource with id {} does not exist".format(str(objectid))
        abort(404, message=error)
    return item

def clause_type(text):
    try:
        type, value = text.split(':', 1)
    except:
        raise ValueError('Parse error')
    # prepend hash if necessary
    if not type.startswith('#'):
        type = '#' + type
    return { 'type': type, 'text': value }

def parse_data_url(data_url):
    """
    Parse a data url into a tuple of params and the encoded data.
    
    E.g.
    >>> data_url = "data:image/png;base64,ABC123xxx"
    >>> params, encoded_data = parse_data_url(data_url)
    >>> params
    ('image/png', 'base64')
    >>> data
    'ABC123xxx'
    
    """
    # e.g. data:image/png;base64,xxx..
    if not data_url.startswith('data:'):
        raise ValueError('not a data url')
    data_url = data_url[5:]
    params, data = data_url.split(',')
    params = params.split(';')
    return params, data
    
def get_image_data_and_extension_from_data_url(data_url):
    """
    Parse image data encoded in a data URL and return the decoded (raw) data
    and an appropriate file extension to use.
    """
    params, data = parse_data_url(data_url)
    if len(params) < 2:
        raise ValueError('invalid data url: not enough params')
    mimetype = params[0]
    encoding = params[-1]
    if encoding != 'base64':
        raise ValueError('Unsupported encoding: {}'.format(encoding))
    if mimetype not in supported_mimetypes:
        raise ValueError('Unsupported mimetype: {}'.format(mimetype))
    data = b64decode(data)
    extension = supported_mimetypes[mimetype]
    return data, extension
    
        