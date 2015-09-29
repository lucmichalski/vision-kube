# Author: Kevin McGuinness <kevin.mcguinness@dcu.ie>
#
"""
Interface to LIMAS JSON RPC API
"""
import jsonrpclib
import pymongo
import hashlib
import utils
import logging
import time

from postprocess import RegexPostprocessor
from django.conf import settings
from query import encode_query

log = logging.getLogger('axesresearch')

class LimasError(Exception):
    def __init__(self, message, cause, **kwargs):        
        message = u'{} cause: {} args:{}'.format(
            message, repr(cause), repr(kwargs))
        super(LimasError, self).__init__(message)
        self.cause = cause
        log.error(self)

class LimasService(object):
    """
    Service wrapper around the LIMAS JSON RPC API
    """
    
    def __init__(self):
        self._service = None
        self._service_type = jsonrpclib.Server
        self._postprocessors = [
            RegexPostprocessor(settings.LIMAS_RESPONSE_POSTPROCESSING_RULES)
        ]
        
    @property
    def service(self):
        # Lazy connection creation
        if self._service is None:
            self._service = self._service_type(settings.SERVICE_URL)
        return self._service
    
    @staticmethod    
    def fix_uri(uri):
        # Limas doesn't like '/' at the end of URIs
        if uri.endswith('/'):
            return uri[:-1]
        # Apache collapses double slashes into single ones
        if settings.LIMAS_PREPEND_URI_SLASH and not uri.startswith('/'):
            uri = '/' + uri
        return uri
        
    @staticmethod
    def to_limas_query_id(id):
        return 'urn:query:{}'.format(id)
        
    @staticmethod
    def make_query_object(query, options):
        query_object = { 
            'id': LimasService.to_limas_query_id(query['_id']),
            'options': options }
        
        is_magic = (query['data']['type'] != 'advanced' and 
            query['data']['textQueryType'] == 'magic')
        
        if is_magic:
            # Use raw query
            query_object['text'] = query['data']['text']
            query_object['queryString'] = query['data']['text']
            query_object['magic'] = True
        else:
            # Use encoded query
            encoder = encode_query(query['data'])
            query_object['text'] = encoder.encoded_query
            query_object['queryString'] = encoder.query_text      
        return query_object
    
    def postprocess(self, response):
        """
        Apply all post-processing transformations to a received response.
        """
        for postprocessor in self._postprocessors:
            response = postprocessor.process(response)
        return response
    
    def lookup_video(self, uri, **kwargs):
        uri = self.fix_uri(uri)
        options = dict(spokenWords=True, metadata=True, entityOccurrences=True)
        options.update(kwargs)
        
        try:
            result = self.service.lookup([uri], options)
        except jsonrpclib.ProtocolError, e:
            raise LimasError('lookup() error', e, 
                uri=uri, options=options)
        
        if len(result) == 0:
            return None
        video = result[0]
        if video is None:
            return None
        if video['videoUri'] != uri:
            if video['videoUri'] is None:
                video['videoUri'] = uri
            else:
                return None
        return self.postprocess(video)
        
    def lookup_segment(self, uri, **kwargs):
        uri = self.fix_uri(uri)
        options = dict(spokenWords=True, metadata=True, entityOccurrences=True)
        options.update(kwargs)
        
        try:
            result = self.service.lookup([uri], options)
        except jsonrpclib.ProtocolError, e:
            raise LimasError('lookup() error', e, 
                uri=uri, options=options) 
        
        if len(result) == 0:
            return None
        segment = result[0]
        if segment is None:
            return None
        if segment['videoUri'] == uri or segment['videoUri'] is None:
            # Result is a video and not a segment
            return None
        return self.postprocess(segment)
        
    def get_available_services(self):
        return map(str, self.service.getAvailableServices())
    
    def lookup_asset(self, uri, **kwargs):
        uri = self.fix_uri(uri)
        segment = self.lookup_segment(uri, **kwargs)
        if segment is None:
            asset_type = 'Video'
            video = self.lookup_video(uri, **kwargs)
        else:
            asset_type = 'Segment'
            video = self.lookup_video(segment['videoUri'], **kwargs)
        asset = {
            'uri': uri,
            'type': asset_type,
            'video': video,
            'segment': segment }
        return self.postprocess(asset)
    
    def search(self, query, **kwargs):
        options = dict(spokenWords=True, metadata=True, limit=200)
        options.update(kwargs)
        query_object = self.make_query_object(query, options)
        try:
            results = self.service.search(query_object)
        except jsonrpclib.ProtocolError, e:
            raise LimasError('search() error', e, query=query_object)
        results['queryId'] = str(query['_id'])
        return self.postprocess(results)
    
    def suggest(self, query, **kwargs):
        options = dict(spokenWords=False, metadata=True)
        options.update(kwargs)
        query_object = self.make_query_object(query, options)
        try:
            entity_scores = self.service.suggestEntities(query_object)
        except jsonrpclib.ProtocolError, e:
            raise LimasError('suggestEntities() error', e, query=query_object)
        uris = list(entity_scores.keys())
        entities = self.service.lookupEntity(uris, options)
        response = {'entities': entities, 'queryId': query['_id']}
        return self.postprocess(response)
    
    def find_related_videos(self, uri, **kwargs):
        uri = self.fix_uri(uri)
        options = dict(metadata=True, limit=10)
        options.update(kwargs)
        try:
            videos = self.service.findRelatedVideos(uri, options)
        except jsonrpclib.ProtocolError, e:
            raise LimasError('findRelatedVideos() error', e, 
                uri=uri, options=options)
        # FIXME: the backend currently ignores the limit option for related
        # videos. This is a temporary hack to fix this. Should be removed
        # when the backend is fixed.
        videos['ranking'] = videos['ranking'][0:options['limit']]
        return self.postprocess(videos)
    
    def find_related_segments(self, uri, **kwargs):
        uri = self.fix_uri(uri)
        options = dict(metadata=True, limit=10)
        options.update(kwargs)
        try:
            segments = self.service.findRelatedSegments(uri, options)
        except jsonrpclib.ProtocolError, e:
            raise LimasError('findRelatedSegments() error', e, 
                uri=uri, options=options)
        # FIXME: the backend currently ignores the limit option for related
        # videos. This is a temporary hack to fix this. Should be removed
        # when the backend is fixed.
        segments['ranking'] = segments['ranking'][0:options['limit']]
        return self.postprocess(segments)
    
    def get_keyframes(self, uri):
        uri = self.fix_uri(uri)
        try:
            kf = self.service.getKeyframes(uri)
        except jsonrpclib.ProtocolError, e:
            raise LimasError('getKeyframes() error', e, uri=uri)
        response = {'keyframes': kf}
        return self.postprocess(response)
    
    def get_transcript(self, uri):
        uri = self.fix_uri(uri)
        try:
            speech = self.service.getSpeechSegments(uri)
        except jsonrpclib.ProtocolError, e:
            raise LimasError('getSpeechSegments() error', e, uri=uri)
        response = {'transcript': speech}
        return self.postprocess(response)
    
    def get_face_tracks(self, uri):
        uri = self.fix_uri(uri)
        try:
            tracks = self.service.getFaceTracks(uri)
        except jsonrpclib.ProtocolError, e:
            raise LimasError('getFaceTracks() error', e, uri=uri)
        response = {'tracks': tracks}
        # No need to postprocess these
        return response 
    
    def get_collection_statistics(self):
        stats = {}
        for k, v in settings.LIMAS_STATS.iteritems():
            try:
                stat = self.service.lookupStat(k, v)
            except jsonrpclib.ProtocolError, e:
                raise LimasError('lookupStat() error', e, name=k, limit=v)
            stats[k] = stat
        response = {'stats': stats}
        return self.postprocess(response)
    
    def submit_feedback(self, query_id, value, **kwargs):
        first = kwargs.pop('first', 0)
        count = kwargs.pop('count', 50)
        limas_query_id = self.to_limas_query_id(query_id)
        try:
            self.service.submitAnnotationForResults(
                limas_query_id, first, count, 'feedback', value)
        except jsonrpclib.ProtocolError, e:
            raise LimasError('submitAnnotationForResults() error', e, 
                query_id=limas_query_id, first=first, count=count, value=value)
    
    def get_last_update_time(self):
        """
        Returns the time that the limas index was last updated, in seconds 
        since the unix epoch.
        """
        try:
            return self.service.getLastChange() / 1000.0
        except jsonrpclib.ProtocolError, e:
            raise LimasError('getLastChange() error', e)
    
    def get_service_info(self):
        try:
            response = self.service.getServiceInfo()
        except jsonrpclib.ProtocolError, e:
            raise LimasError('getServiceInfo() error', e)
        return self.postprocess(response)
    
    def get_version_info(self):
        try:
            response = self.service.getVersionInfo()
        except jsonrpclib.ProtocolError, e:
            raise LimasError('getVersionInfo() error', e)
        return self.postprocess(response)
        
