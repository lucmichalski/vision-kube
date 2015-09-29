# Author: Kevin McGuinness <kevin.mcguinness@dcu.ie>
#
"""
Main API endpoints
"""
import pymongo
import tasks
import utils

from django.http import Http404
from celery.result import AsyncResult
from utils import Api
from models import *
from bson.objectid import ObjectId
from django.conf import settings
from backend import Limas
from datetime import datetime

MONGO_ID = '_id'

api = Api("ajax")      
mongo_client = pymongo.MongoClient()
db = mongo_client[settings.DATABASE_NAME]

    
def fetch_topn_assets(query_id, n=5):
    limas = Limas()
    query = db.queries.find_one({MONGO_ID: query_id})
    results = limas.search(query)
    assets = []
    for result in results['ranking']:
        if len(assets) >= n:
            break
        asset = limas.lookup_asset(result['uri'])
        assets.append(asset)
    return assets
    
##    
# Auth API
##

@api.endpoint("login", method='POST', authentication=None)
def auth_login(request, data):
    """
    Login a user in. Password is transmitted base64 encoded, to its best to 
    call this using https.
    """
    from django.contrib.auth import login
    from base64 import b64decode
    from django.contrib.auth import authenticate
    from django.contrib.auth.models import AnonymousUser
    username = data['username']
    password = b64decode(data['password'])
    request.user = authenticate(
        username=username, 
        password=password) or AnonymousUser()
    response = { }
    if not request.user.is_anonymous():
        login(request, request.user)
        response['status'] = 'OK'
        response['logged_in'] = True
        status_code = 200
    else:
        response['status'] = 'Invalid username or password'
        response['logged_in'] = False
        status_code = 401
    response['user'] = utils.user_info(request)
    return status_code, response

@api.endpoint("logout")
def auth_logout(request):
    from django.contrib.auth import logout
    from django.core.urlresolvers import reverse
    logout(request)
    login_page = reverse('login')
    return { 'redirect': login_page }

@api.endpoint("user", method='GET', authentication=None)
def auth_user(request):
    from django.contrib.auth.models import AnonymousUser
    logged_in = request.user and request.user.is_authenticated()
    response =  { 'logged_in': logged_in, 'user': utils.user_info(request) }
    return response

 
##
# Available services API
##
@api.endpoint("available-services", method="GET")
def get_available_services(request):
    services = Limas().get_available_services()
    return services
    
    
##    
# Query API
##

@api.endpoint("query/create", method='POST')
def create_query(request, data):
    query = {}
    query['date'] = datetime.now()
    query['user'] = request.user.username
    query['data'] = data
    print query
    id = db.queries.insert(query)
    return {'queryId': id}
    
@api.endpoint(pattern=r"query/get/(?P<id>[^/]+)/$", method='GET')
def get_query(request, id):
    query = db.queries.find_one({MONGO_ID: ObjectId(id)})
    return query['data']
    
##    
# Search API
##

@api.endpoint(pattern=r"^search/(?P<id>[^/]+)/$", method='GET')
def search(request, id):
    query = db.queries.find_one({MONGO_ID: ObjectId(id)})
    print query
    results = Limas().search(query)
    
    # Attach video statistics
    if results is not None and 'videos' in results:
        for uri, video in results['videos'].iteritems():
            video['stats'] = VideoStats.summary_for_video(uri)
     
    return results

##    
# Suggestions API
##

@api.endpoint(pattern=r"^suggest/(?P<id>[^/]+)/$", method='GET')
def suggest(request, id):
    query = db.queries.find_one({MONGO_ID: ObjectId(id)})
    return Limas().suggest(query)
    
##    
# Related API
##
    
@api.endpoint(pattern=r"^related/videos/(?P<id>.*?)/$", method='GET')
def find_related_videos(request, id):
    return Limas().find_related_videos(id)
    
@api.endpoint(pattern=r"^related/segments/(?P<id>.*?)/$", method='GET')
def find_related_segments(request, id):
    return Limas().find_related_segments(id)
    
##    
# Asset lookup API
##
    
@api.endpoint(pattern=r"^asset/(?P<id>.*?)/$", method='GET')
def asset(request, id):
    asset = Limas().lookup_asset(id)
    video_uri = asset['video']['videoUri']
        
    # Increment view count
    VideoStats.increment_view_count(request.user, video_uri)
    
    # Attach stats
    asset['video']['stats'] = VideoStats.summary_for_video(video_uri)
    return asset 
    
    
