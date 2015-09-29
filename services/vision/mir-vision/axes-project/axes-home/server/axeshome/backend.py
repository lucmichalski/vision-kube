#
# (c) Copyright 2015 Kevin McGuinness. All Rights Reserved. 
#
"""
Interface to the link management and search system (LIMAS)
"""
import jsonrpclib
import time
import json
import warnings
import logging

from functools import wraps
from flask import current_app as app

from axeshome.postprocess import RegexPostprocessor

log = logging.getLogger('axeshome')

def encode_query(parsed_query):
    """
    Take a parsed query and encode it to a query string.
    """
    def escape_parens(value):
        return value.replace('(', '\\(').replace(')', '\\)')
    encoded_clauses = []
    for clause in parsed_query['clauses']:
        type = clause['type']
        text = escape_parens(clause['text'])
        encoded_clause = '{}({})'.format(type, text)
        encoded_clauses.append(encoded_clause)
    return ' '.join(encoded_clauses)

def create_limas_query_from_parsed_query(parsed_query):
    """
    Take a parsed query and return a limas query object for it.
    """
    query = {
        'id': 'urn:query:{}'.format(int(time.time()*10)),
        'text': encode_query(parsed_query),
        'queryString': parsed_query['queryText'],
        'magic': False,
        'options': { 'metadata': True, 'spokenWords': True }
    }
    return query
    
def create_magic_search_query_from_string(text):
    query = {
        'id': 'urn:query:{}'.format(int(time.time()*10)),
        'text': text,
        'queryString': text,
        'magic': True,
        'options': { 'metadata': True, 'spokenWords': True }
    }
    return query
    
def clean_metadata(metadata):
    """
    Transform metadata to cleaner version.
    """
    metadata = metadata.copy()
    
    # Fix missing metadata fields
    metadata['description'] = metadata['description'] or metadata['summary']
    metadata['summary'] = metadata['summary'] or metadata['description']
    return metadata
    
def get_evidence_for_item(search_results, item):
    """
    Get evidence fields with positive scores for a particular search result 
    item.
    """
    if search_results.get('evidence', None) is None:
        return []
    item_evidence = []
    for i, score in enumerate(item['scores']):
        if score > 0:
            evidence = search_results['evidence'][i]
            evidence['score'] = score
            item_evidence.append(evidence)
    return item_evidence
    
def collect_results(search_results):
    """
    Translate a limas SearchResult object into a flat unified ranked list
    of segments and videos with simplified and identical data interfaces.
    """
    ranked_list = []
    
    for item in search_results['ranking']:
        uri = item['uri']
        
        if item['type'] == 'Segment':
            
            # Fragment is a video segment
            segment = search_results['segments'][uri]
            segment_uri = uri
            video_uri = segment['videoUri']
            video = search_results['videos'][video_uri]
            fragment = segment
            
        elif item['type'] == 'Video':
            
            # Fragment is a full video
            segment = None
            segment_uri = None
            video_uri = uri
            video = search_results['videos'][video_uri]
            fragment = video
            
        else:
            
            # Ignore results that are neither segments or videos
            warnings.warn('result type is {}'.format(item['type']))
            continue
        
        # Create asset
        asset = {}
        asset['uri'] = uri
        asset['videoUri'] = video_uri
        asset['segmentUri'] = segment_uri
        asset['type'] = item['type']
        asset['keyframe'] = fragment['keyframe']
        asset['startTime'] = fragment['startTimeMillis']
        asset['endTime'] = fragment['endTimeMillis']
        asset['segmentDuration'] = fragment['durationMillis']
        asset['speech'] = fragment['speech']
        asset['metadata'] = clean_metadata(video['metadata'])
        asset['videoDuration'] = video['durationMillis']
        asset['videoSources'] = video['sources']
        asset['videoKeyframe'] = video['keyframe']
        asset['visualTags'] = fragment.get('visualTags', [])
        
        # Create result
        result = {}
        result['rank'] = item['rank']
        result['score'] = item['score']
        
        # Attach evidence
        result['evidence'] = get_evidence_for_item(search_results, item)
        
        # Attach asset
        result['asset'] = asset
        
        ranked_list.append(result)
        
    return ranked_list
    
