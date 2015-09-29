#
# (c) Copyright 2015 Kevin McGuinness. All Rights Reserved. 
#
"""
Flask-Restful resources
"""
from flask.ext.restful import reqparse, abort, Resource
from flask.ext.restful import fields, marshal, marshal_with
from bson.objectid import ObjectId
from flask import request
from flask.ext.login import login_required

import axeshome.marshal as objects
import axeshome.backend as backend
import axeshome.social as social
import axeshome.querylog as querylog
import axeshome.user as user
import axeshome.storage as storage
import axeshome.userlog as userlog

from axeshome.util import find_or_404, clause_type
from axeshome.util import get_image_data_and_extension_from_data_url

class AvailableServices(Resource):
    def get(self):
        return [{'value': x} for x in backend.get_available_services()]

class SimpleSearch(Resource):
    parser = reqparse.RequestParser()
    parser.add_argument('q', type=str, required=True)
    
    @marshal_with(objects.SearchResult)
    def get(self):
        args = self.parser.parse_args()
        querylog.insert(args.q)
        userlog.log_action('simple-search', args.q)
        return backend.simple_search(args.q)
        
class ImageSearch(Resource):
    parser = reqparse.RequestParser()
    parser.add_argument('q', type=str, required=True)
    
    @marshal_with(objects.SearchResult)
    def get(self):
        args = self.parser.parse_args()
        userlog.log_action('image-search', args.q)
        clauses = [{ 'type': '#instance-i', 'text': args.q }]
        return backend.advanced_search('', clauses)

class AdvancedSearch(Resource):
    parser = reqparse.RequestParser()
    parser.add_argument('text', type=str, required=True, dest='text')
    parser.add_argument('clauses', type=clause_type, action='append', 
        dest='clauses')
    
    @marshal_with(objects.SearchResult)
    def get(self):
        args = self.parser.parse_args()
        userlog.log_action('advanced-search', args)
        return backend.advanced_search(args.text, args.clauses)

class Asset(Resource):
    @marshal_with(objects.Asset)
    def get(self, uri):
        userlog.log_action('fetch-asset', uri)
        asset = backend.lookup_asset(uri)
        if asset is None:
            abort(404, message='No such asset')
        else:
            video_uri = asset['videoUri']
            social.increment_stats(video_uri, 'views')
            user.add_to_history(asset)
            asset['bookmarked'] = user.has_bookmark(video_uri)
        return asset
        
class VideoStats(Resource):
    parser = reqparse.RequestParser()
    parser.add_argument('increment', type=str, required=True, dest='increment')
    
    @marshal_with(objects.VideoStats)
    def get(self, uri):
        return social.get_video_stats(uri)
    
    @marshal_with(objects.VideoStats)
    def put(self, uri):
        args = self.parser.parse_args()
        return social.increment_stats(uri, args.increment)

class NewsSources(Resource):    
    @marshal_with(objects.NewsSource)
    def get(self):
        userlog.log_action('fetch-news-sources')
        return backend.get_news_sources()

class NewsItems(Resource):
    parser = reqparse.RequestParser()
    parser.add_argument('limit', type=int, dest='limit', default=5)
    
    @marshal_with(objects.NewsItem)
    def get(self, uri):
        args = self.parser.parse_args()
        return backend.get_news_items(uri, args.limit)

class NewsItem(Resource):
    @marshal_with(objects.NewsItem)
    def get(self, uri):
        return backend.get_news_item(uri)

class NewsItemSearch(Resource):
    @marshal_with(objects.SearchResult)
    def get(self, uri):
        userlog.log_action('news-item-search', uri)
        return backend.search_news_item(uri)

class NewsSourceSearch(Resource):
    @marshal_with(objects.SearchResult)
    def get(self, uri):
        userlog.log_action('news-source-search', uri)
        return backend.search_news_source(uri)

class InterestingItems(Resource):
    @marshal_with(objects.SearchResult)
    def get(self):
        userlog.log_action('fetch-interesting-items')
        return backend.get_interesting_items()

class TopicTypes(Resource):
    def get(self):
        return [{'value': x} for x in backend.get_topic_types()]

class InterestingTopics(Resource):
    parser = reqparse.RequestParser()
    parser.add_argument('limit', type=int, dest='limit', default=10)
    parser.add_argument('type', type=str, dest='type', default=None)
    
    @marshal_with(objects.Topic)
    def get(self):
        args = self.parser.parse_args()
        userlog.log_action('fetch-interesting-topics', (args.limit, args.type))
        return backend.get_interesting_topics(args.limit, args.type)

class HomeTopics(Resource):
    parser = reqparse.RequestParser()
    parser.add_argument('limit', type=int, dest='limit', default=10)
    
    @marshal_with(objects.Topic)
    def get(self):
        args = self.parser.parse_args()
        userlog.log_action('fetch-home-topics', args.limit)
        return backend.get_home_topics(args.limit)