##
# Keyframes API
##
    
@api.endpoint(pattern=r"^keyframes/(?P<id>.*?)/$", method='GET')
def keyframes(request, id):
    limas = Limas()
    keyframes = limas.get_keyframes(id)
    keyframes['video'] = limas.lookup_video(id)
    return keyframes
    
##
# Transcripts API
##

@api.endpoint(pattern=r"^transcript/(?P<uri>.*?)/$", method='GET')
def transcript(request, uri):
    return Limas().get_transcript(uri)
    
##
# Face tracks API
##

@api.endpoint(pattern=r"^facetracks/(?P<uri>.*?)/$", method='GET')
def facetracks(request, uri):
    return Limas().get_face_tracks(uri)
    
##
# Collection Statistics API
##
@api.endpoint("collection/stats", method='GET')
def collection_stats(request):
    return Limas().get_collection_statistics()

    
##    
# Feedback API
##

@api.endpoint("feedback/results", method='POST')
def submit_feedback_on_results(request, feedback):
    Limas().submit_feedback(feedback['queryId'], feedback['type'])
    return feedback
    
##    
# Recent API
##

@api.endpoint("videos/recent", method='GET')
def recently_viewed_videos(request):
    limas = Limas()
    user = request.user
    recent = VideoStats.objects \
        .filter(user=request.user) \
        .order_by('-lastViewed') \
        .values_list('videoUri') \
        .distinct()[:10]
    videos = []
    for (videoUri, ) in recent:
        video = limas.lookup_video(videoUri)
        if video:
            video['stats'] = VideoStats.summary_for_video(videoUri)
            videos.append(video)
    return videos

@api.endpoint('searches/recent', method='GET')
def recent_search_results(request):
    
    # Fetch last 10 queries
    queries = list(db.queries.find({
        'user': request.user.username
    }).sort('date', -1)[:10])
    
    history = [{'date': q['date'], 
        'query': q['data'], 'id': q['_id']} for q in queries]
    for result in history:
        try:
            assets = fetch_topn_assets(result['id'], 6)
        except:
            # We skip over any queries causing exceptions so that we still
            # have some results to display even if was is a query that caused 
            # a HTTP 500 in LIMAS due to some problem there.
            continue
        else:
            result['topn'] = assets
    return filter(lambda x: 'topn' in x, history)
    
##    
# Favorites API
##

@api.endpoint('favorites', method='GET')
def list_favorites(request):
    limas = Limas()
    recent = VideoStats.objects \
        .filter(user=request.user, favorite=True) \
        .values_list('videoUri') \
        .distinct()
    videos = []
    for (videoUri, ) in recent:
        video = limas.lookup_video(videoUri)
        video['stats'] = VideoStats.summary_for_video(videoUri)
        videos.append(video)
    return videos

##    
# Annotation API
##

@api.endpoint(pattern="^download/(?P<video_uri>.*?)/$", method='GET')
def download(request, video_uri):
    video_uri = Limas.fix_uri(video_uri)
    VideoStats.increment_download_count(request.user, video_uri)
    return VideoStats.summary_for_video(video_uri)
    
@api.endpoint(pattern="^favorite/(?P<video_uri>.*?)/$", method='GET')
def favorite(request, video_uri):
    video_uri = Limas.fix_uri(video_uri)
    stats, created = VideoStats.objects.get_or_create(
        user=request.user, videoUri=video_uri)
    stats.favorite = True
    stats.save()
    return VideoStats.summary_for_video(video_uri)
    
@api.endpoint(pattern="^like/(?P<video_uri>.*?)/$", method='GET')
def like(request, video_uri):
    video_uri = Limas.fix_uri(video_uri)
    stats, created = VideoStats.objects.get_or_create(
        user=request.user, videoUri=video_uri)
    stats.likes = True
    stats.dislikes = False
    stats.save()
    return VideoStats.summary_for_video(video_uri)

