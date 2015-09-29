#
# (c) Copyright 2015 Kevin McGuinness. All Rights Reserved. 
#
"""
User and user registration management.
"""
import string
import random
import hashlib
import uuid

from axeshome.api import mongo
from flask.ext.login import UserMixin
from flask.ext.login import current_user
from flask.ext.restful import abort

class RegistrationError(Exception):
    """Raised when user registration fails"""
    pass
    
def slow_compare(val1, val2):
    """Compare strings in a way insensitive to timing attacks"""
    if len(val1) != len(val2):
        return False
    result = 0
    for x, y in zip(val1, val2):
        result |= ord(x) ^ ord(y)
    return result == 0

class PasswordHasher(object):
    """Object for hashing and verifying passwords"""
    algorithm = 'sha256'
    
    def salt(self, length=12):
        chars = string.ascii_lowercase + string.ascii_uppercase + string.digits
        return ''.join(random.choice(chars) for i in range(length))

    def encode(self, password, salt=None):
        if password is None:
            raise ValueError('password is None')
        if salt is None:
            salt = self.salt()
        elif '$' in salt:
            raise ValueError('$ in salt')
        hash = hashlib.sha256(salt + password).hexdigest()
        return '$'.join([self.algorithm, salt, hash])

    def verify(self, password, encoded):
        algorithm, salt, hash = encoded.split('$', 2)
        assert algorithm == self.algorithm
        encoded_pass = self.encode(password, salt)
        return slow_compare(encoded_pass, encoded)
        
def get_password_hasher():
    """Returns an instance of a password hasher"""
    return PasswordHasher()
        
class User(UserMixin):
    """User object"""
    
    def __init__(self, profile):
        self.profile = profile
        self.id = profile['username']
        self.username = profile['username']
        self.password_hash = profile['password']
        self.email = profile['email']
        self.first_name = profile['first_name']
        self.last_name = profile['last_name']
        self.active = profile['active']

    def is_active(self):
        return self.active
        
    def get_auth_token(self):
        return self.profile['token']

def password_is_insecure(password):
    """Checks if a password is secure"""
    # TODO: include any additional checks here
    return password is None or len(password) < 6
    
def find(username):
    """Returns the user object given the username, or None if not found"""
    mongo.db.users.ensure_index('username')
    profile = mongo.db.users.find_one({'username': username}) 
    if profile is not None:
        return User(profile)
    return None
    
def find_for_token(token):
    """Returns the user object given the token, or None if not found"""
    mongo.db.users.ensure_index('token')
    profile = mongo.db.users.find_one({'token': token}) 
    if profile is not None:
        return User(profile)
    return None
    
def exists(username):
    """Returns true if there is a user with the given name"""
    return find(username) is not None
    
def userlist():
    """Returns a list of all users"""
    return [User(p) for p in mongo.db.users.find()]

def register(username, password, email=None, first_name=None, last_name=None):
    """
       Register a new user with given name and password. Raises a 
       RegistrationError if the user exists or the password is insecure.
    """

    if exists(username):
        raise RegistrationError('user exists')
        
    if password_is_insecure(password):
        raise RegistrationError('insecure password')
    
    new_profile = {
        'username': username,
        'password': get_password_hasher().encode(password),
        'email': email,
        'first_name': first_name,
        'last_name': last_name,
        'active': True,
        'token': str(uuid.uuid4())
    }
    
    mongo.db.users.insert(new_profile)
    return User(new_profile)

def authenticate(username, password):
    """
       Authenticate the given username, password pair. Returns the user if
       authentication succeeds. Otherwise returns None.   
    """
    user = find(username)
    if user is None:
        return None
    hasher = get_password_hasher()
    encoded_password = user.password_hash
    if hasher.verify(password, encoded_password):
        return user
    return None

def login(username, password, remember=True):
    """
       Authenticate and log the user in. Returns the user on success and 
       None on failure.
    """
    from flask.ext.login import login_user
    user = authenticate(username, password)
    if user is not None:
        login_user(user, remember=remember)
    return user

def logout():
    """Log out the current user"""
    from flask.ext.login import logout_user
    logout_user()
    
def is_logged_in():
    return current_user and current_user.is_authenticated()

def add_to_history(asset):
    """Add an asset to user history"""
    from datetime import datetime
    if is_logged_in():
        
        # Remove old history item, if exists
        mongo.db.history.ensure_index('username')
        mongo.db.history.ensure_index('asset.uri')
        mongo.db.history.remove({
            'username': current_user.username,
            'asset.uri': asset['uri']
        })
        
        # Insert new history item
        mongo.db.history.insert({
            'username': current_user.username,
            'timestamp': datetime.now(),
            'asset': asset
        })
        
        return True
    return False

def get_history(limit=100):
    """Get user history of assets visited by user"""
    if is_logged_in():
        mongo.db.history.ensure_index('username')
        history = mongo.db.history.find(
            {'username': current_user.username},
            sort=[('timestamp', -1)], limit=limit)
        return list(history)
    return None
    
def add_bookmark(asset):
    """Add a bookmark to an asset for the currently logged in user"""
    from datetime import datetime
    if is_logged_in():
        
        # Remove old bookmark, if exists
        mongo.db.bookmarks.ensure_index('username')
        mongo.db.bookmarks.ensure_index('asset.uri')
        mongo.db.bookmarks.remove({
            'username': current_user.username,
            'asset.uri': asset['uri']
        })
        
        # Insert new bookmark
        mongo.db.bookmarks.insert({
            'username': current_user.username,
            'timestamp': datetime.now(),
            'asset': asset
        })
        
        return True
    return False
    
def remove_bookmark(uri):
    """Remove a bookmark to an asset for the currently logged in user"""
    if is_logged_in():
        mongo.db.bookmarks.ensure_index('username')
        mongo.db.bookmarks.ensure_index('asset.uri')
        mongo.db.bookmarks.remove({
            'username': current_user.username,
            'asset.uri': uri
        })
        return True
    return False
    
def has_bookmark(uri):
    """
       Returns true if the asset with given URI has been bookmarked by the
       currently logged in user. Returns false if there is no currently logged
       in user.
    """
    if is_logged_in():
        mongo.db.bookmarks.ensure_index('username')
        mongo.db.bookmarks.ensure_index('asset.uri')
        return mongo.db.bookmarks.find_one({
            'username': current_user.username,
            'asset.uri': uri
        }) is not None
    return False

def get_bookmarks(limit=0):
    """Get a list of the current users bookmarks"""
    if is_logged_in():
        mongo.db.bookmarks.ensure_index('username')
        bookmarks = mongo.db.bookmarks.find(
            {'username': current_user.username},
            sort=[('timestamp', -1)], limit=limit)
        return list(bookmarks)
    return None
    
        