MONGO_ID = '_id'
        
class CachedLimasService(LimasService):
    """
    Locally cached version of the LIMAS JSON RPC using MongoDB as the cache
    """
         
    mongo = pymongo.MongoClient()
    db = mongo[settings.DATABASE_NAME]
    
    caches = (
        'videos', 
        'segments', 
        'assets', 
        'relatedvideos', 
        'relatedsegments',
        'keyframes', 
        'transcripts', 
        'facetracks', 
        'collectionstats',
        'suggestions', 
        'results')
    
    def on_cache_hit(self, dbname, key):
        pass
        
    def on_cache_miss(self, dbname, key):
        pass
        
    def clear_caches(self, caches=None):
        if caches is None:
            caches = self.caches
        for cache in caches:
            # Remove cache
            self.db[cache].remove()
            # Update cache age timestamp
            timestamp = time.time()
            self.db.cacheinfo.update({MONGO_ID: cache}, 
                { '$set': {'age': timestamp} }, upsert=True)
        return caches
        
    def cache_is_invalid(self, cache):
        try:
            last_modified = self.get_last_update_time()
        except LimasError, e:
            # If there is a limas error, assume caches are good. This allows
            # us to pull data from the cache when limas is down
            log.error(e)
            return False
            
        cacheinfo = self.db.cacheinfo.find_one({MONGO_ID: cache})
        if cacheinfo is None:
            # Cache info entry doesn't yet exist: assume caches are invalid
            return True
        
        # Find out the cache age
        cache_age = cacheinfo['age']
        
        # Return true of cache is older than last modified time of limas
        return cache_age < last_modified
        
    def clear_cache_if_invalid(self, cache):
        if self.cache_is_invalid(cache):
            log.info('clearing cache: %s', cache)
            self.clear_caches([cache])
    
    def _cache(self, dbname, func, id, *args, **kwargs):
        self.clear_cache_if_invalid(dbname)
        db = self.db[dbname]
        object = db.find_one({MONGO_ID: id})
        if object is None:
            self.on_cache_miss(dbname, id)
            object = func(self, *args, **kwargs)
            if object is not None:
                object[MONGO_ID] = id
                db.save(object)
        else:
            self.on_cache_hit(dbname, id)
        return object
        
    def _query_key(self, query):
        encoded_query = encode_query(query['data']).encoded_query
        return hashlib.md5(encoded_query.encode('utf-8')).hexdigest()
        
    def lookup_video(self, uri, **kwargs):
        func = LimasService.lookup_video
        return self._cache('videos', func, uri, uri, **kwargs)
        
    def lookup_segment(self, uri, **kwargs):
        func = LimasService.lookup_segment
        return self._cache('segments', func, uri, uri, **kwargs)
        
    def lookup_asset(self, uri, **kwargs):
        func = LimasService.lookup_asset
        return self._cache('assets', func, uri, uri, **kwargs)
        
    def find_related_videos(self, uri, **kwargs):
        func = LimasService.find_related_videos
        return self._cache('relatedvideos', func, uri, uri, **kwargs)
        
    def find_related_segments(self, uri, **kwargs):
        func = LimasService.find_related_segments
        return self._cache('relatedsegments', func, uri, uri, **kwargs)
        
    def get_keyframes(self, uri):
        func = LimasService.get_keyframes
        return self._cache('keyframes', func, uri, uri)
        
    def get_transcript(self, uri):
        func = LimasService.get_transcript
        return self._cache('transcripts', func, uri, uri)
        
    def get_face_tracks(self, uri):
        func = LimasService.get_face_tracks
        return self._cache('facetracks', func, uri, uri)
        
    def get_collection_statistics(self):
        func = LimasService.get_collection_statistics
        return self._cache('collectionstats', func, settings.DEFAULT_COLLECTION)
    
    def suggest(self, query, **kwargs):
        func = LimasService.suggest
        key = self._query_key(query)
        return self._cache('suggestions', func, key, query, **kwargs)
    
    def search(self, query, **kwargs):
        func = LimasService.search
        key = self._query_key(query)
        results = self._cache('results', func, key, query, **kwargs)
        if results is not None:
            # Update segments cache
            if 'segments' in results:
                for uri, segment in results['segments'].iteritems():
                    segment[MONGO_ID] = uri
                    self.db.segments.save(segment)
            # Update videos cache
            if 'videos' in results:
                for uri, video in results['videos'].iteritems():
                    video[MONGO_ID] = uri
                    video['videoUri'] = uri
                    self.db.videos.save(video) 
        return results

# Use cached service if enabled
if settings.LIMAS_CACHE_ENABLED:
    Limas = CachedLimasService
else:
    Limas = LimasService