class FaceTracks(Resource):
    @marshal_with(objects.FaceTrack)
    def get(self, uri):
        return backend.get_face_tracks(uri)
        
class RelatedVideos(Resource):
    @marshal_with(objects.SearchResult)
    def get(self, uri):
        return backend.find_related_videos(uri)
        
class RelatedSegments(Resource):
    @marshal_with(objects.SearchResult)
    def get(self, uri):
        return backend.find_related_segments(uri) 
        
class Keyframes(Resource):
    @marshal_with(objects.KeyframeSegment)
    def get(self, uri):
        return backend.get_keyframes(uri) 

class Transcript(Resource):
    @marshal_with(objects.SpeechSegment)
    def get(self, uri):
        return backend.get_transcript(uri)
        
class PopularQueries(Resource):
    @marshal_with(objects.QueryLogEntry)
    def get(self):
        userlog.log_action('find-popular-queries')
        return querylog.find_popular()
        
class PopularVideos(Resource):
    @marshal_with(objects.Asset)
    def get(self):
        userlog.log_action('find-popular-videos')
        items = social.find_popular_videos(20)
        results = []
        for item in items:
            asset = backend.lookup_asset(item['uri'])
            results.append(asset)
        return results
        
class ImageStore(Resource):
    def post(self):
        data_url = request.json['dataUrl']
        data, extension = get_image_data_and_extension_from_data_url(data_url)
        media_storage = storage.MediaStorage()
        url, full_path = media_storage.save(data, extension=extension)
        return dict(url=url)
        
class UserLogin(Resource):
    parser = reqparse.RequestParser()
    parser.add_argument('username', type=str)
    parser.add_argument('password', type=str)
    parser.add_argument('remember', type=bool, default=True)
    
    @marshal_with(objects.LoginDetails)
    def post(self):
        args = self.parser.parse_args()
        usr = user.login(args.username, args.password, args.remember)
        if usr is not None:
            userlog.log_action('user-login', args.username)
            return dict(user=usr.profile, token=usr.get_auth_token())
        abort(403, message='Invalid username or password')
        
class UserProfile(Resource):
    method_decorators = [login_required] 
    
    @marshal_with(objects.LoginDetails)
    def get(self):
        if user.is_logged_in():
            return dict(user=user.current_user.profile, 
                token=user.current_user.get_auth_token())
        return None

class UserHistory(Resource):
    method_decorators = [login_required] 
    
    @marshal_with(objects.HistoryItem)
    def get(self):
        userlog.log_action('fetch-user-history')
        return user.get_history()
        
class UserBookmarks(Resource):
    method_decorators = [login_required] 
    
    @marshal_with(objects.HistoryItem)
    def get(self):
        userlog.log_action('fetch-user-bookmarks')
        return user.get_bookmarks()
    
    def post(self):
        asset = request.json
        userlog.log_action('save-bookmark', asset['videoUri'])
        user.add_bookmark(asset)
        social.increment_stats(asset['videoUri'], 'bookmarks')

class UserBookmark(Resource):
    method_decorators = [login_required] 
    
    def get(self, uri):
        return {
            'uri': uri,
            'bookmarked': user.has_bookmark(uri)
        }
    
    def put(self, uri):
        userlog.log_action('save-bookmark', uri)
        asset = backend.lookup_asset(uri)
        user.add_bookmark(asset)
        social.increment_stats(uri, 'bookmarks')
        
    def delete(self, uri):
        userlog.log_action('delete-bookmark', uri)
        user.remove_bookmark(uri)
        social.decrement_stats(uri, 'bookmarks')

class UserRegistration(Resource):
    parser = reqparse.RequestParser()
    parser.add_argument('username', type=str)
    parser.add_argument('password', type=str)
    parser.add_argument('email', type=str, required=True)
    parser.add_argument('firstName', type=str, dest='first_name')
    parser.add_argument('lastName', type=str, dest='last_name')
    
    @marshal_with(objects.LoginDetails)
    def post(self):
        args = self.parser.parse_args()
        try:
            usr = user.register(args.username, args.password, args.email, 
                args.first_name, args.last_name)
        except user.RegistrationError as e:
            abort(400, message='Registration failed: {}'.format(e.message))
        return dict(user=usr.profile, token=usr.get_auth_token())

class UserLogout(Resource):
    def post(self):
        userlog.log_action('user-logout')
        user.logout()

class ServiceInfo(Resource):
    @marshal_with(objects.ServiceInfo)
    def get(self):
        return backend.get_service_info()
        
class VersionInfo(Resource):
    @marshal_with(objects.VersionInfo)
    def get(self):
        return backend.get_version_info()

class DatasetInfo(Resource):
    @marshal_with(objects.DatasetInfo)
    def get(self):
        from flask import current_app as app
        return app.config['DATASET_INFO']
    