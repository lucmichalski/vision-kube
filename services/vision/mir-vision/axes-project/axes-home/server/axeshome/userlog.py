#
# (c) Copyright 2015 Kevin McGuinness. All Rights Reserved. 
#
"""
Logging of user actions.
"""
import logging

from axeshome.api import mongo
import axeshome.user as user
from datetime import datetime

log = logging.getLogger('axeshome.userlog')

def get_current_username():
    if user.is_logged_in():
       return user.current_user.username
    return 'anonymous' 

def log_action(action, info=None): 
    user = get_current_username()
    log.info('[%s] <%s> info: %s', user, action, info)
    mongo.db.userlog.insert({
        'user': get_current_username(),
        'timestamp': datetime.now(),
        'action': action,
        'info': info 
    })
    