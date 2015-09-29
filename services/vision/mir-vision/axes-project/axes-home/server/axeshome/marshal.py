#
# (c) Copyright 2015 Kevin McGuinness. All Rights Reserved. 
#
"""
Data marshaling definitions
"""
from flask.ext.restful import fields

def extend(orig, extension):
    extended = orig.copy()
    extended.update(extension)
    return extended
    
QueryLogEntry = {
    'text': fields.String(),
    'hits': fields.Integer(),
}

UserProfile = {
    'username': fields.String(),
    'email': fields.String(),
    'firstName': fields.String(attribute='first_name'),
    'lastName': fields.String(attribute='last_name')
}

LoginDetails = {
    'user': fields.Nested(UserProfile),
    'token': fields.String()
}

VideoSource = {
    'format' : fields.String(),
    'href' : fields.String(attribute='url'),
}

Keyframe = {
    'imageUrl': fields.String(),
    'thumbnailUrl': fields.String(),
}

Speech = {
    'speaker': fields.String(),
    'spokenWords': fields.String(),
}

Segment = {
    'uri': fields.String(),
    'startTime': fields.Integer(attribute='startTimeMillis'),
    'endTime': fields.Integer(attribute='endTimeMillis'),
    'duration': fields.Integer(attribute='durationMillis')
}

Metadata = {
    'title': fields.String(),
    'summary': fields.String(),
    'description': fields.String(),
    'genres': fields.List(fields.String),
    'keywords': fields.List(fields.String),
    'entities': fields.List(fields.String),
    'language': fields.String(),
    'license': fields.String(default='Copyrighted'),
    'publicationDate': fields.String(default='Unknown'),
    'contributors': fields.List(fields.String),
    'persons': fields.List(fields.String),
    'objects': fields.List(fields.String),
    'places': fields.List(fields.String),
}

Asset = {
    'uri': fields.String(),
    'videoUri': fields.String(),
    'segmentUri': fields.String(),
    'type': fields.String(),
    'metadata': fields.Nested(Metadata),
    'speech': fields.Nested(Speech),
    'startTime': fields.Integer(),
    'endTime': fields.Integer(),
    'segmentDuration': fields.Integer(),
    'videoDuration': fields.Integer(),
    'keyframe': fields.Nested(Keyframe),
    'videoKeyframe': fields.Nested(Keyframe),
    'videoSources': fields.List(fields.Nested(VideoSource)),
    'bookmarked': fields.Boolean(default=False),
    'visualTags': fields.List(fields.String, default=[]),
}

VideoStats = {
    'uri': fields.String(),
    'views': fields.Integer(),
    'likes': fields.Integer(),
    'bookmarks': fields.Integer(),
}

Evidence = {
    'queryString': fields.String(),
    'displayName': fields.String(),
    'examples': fields.List(fields.Nested(Keyframe)),
    'score': fields.Float()
}

SearchResult = {
    'rank': fields.Integer(),
    'score': fields.Float(),
    'evidence': fields.List(fields.Nested(Evidence)),
    'asset': fields.Nested(Asset),
}

Topic = {
    'name': fields.String(),
    'collectionExamples': fields.List(fields.Nested(SearchResult))
}

NewsSource = {
    'uri': fields.String(),
    'name': fields.String(),
    'description': fields.String(),
    'homeUrl': fields.String(attribute='homeURL'),
    'pictureUrl': fields.String(attribute='pictureURL'),
}

NewsItem = {
    'uri': fields.String(),
    'url': fields.String(),
    'title': fields.String(),
    'summary': fields.String(),
    'timestamp': fields.DateTime()
}

QueryClause = {
    'type': fields.String(),
    'text': fields.String(),
}

ParsedQuery = {
    'queryText': fields.String(),
    'clauses': fields.List(fields.Nested(QueryClause)),
}

Evidence = {
    'uri': fields.String(),
    'displayName': fields.String(),
    'queryString': fields.String(),
    'examples': fields.List(fields.String),
}

FacePosition = {
    'frame' : fields.Integer(),
    'time': fields.Integer(),
    'roi': fields.List(fields.Integer),
}

FaceTrack = {
    'uri': fields.String(),
    'startTime': fields.Integer(attribute='startTimeMillis'),
    'endTime': fields.Integer(attribute='endTimeMillis'),
    'keyframe' : fields.Nested(Keyframe),
    'keyframePos': fields.Integer(),
    'positions': fields.List(fields.Nested(FacePosition)),
}

SpeechSegment = extend(Segment, {
    'speech': fields.Nested(Speech),
})

KeyframeSegment = extend(Segment, {
    'keyframe': fields.Nested(Keyframe),
})

HistoryItem = {
    'username': fields.String(),
    'timestamp': fields.DateTime(),
    'asset': fields.Nested(Asset)
}

VersionInfo = {
    'majorVersion': fields.Integer(),
    'minorVersion': fields.Integer(),
    'revision': fields.Integer(),
}

ServiceInfo = {
    'name': fields.String(),
    'description': fields.String(),
    'status': fields.String(),
    'version': fields.Nested(VersionInfo),
}

DatasetInfo = {
    'id': fields.String(),
    'name': fields.String(),
    'description': fields.String(),
    'lengthHours': fields.Integer(),
    'videoCount': fields.Integer(),
    'shotCount': fields.Integer(),
    'keyframeCount': fields.Integer()
}

