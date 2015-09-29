'use strict';
// (c) Copyright 2015 Kevin McGuinness. All Rights Reserved. 

var services = angular.module('axeshome.services', [
  'ngResource', 'axeshome.settings']);
  
/*
 * Service that takes care of storing login tokens and user details locally.
 */
services.factory('loginService', function($window) {
  var service = {
    user: null
  };

  service.login = function(loginDetails) {
    if ('token' in $window.localStorage) {
      delete $window.localStorage.token;
    }
  
    if ('token' in loginDetails) {
      $window.localStorage.token = loginDetails.token;
    }
  
    service.user = loginDetails.user;
  };

  service.logout = function() {
    if ('token' in $window.localStorage) {
      delete $window.localStorage.token;
    }
    service.user = null;
  };

  service.hasToken = function() {
    return 'token' in $window.localStorage;
  };

  service.getToken = function() {
    if ('token' in $window.localStorage) {
      return $window.localStorage.token;
    }
    return null;
  };

  return service;
});

/**
 * Service to keep track of pending requests so that they can be aborted
 * when the page changes.
 */
services.service('pendingRequests', function() {
  var pending = [];
  
  this.add = function(canceller) {
    pending.push(canceller);
  };
  
  this.remove = function(canceller) {
    var index = pending.indexOf(canceller);
    if (index >= 0) {
      pending.splice(index, 1);
      return true;
    }
    return false;
  };
  
  this.abortAll = function() {
    for (var i = 0; i < pending.length; i++) {
      var canceller = pending[i];
      canceller.resolve();
    }
    pending.length = 0;
  };
  
  this.length = function() {
    return pending.length;
  };
});

/*
 * HTTP interceptor for the app. Adds and removes pending requests from
 * the active list to allow ajax aborts on page changes.
 */
services.service('ajaxAbortInterceptor', function($q, pendingRequests) {
  
  this.request = function(config) {
    var canceller = $q.defer();
    canceller.url = config.url;
    config.timeout = canceller.promise;
    config.canceller = canceller;
    pendingRequests.add(canceller);
    return config || $q.when(config);
  };
  
  this.response = function(response) {
    pendingRequests.remove(response.config.canceller);
    return response || $q.when(response);
  };
  
  this.responseError = function(rejection) {
    pendingRequests.remove(rejection.config.canceller);
    return $q.reject(rejection);
  };
});
  
/*
 * Wrapper around $resource that adds API location and suffix specifics.
 */
services.factory('apiResource', function($resource, $q,
  apiConfig, pendingRequests
) {  
  return function(url, paramDefaults, actions) {
    var fullUrl = apiConfig.prefix + url + apiConfig.suffix;
    return $resource(fullUrl, paramDefaults, actions);
  };
});

/*
 * Some misc utilities
 */
services.factory('utilities', function($sce) {
  var utilities = {};
  
  // Map video sources to trusted resources
  utilities.markSourcesAsTrusted = function(sources) {
    
    var trustedSources = [];
    angular.forEach(sources, function(source) {
      trustedSources.push({
        format: source.format,
        href: $sce.trustAsResourceUrl(source.href)
      });
    });
    
    return trustedSources;
  };
  
  // Sadly, order matters in some browsers; this function sorts videos
  // so that they work on targetted platforms
  utilities.sortVideoSources = function(sources) {
    
    var sourceFormatPrefixes = [
      'video/webm',
      'video/mp4',
      'video/mpeg',
      'video/avi'
    ];
    
    var getFormatPrefix = function(source) {
      var format = source.format;
      var i = format.indexOf(';');
      return (i > 0) ? format.substr(0, i) : format;
    };
    
    var getOrder = function(source) {
      var prefix = getFormatPrefix(source);
      var order = sourceFormatPrefixes.indexOf(prefix);
      return (order >= 0) ? order : 1000;
    };      

    var sources = sources.slice();
    sources.sort(function(s1, s2) {
      return getOrder(s1) - getOrder(s2);
    });
    
    return sources;
  };
  
  // Count keys in an associative array
  utilities.countKeys = function(object) {
    var count = 0;
    angular.forEach(object, function() {
      count++;
    });
    return count;
  };
  
  // Count unique videos in a result list
  utilities.countUniqueVideos = function(results) {
    var videoUris = {};
    angular.forEach(results, function(result) {
      videoUris[result.asset.videoUri] = true;
    });
    return utilities.countKeys(videoUris);
  };
  
  // Group unique videos
  utilities.getUniqueVideos = function(results) {
    var uniqueVideos = [];
    
    var videoUris = {};
    angular.forEach(results, function(result) {
      var videoUri = result.asset.videoUri;
      if (!(videoUri in videoUris)) {
        uniqueVideos.push(result);
        videoUris[videoUri] = true;
      }
    });
    
    return uniqueVideos;
  };
  
  // Result explanation icons
  var evidenceIconMapping = {
    '#meta': 'icon-write',
    '#speech': 'icon-bubbles',
    '#face-t': 'icon-user',
    '#category-t': 'icon-image',
    '#instance-t': 'icon-image',
    '#entity': 'icon-image'
  };
  
  // Map evidence to result explanation icon class
  utilities.getIconForEvidence = function(evidence) {
    for (var key in evidenceIconMapping) {
      if (evidence.queryString.indexOf(key) >= 0) {
        return evidenceIconMapping[key];
      }
    }
    return null;
  };
  
  // Create a result explanation for a given result
  utilities.createResultExplanation = function(result) {
    var explanations = [];
    angular.forEach(result.evidence, function(evidence) {
      if (evidence.score > 0) {
        var icon = utilities.getIconForEvidence(evidence);
        if (icon) {
          explanations.push({ 
            iconClass: icon, 
            description: evidence.displayName, 
            score: evidence.score
          });
        }
      }
    });
    return explanations;
  };
  
  // Attach explanations to all results
  utilities.attachResultExplanations = function(results) {
    angular.forEach(results, function(result) {
      result.explanations = utilities.createResultExplanation(result);
    });
  };
  
  return utilities;
});