def segment_to_asset(uri, segment, video):
    """
    Construct asset data type from a segment and the corresponding video.
    """
    asset = {}
    asset['uri'] = uri
    asset['videoUri'] = segment['videoUri'] or video['uri']
    asset['segmentUri'] = segment['uri']
    asset['type'] = 'Segment'
    asset['keyframe'] = segment['keyframe']
    asset['startTime'] = segment['startTimeMillis']
    asset['endTime'] = segment['endTimeMillis']
    asset['segmentDuration'] = segment['durationMillis']
    asset['speech'] = segment['speech']
    asset['metadata'] = clean_metadata(video['metadata'])
    asset['videoDuration'] = video['durationMillis']
    asset['videoSources'] = video['sources']
    asset['videoKeyframe'] = video['keyframe']
    asset['visualTags'] = video.get('visualTags', [])
    return asset
    
def video_to_asset(uri, video):
    """
    Construct video data type a video.
    """
    asset = {}
    asset['uri'] = uri
    asset['videoUri'] = uri
    asset['segmentUri'] = None
    asset['type'] = 'Video'
    asset['keyframe'] = video['keyframe']
    asset['startTime'] = video['startTimeMillis']
    asset['endTime'] = video['endTimeMillis']
    asset['segmentDuration'] = video['durationMillis']
    asset['speech'] = video['speech']
    asset['metadata'] = clean_metadata(video['metadata'])
    asset['videoDuration'] = video['durationMillis']
    asset['videoSources'] = video['sources']
    asset['videoKeyframe'] = video['keyframe']
    asset['visualTags'] = video.get('visualTags', [])
    return asset
    
def postprocess_limas_results(results):
    postprocessors = [
        RegexPostprocessor(app.config['LIMAS_RESPONSE_POSTPROCESSING_RULES'])
    ]
    for postprocessor in postprocessors:
        postprocessor.process(results)
    return results
    
def fix_uri(uri):
    # Limas doesn't like '/' at the end of URIs
    if uri.endswith('/'):
        return uri[:-1]
    # Apache collapses double slashes into single ones
    if app.config['LIMAS_PREPEND_URI_SLASH'] and not uri.startswith('/'):
        uri = '/' + uri
    return uri
    
class LimasError(Exception):
    pass
    
def with_limas(func):
    """
    Decorator to inject limas JSON RPC service instance as first argument to 
    function, and pass results through a post-processing step.
    """
    @wraps(func)
    def wrapper(*args, **kwargs):
        service = jsonrpclib.Server(app.config['SERVICE_URL'])
        
        try:
            results = func(service, *args, **kwargs)
        
        except jsonrpclib.ProtocolError as e:
            error_msg = e.message
            
            # jsonrpclib sometimes passes a tuple to Protocol error :(
            if isinstance(error_msg, tuple) and len(error_msg) == 2:
                error_msg = '{} (code: {})'.format(error_msg[1], error_msg[0])
            error_msg = 'Protocol error ({}): {}'.format(func.__name__, error_msg)
            
            # Log error and re-raise
            log.error(error_msg)
            raise LimasError(error_msg, e)
        
        # Apply postprocessors
        return postprocess_limas_results(results)
    
    return wrapper
    
#
# Limas interface
#

@with_limas
def get_available_services(limas):
    return map(str, limas.getAvailableServices())

@with_limas
def simple_search(limas, text):
    query = create_magic_search_query_from_string(text)
    results = limas.search(query)
    return collect_results(results)

@with_limas
def advanced_search(limas, text, clauses):
    query = {'queryText': text, 'clauses': clauses}
    results = limas.searchWithParsedQuery(query)
    return collect_results(results)
    
@with_limas
def get_news_sources(limas):
    return limas.getNewsSources()

