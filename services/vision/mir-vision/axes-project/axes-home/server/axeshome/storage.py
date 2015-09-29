#
# (c) Copyright 2015 Kevin McGuinness. All Rights Reserved. 
#
"""
Filesystem storage objects.
"""
import os
import uuid

from flask import current_app as app
from flask import request
from urlparse import urlparse, urlunsplit, urljoin

class MediaStorage(object):
    """
    Represents a HTTP accessible storage location on a locally accessible 
    filesystem path. The constructor reads the variables MEDIA_PATH and 
    MEDIA_URL from the flask application config.
    """
    def __init__(self):
        self.media_url = app.config['MEDIA_URL']
        self.media_path = app.config['MEDIA_PATH']
    
    def get_path(self, fn):
        """
        Get the path to the given filename on the locally accessible filesystem.
        """
        return os.path.join(self.media_path, fn)
    
    def get_url(self, fn):
        """
        Get the relative URL for the given filename.
        """
        return self.media_url + fn
        
    def get_absolute_url(self, fn):
        """
        Returns the absolute URL for the given filename. Assumes that any 
        relative MEDIA_URL has the same hostname as the incoming requests
        url_root and that there is a request object available.
        """
        url = self.get_url(fn)
        
        if urlparse(url).scheme:
            
            # Already have an absolute URL specified in MEDIA_URL
            abs_url = url
        
        else:
            
            # URL has no scheme component. Assume it is relative to incoming
            # request URL
            
            if url.startswith('/'):
                
                # URL path is absolute: use location relative to host base
                parsed_req_url = urlparse(request.url_root)
                base_url = urlunsplit((parsed_req_url.scheme, 
                    parsed_req_url.netloc, '', '', ''))
                
            else:
                
                # URL path is relative: use location relative to url root
                base_url = request.url_root
            
            abs_url = urljoin(base_url, url)
   
        return abs_url
        
    def create_unique_filename(self, extension=None):
        """
        Generate a unique filename with given extension.
        """
        fn = unicode(uuid.uuid4())
        if extension is not None:
            fn += extension
        return fn
        
    def exists(self, fn):
        """
        Returns true if a file with the given name exists.
        """
        return os.path.isfile(self.get_path(fn))
    
    def save(self, data, fn=None, extension=None, abs_url=True):
        """
        Save a file to storage, returning the url and full path to the file as
        a tuple. 
        
        Parameters
        ----------
        
        data : str like
            Data to write to the file.
        fn : str like
            Name of file to use. If None, then a unique filename is generated
        extension : str like
            Extension to append to filename.
        abs_url : bool
            If true, an absolute url is returned. See self.get_absolute_url
            
            
        Returns
        -------
        url : str like
            URL
        full_path: str like
            Full path to file on disk
        """
        
        if fn is None:
            fn = self.create_unique_filename(extension)
        full_path = self.get_path(fn)
        
        with open(full_path, 'w') as f:
            f.write(data)
            
        if abs_url:
            url = self.get_absolute_url(fn)
        else:
            url = self.get_url(fn)
        
        return url, full_path
        