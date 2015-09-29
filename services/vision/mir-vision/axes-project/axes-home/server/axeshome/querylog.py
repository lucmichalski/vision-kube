#
# (c) Copyright 2015 Kevin McGuinness. All Rights Reserved. 
#
"""
Simple database query logging functions
"""
from axeshome.api import mongo

def normalize_query(text):
    text = text.strip().lower()
    return text
    
def find(query_text):
    query_text = normalize_query(query_text)
    mongo.db.queries.ensure_index('text')
    return mongo.db.queries.find_one({'text': query_text})
    
def insert(query_text):
    query_text = normalize_query(query_text)
    mongo.db.queries.ensure_index('text')
    mongo.db.queries.update({'text': query_text}, {'$inc': {'hits': 1}}, True)
    
def find_popular(n=100):
    return list(mongo.db.queries.find(sort=[('hits', -1)], limit=n))
    


        
    