@with_limas
def get_news_items(limas, uri, count=5):
    from datetime import datetime
    uri = fix_uri(uri)
    # Transform timestamps to datetime objects
    items = limas.getNewsItems(uri, count)
    for item in items:
        item['timestamp'] = datetime.fromtimestamp(item['timestamp'])
    return items

@with_limas
def get_news_item(limas, uri):
    from datetime import datetime
    uri = fix_uri(uri)
    item = limas.getNewsItem(uri)
    item['timestamp'] = datetime.fromtimestamp(item['timestamp'])
    return item

@with_limas
def get_query_for_item(limas, uri):
    uri = fix_uri(uri)
    return limas.getQueryForItem(uri)
    
@with_limas
def search_news_source(limas, uri):
    uri = fix_uri(uri)
    query = limas.getQueryForSource(uri)
    results = limas.searchWithParsedQuery(query)
    return collect_results(results)

@with_limas
def search_news_item(limas, uri):
    uri = fix_uri(uri)
    query = limas.getQueryForItem(uri)
    results = limas.searchWithParsedQuery(query)
    return collect_results(results)
    
@with_limas
def get_interesting_items(limas, count=12):
    items = limas.getInterestingItems()
    results = collect_results(items)
    return results[:count]
    
@with_limas
def get_topic_types(limas):
    return map(str, limas.getTopicTypes())
    
@with_limas
def get_interesting_topics(limas, count=10, type=None):
    options = { 
        'temporalGroupingWindowMillis': 10*60*1000 # 10 mins
    }
    if type is None:
        topics = limas.getInterestingTopics(count, options)
    else:
        topics = limas.getInterestingTopics(count, type, options)
    examples = 'collectionExamples'
    for topic in topics:
        # Examples from the internet. Not relevant for now
        del topic['exampleImages']
        # Collect results from ranked list
        topic[examples] = collect_results(topic[examples])
    return topics
    
@with_limas
def get_home_topics(limas, count=10):
    topics = limas.getHomeTopics(count)
    examples = 'collectionExamples'
    for topic in topics:
        # Examples from the internet. Not relevant for now
        del topic['exampleImages']
        # Collect results from ranked list
        topic[examples] = collect_results(topic[examples])[:6]
    return topics
    
@with_limas
def get_face_tracks(limas, uri):
    uri = fix_uri(uri)
    return limas.getFaceTracks(uri)
    
@with_limas
def lookup_asset(limas, uri):
    uri = fix_uri(uri)
    options = dict(spokenWords=True, metadata=True, entityOccurrences=True)
    result = limas.lookup([uri], options)
    if len(result) == 0:
        return None
    result = result[0]
    if result is None:
        return None
    video_uri = result['videoUri']
    is_segment = video_uri != uri
    if is_segment:
        video = limas.lookup([video_uri], options)
        if len(video) == 0:
            return None
        else:
            return segment_to_asset(uri, result, video[0])
    return video_to_asset(uri, result)

@with_limas
def find_related_videos(limas, uri, **kwargs):
    uri = fix_uri(uri)
    options = dict(metadata=True, limit=10)
    options.update(kwargs)
    results = limas.findRelatedVideos(uri, options)
    return collect_results(results)

@with_limas
def find_related_segments(limas, uri, **kwargs):
    uri = fix_uri(uri)
    options = dict(metadata=True, limit=10)
    options.update(kwargs)
    results = limas.findRelatedSegments(uri, options)
    return collect_results(results)

@with_limas 
def get_keyframes(limas, uri):
    uri = fix_uri(uri)
    return limas.getKeyframes(uri)

@with_limas     
def get_transcript(limas, uri):
    uri = fix_uri(uri)
    return limas.getSpeechSegments(uri)
    
@with_limas
def get_last_update_time(limas):
    return limas.getLastChange() / 1000.0

@with_limas
def get_service_info(limas):
    return limas.getServiceInfo()

@with_limas
def get_version_info(limas):
    return limas.getVersionInfo()
    