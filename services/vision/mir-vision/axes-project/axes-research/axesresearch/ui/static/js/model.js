//
// knockout.js data model
//
// Author: Kevin McGuinness <kevin.mcguinness@dcu.ie>
//
var model = (function() {
  var module = {};
  
  // Collections
  
  module.Collection = function() {
    var self = this;
    self.name = ko.observable('My Collection');
    self.videos = ko.observableArray([]);
  };
  
  // Preferences
  
  module.Preferences = function() {
    var self = this;
    self.user = ko.observable("");
    self.showFeedbackButtons = ko.observable(true);
    self.defaultResultView = ko.observable('Detailed');
    self.defaultResultType = ko.observable('Segment');
    self.resultsPerPage = ko.observable(50);
  };
  
  // Queries
  
  module.QueryType = (function() {
    var self = {};
    
    self.image = settings.imageSearchTypes;
    self.text = settings.textSearchTypes;
    self.visual = settings.visualSearchTypes;
    self.magic = ['magic'];
    self.textual = self.text.concat(self.visual);
    
    var descriptions = {
      'instance-i': 'Non faces',
      'face-i': 'Faces',
      'meta': 'Metadata',
      'speech': 'Spoken Words',
      'meta speech': 'Both',
      'category-t': 'Broad terms',
      'instance-t': 'Places or logos',
      'face-t': 'Faces',
      'magic': 'Automatic search'
    };
    
    var tooltips = {
      'instance-i': 'Similarity search for everything but faces',
      'face-i': 'Similarity search for human faces',
      'meta': 'Search in textual metadata',
      'speech': 'Search in dialogue',
      'meta speech': 'Search both in textual metadata and dialog',
      'category-t': 'Search visual categories like people, car, building, house',
      'instance-t': 'Places (e.g. Brandenburger Tor, Parliament) or logos (everything flat and 2D, bank note, painting)',
      'face-t': 'Search faces and facial attributes (e.g. name of a person, moustache, glasses)',
      'magic': 'Allow the system to automatically choose search type'
    };
    
    self.getDescription = function(type) {
      return descriptions[type];
    };
    
    self.getTooltip = function(type) {
      return tooltips[type];
    };
    
    self.hasInstanceSearch = function() {
      return self.image.indexOf('instance-i') >= 0;
    };
    
    self.hasSimilaritySearch = function() {
      return self.image.length > 0;
    };
    
    return self;
  })();

  module.Image = function(imageUrl, thumbnailUrl) {
    var self = this;
    self.imageUrl = ko.observable(imageUrl || '');
    self.thumbnailUrl = ko.observable(thumbnailUrl || '');
    self.selection = ko.observable(null);
  };

  module.Query = function(text) {
    var self = this;
    self.type = ko.observable('simple');
    self.textQueryType = ko.observable(module.QueryType.text[2]);
    self.imageQueryType = ko.observable(module.QueryType.image[0]);
    self.text = ko.observable(text || '');
    self.queryImages = ko.observableArray([]);
  };
  
  // Advanced query  
  module.AdvancedQueryTextClauseTypes = [];
  module.AdvancedQueryImageClauseTypes = [];
  
  var addAdvancedTextClause = function(type, name, text) {
    if (module.QueryType[type].indexOf(name) >= 0) {
      module.AdvancedQueryTextClauseTypes.push({
        displayName:text, type:name, accepts: 'text'
      });
    }
  };
  
  var addAdvancedImageClause = function(name, text) {
    if (module.QueryType.image.indexOf(name) >= 0) {
      module.AdvancedQueryImageClauseTypes.push({
        displayName:text, type:name, accepts: 'images'
      });
    }
  };
  
  addAdvancedTextClause('text', 'meta', 
    'Text search (metadata)');
  addAdvancedTextClause('text', 'speech', 
    'Text search (spoken words)');
  addAdvancedTextClause('text', 'meta speech', 
    'Text search (metadata and spoken words)');
  addAdvancedTextClause('visual', 'category-t', 
    'Visual search (categories/broad terms)');
  addAdvancedTextClause('visual', 'instance-t', 
    'Visual search (places or logos)');
  addAdvancedTextClause('visual', 'face-t', 
    'Visual search (faces)');  
  addAdvancedImageClause('instance-i', 
    'Similarity search (non-faces)');
  addAdvancedImageClause('face-i', 
    'Similarity search (faces)');
  addAdvancedImageClause('category-i', 
    'Similarity search (categories/broad terms)');
  
  module.AdvancedQueryClauseTypes = (function() {
    var types = module.AdvancedQueryTextClauseTypes.concat(
      module.AdvancedQueryImageClauseTypes);
    var mapping = {};
    for (var i = 0; i < types.length; i++) {
      mapping[types[i].type] = types[i];
    }
    return mapping;
  })();
  
  module.AdvancedQueryClause = function(type, text) {
    var self = this;
    self.type = ko.observable(type || 'meta');
    self.text = ko.observable(text || '');
    
    self.displayName = ko.computed(function() {
      return module.AdvancedQueryClauseTypes[self.type()].displayName;
    }, self);
    
    self.accepts = ko.computed(function() {
      return module.AdvancedQueryClauseTypes[self.type()].accepts;
    }, self);
  };
  
  module.AdvancedQuery = function(clauses) {
    var self = this;
    self.type = ko.observable('advanced');
    
    if (clauses) {
      clauses = ko.utils.arrayMap(clauses, function(item) {
        return new module.AdvancedQueryClause(item.type, item.text);
      });
      
      self.clauses = ko.observableArray(clauses);
      
    } else if (module.QueryType.text.indexOf('meta speech') >= 0) {
      self.clauses = ko.observableArray([
        new module.AdvancedQueryClause('meta speech')]);
    } else {
      self.clauses = ko.observableArray([
        new module.AdvancedQueryClause('meta')]);
    }
    
    self.text = ko.computed(function() {
      var clauses = self.clauses();
      var parts = [];
      for (var i = 0; i < clauses.length; i++) {
        var clause = clauses[i];
        if (clause.accepts() == 'text' && clause.text()) {
          parts.push(clause.text());
        }
      }
      return parts.join(' ');
    }, self);
    
    self.queryImages = ko.computed(function() {
      var clauses = self.clauses();
      var images = [];
      for (var i = 0; i < clauses.length; i++) {
        var clause = clauses[i];
        var url = clause.text();
        if (clause.accepts() == 'images' && url) {
          images.push(new model.Image(url, url));
        }
      }
      return images;
    }, self);
    
    self.isEmpty = ko.computed(function() {
      var clauses = self.clauses();
      for (var i = 0; i < clauses.length; i++) {
        var clause = clauses[i];
        if (clause.text()) {
          return false;
        }
      }
      return true;
    }, self);
  };
  
  // Results
  
  module.VideoSource = function(data) {
    var self = this;
    self.format = data ? (data.format || '') : '';
    self.url = data.url || '';
  };

  module.VideoMetadata = function(data) {
    var self = this;
    utils.set(self, data, {
      title: '',
      description: '',
      summary: '',
      subject: '',
      publicationDate: '',
      genres: [],
      language: '',
      license: 'Copyrighted',
      contributors: [],
      keywords: [],
      colorSpace: '',
      persons: [],
      objects: [],
      places: [],
      entities: []
    });
  };

  module.VideoStats = function(data) {
    var self = this;
    utils.set(self, data, {
      favorites: 0,
      likes: 0,
      dislikes: 0,
      views: 0,
      downloads: 0
    });
  };

  module.VisualResult = function(data) {
    var self = this;
    self.uri = data.uri || null;
    self.videoUri = data.videoUri || null;
    self.keyframe = data.keyframe || null;
    self.startTimeMillis = data.startTimeMillis || 0;
    self.endTimeMillis = data.endTimeMillis || null;
    self.durationMillis = data.durationMillis || null;
    self.speech = data.speech || null;
    self.children = data.children || [];
    self.visualTags = ko.observableArray(data.visualTags || []);
  
    self.formattedDuration = function() {
      return utils.formatTime(self.durationMillis);
    };
  };

  module.Video = function(data) {
    var self = this;
    $.extend(self, new module.VisualResult(data));
    self.sources = data.sources || [];
    self.metadata = new module.VideoMetadata(data.metadata);
    self.stats = ko.observable(new module.VideoStats(data.stats));
    
    self.downloadUrl = function() {
      var matches = $.grep(self.sources, function(source) {
        return source.url.slice(-3) == 'mp4';
      });
      return (matches.length > 0 ? matches[0] : self.sources[0]).url;
    };
  };

  module.Videos = function(data) {
    var self = this;
  
    if (data) {
      $.each(data, function(key, item) {
        self[key] = new module.Video(item);
      });
    }
  };

  module.VideoSegment = function(data) {
    var self = this;
    $.extend(self, new module.VisualResult(data));
  };

  module.Segments = function(data) {
    var self = this;
  
    if (data) {
      $.each(data, function(key, item) {
        self[key] = new module.VideoSegment(item);
      });
    }
  };

  module.SearchResult = function(data, resultSet) {
    var self = this;
    self.score = data.score || 0.0;
    self.rank = data.rank || 0;
    self.scores = data.scores || [];
    self.type = data.type || 'Segment';
    self.uri = data.uri || null;
    self.resultSet = resultSet;
  
    self.isSegment = ko.computed(function() {
      return self.type == 'Segment';
    }, this);
  
    self.videoUri = ko.computed(function() {
      if (self.isSegment()) {
        return self.resultSet.segments[self.uri].videoUri;
      } 
      return self.uri;
    }, this);
  
    self.video = ko.computed(function() {
      return self.resultSet.videos[self.videoUri()];
    }, this);
  
    self.segment = ko.computed(function() {
      if (self.isSegment()) {
        return self.resultSet.segments[self.uri];
      }
      return null;
    }, this);
  
    self.segmentOrVideo = ko.computed(function() {
      if (self.isSegment()) {
        return self.segment();
      } else {
        return self.video();
      }
    }, this);
  
    self.keyframe = ko.computed(function() {
      return self.segmentOrVideo().keyframe;
    }, this);
  
    self.metadata = ko.computed(function() {
      return self.video().metadata;
    }, this);
  
    self.viewCount = ko.computed(function() {
      return self.video().stats().views;
    }, this);
  
    self.downloadCount = ko.computed(function() {
      return self.video().stats().downloads;
    }, this);
  
    self.formattedScore = ko.computed(function() {
      return Math.round(self.score * 100.0) + '%';
    }, this);
  
    self.formattedDuration = ko.computed(function() {
      return self.segmentOrVideo().formattedDuration();
    }, this);
  };

  module.ResultSet = function(data) {
    var self = this;
    self.queryId = '';
    self.videos = new module.Videos();
    self.segments = new module.Segments();
    self.ranking = [];
    self.evidence = [];
  
    if (data) {
      self.queryId = data.queryId || '';
      self.videos = new module.Videos(data.videos);
      self.segments = new module.Segments(data.segments);
      self.evidence = ko.observableArray(data.evidence);
    
      self.ranking = ko.utils.arrayMap(data.ranking, function(item) {
        return new module.SearchResult(item, self);
      });
    }
  };
  
  // Assets

  module.Asset = function(data) {
    var self = this;
    self.uri = null;
    self.type = 'Segment';
    self.video = null;
    self.segment = null;
  
    if (data) {
      self.uri = data.uri;
      self.type = data.type || 'Segment';
      self.video = data.video ? new module.Video(data.video) : null;
      self.segment = data.segment ? new module.VideoSegment(data.segment) : null;
    }
  
    self.startTimeSeconds = ko.computed(function() {
      var millis = self.segment ? self.segment.startTimeMillis : 0;
      return millis / 1000.0;
    }, this);
    
    self.segmentOrVideo = ko.computed(function() {
      if (self.segment) {
        return self.segment;
      } else {
        return self.video;
      }
    }, this);
    
    self.keyframe = ko.computed(function() {
      return self.segmentOrVideo().keyframe;
    }, this);
  };
  
  return module;
})();