@api.endpoint(pattern="^dislike/(?P<video_uri>.*?)/$", method='GET')
def dislike(request, video_uri):
    video_uri = Limas.fix_uri(video_uri)
    stats, created = VideoStats.objects.get_or_create(
        user=request.user, videoUri=video_uri)
    stats.likes = False
    stats.dislikes = True
    stats.save()
    return VideoStats.summary_for_video(video_uri)
   
##    
# Collections API
##
    
@api.endpoint('collections/list', method='GET')
def list_collections(request):
    return list(db.collections.find({'user': request.user.username}))
        
@api.endpoint('collections/create', method='POST')
def create_collection(request, collection):
    collection.update(user=request.user.username)
    objectId = db.collections.insert(collection)
    return db.collections.find_one({MONGO_ID: objectId})
    
@api.endpoint('collections/update', method='POST')
def update_collection(request, collection):
    collection[MONGO_ID] = ObjectId(collection[MONGO_ID])
    db.collections.save(collection)
    return collection
    
@api.endpoint('collections/remove', method='POST')
def remove_collection(request, collection):
    id = ObjectId(collection[MONGO_ID])
    db.collections.remove({MONGO_ID: id})
    return collection


##    
# Notes API
##
    
@api.endpoint('notes/add', method='POST')
def add_note(request, note):
    note.update(user=request.user.username)
    objectId = db.notes.insert(note)
    return db.notes.find_one({MONGO_ID: objectId})
    
@api.endpoint('notes/update', method='POST')
def update_note(request, note):
    note[MONGO_ID] = ObjectId(note[MONGO_ID])
    db.notes.save(note)
    return note
    
@api.endpoint('notes/remove', method='POST')
def remove_note(request, note):
    id = ObjectId(note[MONGO_ID])
    db.notes.remove({MONGO_ID: id})
    return note

@api.endpoint('notes/list/user', method='GET')
def list_user_notes(request):
    return list(db.notes.find({'user': request.user.username}))

@api.endpoint(pattern=r"^notes/list/video/(?P<video_uri>.*?)/$", method='GET')
def list_video_notes(request, video_uri):
    video_uri = Limas.fix_uri(video_uri)
    
    # Find all public notes for video
    public_notes = db.notes.find({
        'videoUri': video_uri,
        'type': 'public'})
    # Find all private user notes for video
    private_notes = db.notes.find({
        'videoUri': video_uri,
        'user': request.user.username,
        'type': 'private'})
    return list(public_notes) + list(private_notes)

   
##
# Popular videos API
##

@api.endpoint(pattern=r'most/(?P<type>.*?)/$', method='GET')
def popular_videos(request, type):
    
    limas = Limas()
    first = request.GET.get('first', 0)
    count = request.GET.get('count', None)
    
    def fetch_popular_videos(request, order_by):
        results = VideoStats.fetch_popular(order_by, first, count)
        uris = [result['videoUri'] for result in results]
        videos = []
        for videoUri in uris:
            video = limas.lookup_video(videoUri)
            if video:
                video['stats'] = VideoStats.summary_for_video(videoUri)
                videos.append(video)
        return videos
    
    popularity_types = {
        'viewed': 'views',
        'liked': 'likes',
        'disliked': 'dislikes',
        'favorited': 'favorite',
        'downloaded': 'downloads' }
    
    if type in popularity_types:
        videos = fetch_popular_videos(request, popularity_types[type])
        return videos
    else:
        raise Http404

##
# Preferences API
##

@api.endpoint("preferences", method='GET')
def get_preferences(request):
    username = request.user.username
    preferences = db.preferences.find_one({MONGO_ID: username})
    if preferences is None:
        preferences = {MONGO_ID: username, 'user': username}
        preferences.update(settings.DEFAULT_USER_PREFERENCES)
        db.preferences.insert(preferences)
    else:
        preferences['user'] = username
    return preferences
    
@api.endpoint("preferences/update", method='POST')
def update_preferences(request, preferences):
    db.preferences.save(preferences)
    return preferences
    
##
# Virtual cutter API
##

