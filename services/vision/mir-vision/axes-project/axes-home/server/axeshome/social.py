#
# (c) Copyright 2015 Kevin McGuinness. All Rights Reserved. 
#
"""
Social features
"""
import axeshome.backend as backend
from axeshome.api import mongo

def get_video_stats(uri):
    uri = backend.fix_uri(uri)
    mongo.db.videostats.ensure_index('uri')
    stats = mongo.db.videostats.find_one({'uri': uri})
    if stats is None:
        stats = {'uri': uri, 'views': 0, 'bookmarks': 0, 'likes': 0}
        ident = mongo.db.videostats.insert(stats)
        stats = mongo.db.videostats.find_one({'_id': ident})
    return stats
    
def increment_stats(uri, field):
    stats = get_video_stats(uri)
    stats[field] += 1
    mongo.db.videostats.save(stats)
    return stats

def decrement_stats(uri, field):
    stats = get_video_stats(uri)
    stats[field] -= 1
    mongo.db.videostats.save(stats)
    return stats

def find_popular_videos(n=100):
    return mongo.db.videostats.find(
        sort=[('likes', -1), ('views', -1)], limit=n)