/*
 * Service for keeping track of visible faces given a list of face tracks.
 */
services.factory('faceTracker', function() {
  
  var findFaceTracksVisibleAtTime = function(faceTracks, time) {
    var visibleTracks = [];
  
    if (!faceTracks) {
      return visibleTracks;
    }
  
    // TODO: binary search may be more efficient here
  
    var timeMillis = time * 1000;
    for (var i = 0; i < faceTracks.length; i++) {
      var track = faceTracks[i];
      if (track.startTimeMillis <= timeMillis && 
          track.endTimeMillis >= timeMillis) {
          
        visibleTracks.push(track);
      
      } else if (track.startTimeMillis > timeMillis + 10000) {
      
        // stop if we are 10 sec past the last start time
        break;
      }
    }
  
    return visibleTracks;
  };
  
  var interpolateRect = function(before, after, timeMillis) {
    if (before.time == timeMillis) {
      return before;
    } 
  
    if (after.time == timeMillis) {
      return after;
    }
  
    if (after.time == before.time) {
      return before;
    }
  
    // linear interpolation
    var p = (timeMillis - before.time) / (after.time - before.time + 0.0);
    var roi = [0,0,0,0];
    for (var i = 0; i < 4; i++) {
      roi[i] = before.roi[i] * (1.0 - p) + after.roi[i] * p;
    }
  
    return roi;
  };
  
  var findFacePosition = function(track, time) {
    var timeMillis = time * 1000;
  
    // Find before and after face track box
    var before = track.positions[0];
    var after = track.positions[0];
  
    for (var i = 1; i < track.positions.length; i++) {
      var position = track.positions[i];
      if (position.time >= timeMillis) {
        after = position;
        break;
      }
      before = position;
    }
  
    return interpolateRect(before, after, timeMillis);
  };
  
  // Constructor
  var create = function(faceTracks) {
    
    var tracker = {
      faceTracks: faceTracks,
      currentTime: 0,
      visibleFaces: []
    };
  
    tracker.updateCurrentTime = function(currentTime) {
      tracker.currentTime = currentTime;
    
      var visibleTracks = findFaceTracksVisibleAtTime(
        tracker.faceTracks, currentTime);
    
      // Avoid modifying faces unless necessary to prevent events
      if (tracker.visibleFaces.length > 0) {
        tracker.visibleFaces = [];
      }
    
      // Push new face tracks
      for (var i  = 0; i < visibleTracks.length; i++) {
        var track = visibleTracks[i];
        var roi = findFacePosition(track, currentTime);
        tracker.visibleFaces.push({
          id: track.uri,
          keyframe: track.keyframe,
          position: {
            x: roi[0], 
            y: roi[1], 
            w: roi[2],
            h: roi[3]
          }
        });
      }
    
      return tracker.visibleFaces;
    };
  
    return tracker;
  };
  
  return {create: create};
});

services.factory('search', function(apiResource) {
  return apiResource('search');
});

services.factory('imageSearch', function(apiResource) {
  return apiResource('image-search');
});

services.factory('advancedSearch', function(apiResource) {
  return apiResource('advanced-search');
});