@api.endpoint("cutter/cut", method="POST")
def cut_video(request, options):
    import os
    from urlparse import urlparse
    url = options['videoUrl']
    start_time = options['startTime']
    duration = options['duration']
    filename = os.path.basename(urlparse(url).path)
    output_path = settings.MEDIA_ROOT + filename
    output_url = settings.MEDIA_URL + filename
    result = tasks.cut_video.delay(url, output_path, start_time, duration)
    jobId = db.jobs.insert({
        'type': 'Virtual Cutter',
        'taskId': result.task_id,
        'taskName': result.task_name,
        'owner': request.user.username,
        'status': result.status,
        'videoUrl': url, 
        'downloadUrl': output_url,
        'startTime': start_time,
        'duration': duration,
        'started': datetime.now() })
    return db.jobs.find_one(jobId)
        
@api.endpoint(pattern=r"^cutter/check/(?P<jobId>.*?)/$", method='GET')
def check_cutter_job_status(request, jobId):
    job = db.jobs.find_one(ObjectId(jobId))
    result = AsyncResult(job['taskId'], task_name=job['taskName'])
    job['status'] = result.status
    db.jobs.save(job)
    return job
    
@api.endpoint('cutter/jobs', method='GET')
def list_jobs(request):
    jobs = list(db.jobs.find({
        'owner': request.user.username
    }).sort('started', -1))
    for job in jobs:
        result = AsyncResult(job['taskId'], task_name=job['taskName'])
        job['status'] = result.status
        db.jobs.save(job)
    return jobs
    
@api.endpoint('cutter/clear/completed', method='GET')
def clear_completed_jobs(request):
    db.jobs.remove({
        'owner': request.user.username,
        'status': 'SUCCESS'})
    db.jobs.remove({
        'owner': request.user.username,
        'status': 'FAILURE'})
    return list(db.jobs.find({
        'owner': request.user.username}).sort('started', -1))
        
@api.endpoint(pattern=r'^cutter/remove/(?P<jobId>.*?)/$', method='GET')
def remove_job(request, jobId):
    oid = ObjectId(jobId)
    db.jobs.remove({'_id': oid})
    return list(db.jobs.find({
        'owner': request.user.username}).sort('started', -1))
        
##
# System Information API
##

@api.endpoint('system/info', method='GET')
def system_info(request):
    import sys, os, django, celery
    limas = Limas()
    
    def format_version(info):
        parts = ('majorVersion', 'minorVersion', 'revision')
        return '.'.join(str(info[part]) for part in parts)
    
    try:
        ffmpeg_version = tasks.ffmpeg_version()
    except:
        ffmpeg_version = 'unknown'
    
    try:
        limas_version_info = format_version(limas.get_version_info())
        limas_service_info = limas.get_service_info()
    except (IOError, OSError), e:
        limas_version_info = 'Unknown (connection error: {})'.format(str(e))
        limas_service_info = []

    users = [{
        'username': u.username, 
        'email': u.email, 
        'firstName': u.first_name,
        'lastName': u.last_name,
        'lastLogin': u.last_login,
        'staff': u.is_staff} for u in User.objects.all() ]
    return {
        'os': ' '.join(os.uname()),
        'pythonVersion': sys.version,
        'pythonPath': sys.path,
        'version': settings.VERSION,
        'serverMode': 'debug' if settings.DEBUG else 'production',
        'dbName': settings.DATABASE_NAME,
        'dbVersion': pymongo.version,
        'dbInfo': db.command('dbstats'),
        'dbCollections': db.collection_names(),
        'djangoVersion': django.VERSION,
        'limasURL': settings.SERVICE_URL,
        'limasCacheEnabled': settings.LIMAS_CACHE_ENABLED,
        'limasVersionInfo': limas_version_info,
        'limasServiceInfo': limas_service_info,
        'ffmpegVersion': ffmpeg_version,
        'celeryVersion': celery.__version__,
        'users': users
    }
    
@api.endpoint('system/log', method='GET')
def system_log(request):
    with open(settings.LOG_FILE, 'r') as f:
        lines = f.readlines()
    return [l.strip() for l in lines]
    
##
# Recommendations 
##
@api.endpoint('recommendations', method='GET')
def get_recommendations(request):
    # TODO: Get recommendations from Limas or implement basic rec sys
    # For now, we just randomly recommend from cached videos
    import random
    videos = db.videos.find()
    n_vids = videos.count()
    n_recs = min(n_vids, 10)
    indices = [random.randrange(0, n_vids) for i in xrange(n_recs)]
    indices.sort()
    recommendations = [videos[i] for i in indices]
    return recommendations
    