# UI views
# 
# Author: Kevin McGuinness <kevin.mcguinness@dcu.ie>
#

from django.shortcuts import render
from django.conf import settings
from django.http import HttpResponse
from django.http import HttpResponseRedirect
from django.http import Http404
from django.http import StreamingHttpResponse
from django.contrib.auth import authenticate, login
from django.core.urlresolvers import reverse, get_script_prefix
from urlparse import urlparse
from axesresearch.api.backend import Limas
from axesresearch.utils import expose
from axesresearch.utils import get_db
from axesresearch.utils import uri_to_filename
from axesresearch.utils import value_to_ascii
from axesresearch.utils import format_time
from forms import *
from models import *

import axesresearch.api.endpoints as endpoints
import urllib2
import os
import json

@expose('^$')
def index(request):
    params = {
        'stats': Limas().get_collection_statistics()['stats'],
        'version': settings.VERSION,
        'ui_filters': settings.UI_FILTERS}
    ui_settings = [(name, getattr(settings, name)) 
        for name in dir(settings) 
        if name.startswith('UI_')]
    params.update(ui_settings)
    return render(request, 'index.html', params)
    
@expose('^syslog$')
def syslog(request):
    with open(settings.LOG_FILE, 'r') as f:
        log = f.read()
    return HttpResponse(log, content_type='text/plain')
    
@expose('^sysinfo$')
def sysinfo(request):
    info = endpoints.system_info(request)
    return render(request, 'sysinfo.html', info)
    
@expose('^js/settings.js$', login=False)
def js_settings(request):
    base_url = get_script_prefix()
    
    try:
        available_services = set(Limas().get_available_services())
    except:
        available_services = None
    
    def gather_services(service_names):
        # If service API is unavailable, assume defaults
        if available_services is None:
            return service_names
            
        # Otherwise collect from available services
        result = []
        for name in service_names:
            if '#' + name in available_services:
                result.append(name)
        return result
    
    # Gather services of different types
    text_search_types = gather_services(settings.UI_TEXT_SEARCH_TYPES)
    image_search_types = gather_services(settings.UI_IMAGE_SEARCH_TYPES)
    visual_search_types = gather_services(settings.UI_VISUAL_SEARCH_TYPES)
    
    # meta speech is a combined service, so add it separately
    if (available_services and 
        '#meta' in available_services and 
        '#speech' in available_services):
        text_search_types.append('meta speech')
    
    params = { 
      'apiPrefix': base_url, 
      'baseUrl': base_url,
      'textSearchTypes': text_search_types,
      'imageSearchTypes': image_search_types,
      'visualSearchTypes': visual_search_types
    }
    text = 'settings = ' + json.dumps(params, indent=2)
    response = HttpResponse(text, content_type='application/javascript')
    return response

@expose('^register$', login=False)
def register(request):
    if settings.REGISTRATION_CODE_REQUIRED:
        RegistrationForm = ProtectedUserRegistrationForm
    else:
        RegistrationForm = UserRegistrationForm
    
    if request.method == 'POST':
        if settings.REGISTRATION_CODE_REQUIRED:
            form = RegistrationForm(request.POST)
        else:
            form = RegistrationForm(request.POST)
        if form.is_valid():
            username = form.cleaned_data['username']
            password = form.cleaned_data['password1']
            #print username, password
            form.save()
            user = authenticate(username=username, password=password)
            if user is not None:
                login(request, user)
                
            # Create default collection for user
            db = get_db()
            db.collections.insert({
                'name': 'My Collection',
                'videos': [],
                'user': username
            })    
            
            return HttpResponseRedirect(reverse('index'))    
    else: 
        form = RegistrationForm()
    return render(request, 'register.html', {'form': form})

@expose('^upload$')
def upload(request):
    # The reason we use application/javascript here instead of the more
    # correct application/json is that Firefox shows a Save dialog when
    # it receives something with the mimetype application/json. 
    response = HttpResponse(content_type='application/javascript')
    data = dict(status='Error')
    if request.method == 'POST':
        form = UploadFileForm(request.POST, request.FILES)
        if form.is_valid():
            upload = UserImage(image=form.cleaned_data['image'])
            upload.save()
            # Build an absolute URL for client if we don't have one
            parsed_url = urlparse(upload.image.url)
            if not parsed_url.scheme or not parsed_url.netloc:
                url = request.build_absolute_uri(upload.image.url)
            else:
                url = upload.image.url
            data = dict(status='OK', url=url)
    json.dump(data, response)
    return response
  