services.factory('assets', function(apiResource) {
  return apiResource('assets/axes::id', {}, {
    get: {
      method: 'GET', 
      params: {id: '@id'}
    }
  });
});

services.factory('videoStats', function(apiResource) {
  return apiResource('video-stats/axes::id', {}, {
    get: {
      method: 'GET', 
      params: {id: '@id'}
    },
    
    update: {
      method: 'PUT',
      params: {id: '@id'},
      isArray: false
    }
  });
});

services.factory('newsSources', function(apiResource) {
  return apiResource('news-sources');
});

services.factory('newsItems', function(apiResource) {
  return apiResource('news-items/axes::id', {}, {
    get: {
      method: 'GET', 
      params: {id: '@id'},
      isArray: true
    }
  });
});

services.factory('newsItem', function(apiResource) {
  return apiResource('news-item/axes::id', {}, {
    get: {
      method: 'GET', 
      params: {id: '@id'}
    }
  });
});

services.factory('newsItemSearch', function(apiResource) {
  return apiResource('news-item-search/axes::id', {}, {
    get: {
      method: 'GET', 
      params: {id: '@id'},
      isArray: true
    }
  });
});

services.factory('newsSourceSearch', function(apiResource) {
  return apiResource('news-source-search/axes::id', {}, {
    get: {
      method: 'GET', 
      params: {id: '@id'},
      isArray: true
    }
  });
});

services.factory('interestingItems', function(apiResource) {
  return apiResource('interesting-items');
});

services.factory('topicTypes', function(apiResource) {
  return apiResource('topic-types');
});

services.factory('interestingTopics', function(apiResource) {
  return apiResource('interesting-topics');
});

services.factory('homeTopics', function(apiResource) {
  return apiResource('home-topics');
});

services.factory('relatedVideos', function(apiResource) {
  return apiResource('related-videos/axes::id', {}, {
    get: {
      method: 'GET', 
      params: {id: '@id'},
      isArray: true
    }
  });
});

services.factory('relatedSegments', function(apiResource) {
  return apiResource('related-segments/axes::id', {}, {
    get: {
      method: 'GET', 
      params: {id: '@id'},
      isArray: true
    }
  });
});

services.factory('keyframes', function(apiResource) {
  return apiResource('keyframes/axes::id', {}, {
    get: {
      method: 'GET', 
      params: {id: '@id'},
      isArray: true,
      cache: true
    }
  });
});

services.factory('transcript', function(apiResource) {
  return apiResource('transcript/axes::id', {}, {
    get: {
      method: 'GET', 
      params: {id: '@id'},
      isArray: true,
      cache: true
    }
  });
});

services.factory('facetracks', function(apiResource) {
  return apiResource('face-tracks/axes::id', {}, {
    get: {
      method: 'GET', 
      params: {id: '@id'},
      isArray: true,
      cache: true
    }
  });
});

services.factory('popularVideos', function(apiResource) {
  return apiResource('popular-videos');
});

services.factory('imageStore', function(apiResource) {
  return apiResource('image-store', {}, {
    save: {
      method: 'POST'
    }
  });
});

services.factory('user', function(apiResource) {
  return apiResource('user/:controller', {}, {
    getProfile: {
      method: 'GET', 
      params: {controller: 'profile'}
    },
    
    login: {
      method: 'POST',
      params: {controller: 'login'}
    },
    
    logout: {
      method: 'POST',
      params: {controller: 'logout'}
    },
    
    register: {
      method: 'POST',
      params: {controller: 'register'}
    },
    
    history: {
      method: 'GET',
      params: {controller: 'history'},
      isArray: true
    },
    
    bookmarks: {
      method: 'GET',
      params: {controller: 'bookmarks'},
      isArray: true
    }
  });
});

services.factory('bookmarks', function(apiResource) {
  return apiResource('user/bookmarks/axes::id', {}, {
    addBookmark: {
      method: 'PUT',
      params: {id: '@id'}
    },
    
    hasBookmark: {
      method: 'GET',
      params: {id: '@id'}
    },
    
    removeBookmark: {
      method: 'DELETE',
      params: {id: '@id'}
    },
    
    listBookmarks: {
      method: 'GET',
      params: {id: ''},
      isArray: true
    },
  });
});

services.factory('availableServices', function(apiResource) {
  return apiResource('available-services');
});

services.factory('serviceInfo', function(apiResource) {
  return apiResource('service-info');
});

services.factory('versionInfo', function(apiResource) {
  return apiResource('version-info');
});

services.factory('datasetInfo', function(apiResource) {
  return apiResource('dataset-info');
});