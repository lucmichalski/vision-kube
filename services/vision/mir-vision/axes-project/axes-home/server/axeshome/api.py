#
# (c) Copyright 2015 Kevin McGuinness. All Rights Reserved. 
#
"""
API setup
"""

import os
import logging.config

from flask import Flask, Response
from flask.ext.restful import Api
from flask.ext.pymongo import PyMongo
from flask.ext.login import LoginManager

# Flask app
app = Flask(__name__)
app.config.from_object('axeshome.settings')

# Override settings with environment variable, if set
if 'AXESHOME_SETTINGS' in os.environ:
    app.config.from_envvar('AXESHOME_SETTINGS')
    
# Configure loggers
logging.config.dictConfig(app.config['LOGGING'])    

#  
# Add AXES URI route converter. This allows us to specify routes of the form
# /path/<axes:uri>. AXES URI's begin with axes:/. The converter strips off
# this part. The reason we use these is that web servers often collapse double
# slashes into single ones. Also, the flask path converter doesn't like things
# that begin with a leading forward slash, even if URL encoded. See:
# https://github.com/mitsuhiko/flask/issues/900
#
from axeshome.util import AxesURIConverter
app.url_map.converters['axes'] = AxesURIConverter

# Database
mongo = PyMongo(app)

# Login manager subsystem
login_manager = LoginManager(app)
import axeshome.user as user
login_manager.user_loader(user.find)
login_manager.token_loader(user.find_for_token)

# Api
api = Api(app)
import axeshome.resources as resources

# Available services
api.add_resource(resources.AvailableServices, '/available-services')

# Search
api.add_resource(resources.SimpleSearch, '/search')
api.add_resource(resources.ImageSearch, '/image-search')
api.add_resource(resources.AdvancedSearch, '/advanced-search')

# Assets
api.add_resource(resources.Asset, '/assets/<axes:uri>')
api.add_resource(resources.VideoStats, '/video-stats/<axes:uri>')
api.add_resource(resources.RelatedVideos, '/related-videos/<axes:uri>')
api.add_resource(resources.RelatedSegments, '/related-segments/<axes:uri>')
api.add_resource(resources.Keyframes, '/keyframes/<axes:uri>')
api.add_resource(resources.Transcript, '/transcript/<axes:uri>')
api.add_resource(resources.FaceTracks, '/face-tracks/<axes:uri>')

# News
api.add_resource(resources.NewsSources, '/news-sources')
api.add_resource(resources.NewsItems, '/news-items/<axes:uri>')
api.add_resource(resources.NewsItem, '/news-item/<axes:uri>')
api.add_resource(resources.NewsItemSearch, '/news-item-search/<axes:uri>')
api.add_resource(resources.NewsSourceSearch, '/news-source-search/<axes:uri>')

# Interesting
api.add_resource(resources.TopicTypes, '/topic-types')
api.add_resource(resources.InterestingItems, '/interesting-items')
api.add_resource(resources.InterestingTopics, '/interesting-topics')
api.add_resource(resources.HomeTopics, '/home-topics')

# Popular 
api.add_resource(resources.PopularQueries, '/popular-queries')
api.add_resource(resources.PopularVideos, '/popular-videos')

# Image store
api.add_resource(resources.ImageStore, '/image-store')

# User management
api.add_resource(resources.UserLogin, '/user/login')
api.add_resource(resources.UserProfile, '/user/profile')
api.add_resource(resources.UserLogout, '/user/logout')
api.add_resource(resources.UserRegistration, '/user/register')
api.add_resource(resources.UserHistory, '/user/history')
api.add_resource(resources.UserBookmarks, '/user/bookmarks')
api.add_resource(resources.UserBookmark, '/user/bookmarks/<axes:uri>')

# Admin 
api.add_resource(resources.ServiceInfo, '/service-info')
api.add_resource(resources.VersionInfo, '/version-info')
api.add_resource(resources.DatasetInfo, '/dataset-info')

# Override flask restful unauthorized handler so that the browser does 
# not pop up a basic auth dialog
def handle_unauth(response):
    from flask import current_app
    realm = current_app.config.get("HTTP_BASIC_AUTH_REALM", "flask-restful")
    challenge = u"{0} realm=\"{1}\"".format("Custom", realm)
    response.headers['WWW-Authenticate'] = challenge
    return response
api.unauthorized = handle_unauth