@expose('^download$')
def download(request):
    if request.method == 'GET':
        if 'url' not in request.GET:
            raise Http404
        url = request.GET['url']
        response = open_url(url)
        info = response.info()
        content_type = info.getheader('Content-Type')
        filename = os.path.basename(urlparse(url).path)
        response = StreamingHttpResponse(stream_file(response),
             content_type=content_type)
        response['Content-Disposition'] = (
            'attachment; filename={}'.format(filename))
        response['Content-Length'] = info.getheader('Content-Length')
        return response
    raise Http404
    
@expose('^download_metadata$')
def download_metadata(request):
    
    def lookup_asset_metadata(uri):
        asset = Limas().lookup_asset(uri)
        return asset['video']['metadata']
        
    def encode_metadata_json(metadata):
        import json
        return json.dumps(metadata, indent=2)
        
    def encode_metadata_yaml(metadata):
        import yaml
        return yaml.safe_dump(metadata, default_flow_style=False)
        
    def encode_metadata_csv(metadata):
        import csv, StringIO
        keys = metadata.keys()
        keys.sort()
        buffer = StringIO.StringIO()
        writer = csv.writer(buffer, delimiter=':')
        for key in keys:
            writer.writerow([key, value_to_ascii(metadata[key])])
        encoded_data = buffer.getvalue()
        buffer.close()
        return encoded_data
    
    formats = {
        'json': ('application/json', encode_metadata_json),
        'yaml': ('text/x-yaml', encode_metadata_yaml),
        'csv': ('text/csv', encode_metadata_csv),
        'txt': ('text/plain', encode_metadata_yaml)
    }
        
    def encode_metadata(metadata, format):
        if format not in formats:
            raise Http404
        content_type, encode = formats[format]
        return content_type, encode(metadata)
    
    if request.method == 'GET':
        if 'uri' not in request.GET:
            raise Http404
        uri = request.GET['uri']
        format = request.GET.get('format', 'json')
        metadata = lookup_asset_metadata(uri)
        content_type, data = encode_metadata(metadata, format)
        filename = uri_to_filename(uri, format)
        response = HttpResponse(data, content_type=content_type)
        response['Content-Disposition'] = (
            'attachment; filename={}'.format(filename))
        return response
    raise Http404
    
@expose('^download_transcript$')
def download_transcript(request):
    
    def lookup_transcript(uri):
        raw_transcript = Limas().get_transcript(uri)['transcript']
        transcript = []
        for entry in raw_transcript:
            start_time = format_time(entry['startTimeMillis'])
            end_time = format_time(entry['endTimeMillis'])
            words = value_to_ascii(entry['speech']['spokenWords'])
            transcript.append((start_time, end_time, words))
        return transcript
        
    def encode_transcript(transcript):
        import csv, StringIO
        buffer = StringIO.StringIO()
        writer = csv.writer(buffer, delimiter=',')
        for entry in transcript:
            writer.writerow(entry)
        encoded_data = buffer.getvalue()
        buffer.close()
        return encoded_data
    
    if request.method == 'GET':
        if 'uri' not in request.GET:
            raise Http404
        uri = request.GET['uri']
        transcript = lookup_transcript(uri)
        data = encode_transcript(transcript)
        filename = uri_to_filename(uri, 'csv')
        response = HttpResponse(data, content_type='text/csv')
        response['Content-Disposition'] = (
            'attachment; filename={}'.format(filename))
        return response
    raise Http404
    
def stream_file(fobj, block_size=1048576):
    try:
        data = fobj.read(block_size)
        while data:
            yield data
            data = fobj.read(block_size)
    finally:
        fobj.close()
        
def open_url(url):
    try:
        return urllib2.urlopen(url)
    except urllib2.URLError:
        raise Http404
    except urllib2.HTTPError:
        raise Http404
    except ValueError:
        raise Http404
