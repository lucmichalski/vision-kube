//
// knockout.js view models
//
// Author: Kevin McGuinness <kevin.mcguinness@dcu.ie>
//
var view = (function() {
  
  var QueryViewModel = function(root) {
    var self = this;
    self.query = root.query;
    self.relatedTerms = ko.observableArray([]);
  
    self.search = function() {
      $('#filters-pane > .collapsible-content').slideUp('fast');
      
      var query = ko.mapping.toJS(self.query);
      console.log(query);
      root.server.post('/api/query/create/', query, function(response) {
        location.hash = '/search/' + response.queryId;
      });
    };
  
    self.searchRelatedQuery = function() {
      console.log(self.relatedTerms());
      self.query(new model.Query(this.displayName));
      self.search();
      return false;
    };
  
    self.clearQueryImages = function() {
      self.query().queryImages([]);
    };
  
    self.removeQueryImage = function(image) {
      self.query().queryImages.remove(image);
    };
  
    self.clearQuery = function() {
      self.query(new model.Query());
    };
  
    self.uploadImage = function() {
      $('form[id=image-upload]').ajaxSubmit({
        dataType: 'json',
        method: 'post',
        type: 'post',
        xhrFields: { 
          withCredentials: true 
        },
        success: function(response) {
          $('#image-upload-modal').modal('hide');
          var image = new model.Image(response.url, response.url);
          self.query().queryImages.push(image);
        }
      });
    };
    
    self.queryImageCount = ko.computed(function() {
      var query = self.query();
      if (query.type() == 'simple') {
        return query.queryImages().length;
      }
      return 0;
    }, self);
  };
  
  var AdvancedQueryViewModel = function(root) {
    var self = this;
    self.query = ko.observable(new model.AdvancedQuery());
    
    self.addTextSearchClause = function() {
      var clause = new model.AdvancedQueryClause('meta speech');
      self.query().clauses.push(clause);
    };
    
    self.addSimilaritySearchClause = function() {
      var clause = new model.AdvancedQueryClause('instance-i');
      self.query().clauses.push(clause);
    };
    
    self.removeClause = function(clause) {
      self.query().clauses.remove(clause);
    };
    
    self.clear = function() {
      self.query().clauses.removeAll();
      self.addTextSearchClause();
    };
    
    self.search = function() {
      var query = ko.mapping.toJS(self.query());
      console.log('Advanced Search', query);
      
      root.server.post('/api/query/create/', query, function(response) {
        location.hash = '/search/' + response.queryId;
      });
    };
    
    self.uploadImage = function(clause, elem) {
      $(elem).parents('form').ajaxSubmit({
        dataType: 'json',
        method: 'post',
        type: 'post',
        xhrFields: { withCredentials: true },
        success: function(response) {
          clause.text(response.url);
        }
      });
    };
    
    self.isNonEmpty = ko.computed(function() {
      return !self.query().isEmpty();
    }, self);
  };
  
  var FiltersViewModel = function(root) {
    var self = this;
    self.resultsView = root.resultsView;
    self.collectionStats = ko.observable(null);
    self.titleContains = ko.observable("");
    self.summaryContains = ko.observable("");
    self.subjectContains = ko.observable("");
    self.selectedGenres = ko.observableArray([]);
    self.customGenreText = ko.observable("");
    self.selectedEntities = ko.observableArray([]);
    self.customEntitiesText = ko.observable("");
    self.selectedKeywords = ko.observableArray([]);
    self.customKeywordsText = ko.observable("");
    self.selectedPersons = ko.observableArray([]);
    self.customPersonsText = ko.observable("");
    self.selectedContributors = ko.observableArray([]);
    self.customContributorsText = ko.observable("");
    self.dateFilterEnabled = ko.observable(false);
    self.startYear = ko.observable(1980);
    self.endYear = ko.observable(2013);
  
    self.fetchCollectionStats = function() {
      root.server.get('/api/collection/stats/', function(response) {
        console.log(response);
        self.collectionStats(response.stats);
      });
    };
    
    self.fetchCollectionStatsIfNecessary = function() {
      if (!self.collectionStats()) {
        self.fetchCollectionStats();
      }
    };
    
    self.applyFilters = function() {
      self.resultsView.liveFilters(self.filters());
    };
    
    self.resetFilters = function() {
      self.selectedKeywords([]);
      self.selectedEntities([]);
      self.selectedGenres([]);
      self.selectedPersons([]);
      self.selectedContributors([]);
      self.titleContains("");
      self.summaryContains("");
      self.subjectContains("");
      self.customGenreText("");
      self.customKeywordsText("");
      self.customEntitiesText("");
      self.customPersonsText("");
      self.customContributorsText("");
      self.dateFilterEnabled(false);
      self.startYear(1980);
      self.endYear(2013);
      self.dateFilterEnabled(false);
      self.applyFilters();
    };
    
    self.commonValues = function(type) {
      var stats = self.collectionStats();
      if (stats && type in stats) {
        return $.map(stats[type], function(stat) {
          return stat.value;
        });
      }
      return [];
    };
    
    var filterTerms = function(listAccessor, customAccessor) {
      var values = listAccessor();
      var result = [];
      for (var i = 0; i < values.length; i++) {
        var text = values[i];
        if (text == 'custom') {
          text = customAccessor();
        }
        text = text.trim();
        if (text) {
          result.push(text);
        }
      }
      return result;
    };
    
    self.genreFilter = ko.computed(function() {
      return filterTerms(self.selectedGenres, self.customGenreText);
    }, self);
    
    self.entityFilter = ko.computed(function() {
      return filterTerms(self.selectedEntities, self.customEntitiesText);
    }, self);
    
    self.keywordsFilter = ko.computed(function() {
      return filterTerms(self.selectedKeywords, self.customKeywordsText);
    }, self);
    
    self.personsFilter = ko.computed(function() {
      return filterTerms(self.selectedPersons, self.customPersonsText);
    });
    
    self.contributorsFilter = ko.computed(function() {
      return filterTerms(self.selectedContributors, self.customContributorsText);
    });
    
    self.titleFilter = ko.computed(function() {
      var title = self.titleContains().trim();
      return (title) ? [title] : [];
    }, self);
    
    self.subjectFilter = ko.computed(function() {
      var subject = self.subjectContains().trim();
      return (subject) ? [subject] : [];
    }, self);
    
    self.summaryFilter = ko.computed(function() {
      var summary = self.summaryContains().trim();
      return (summary) ? [summary] : [];
    }, self);
    
    self.dateFromFilter = ko.computed(function() {
      if (self.dateFilterEnabled()) {
        return new Date(self.startYear(), 0, 1);
      }
      return null;
    }, self);
    
    self.dateToFilter = ko.computed(function() {
      if (self.dateFilterEnabled()) {
        return new Date(self.endYear()+1, 0, 1);
      }
      return null;
    }, self);
    
    self.filters = ko.computed(function() {
      return  {
        metadata: {
          genres: self.genreFilter(),
          entities: self.entityFilter(),
          keywords: self.keywordsFilter(),
          persons: self.personsFilter(),
          contributors: self.contributorsFilter(),
          title: self.titleFilter(),
          subject: self.subjectFilter(),
          summary: self.summaryFilter()
        },
        date: {
          from: self.dateFromFilter(),
          to: self.dateToFilter()
        }
      };
    }, self);
    
    self.commonGenres = ko.computed(function() {
      return self.commonValues('Genre');
    }, self);
    
    self.commonKeywords = ko.computed(function() {
      return self.commonValues('Keywords');
    }, self);
    
    self.commonEntities = ko.computed(function() {
      return self.commonValues('entities');
    }, self);
    
    self.commonPersons = ko.computed(function() {
      return self.commonValues('persons');
    });
    
    self.commonContributors = ko.computed(function() {
      return self.commonValues('contributors');
    }, self);
  };
  
  var InitialViewModel = function(root) {
    var self = this;
    self.recent = root.recent;
    self.history = root.history;
  };

  var ResultsViewModel = function(root) {
    var self = this;
  
    self.viewTypes = ['Thumbnails', 'Detailed'];
    self.viewType = ko.observable();
    self.displayTypes = ['Segment', 'Program'];
    self.displayType = ko.observable(self.displayTypes[0]);
    self.availableSortCriteria = ['Relevance', 'Published Date', 'View Count', 'Video Duration'];
    self.sortBy = ko.observable(self.availableSortCriteria[0]);
    self.displayCountOptions = [10, 25, 50, 75, 100];
    self.displayCount = ko.observable(root.preferences().resultsPerPage());
    self.resultSet = root.resultSet;
    self.liveFilters = ko.observable(null);
    
    self.filteredVideos = ko.computed(function() {
      var filters = self.liveFilters();
      var videos = self.resultSet().videos;
      if (filters) {
        return utils.objectGrep(videos, function(video) {
          var metadata = video.metadata;
          for (var filterName in filters.metadata) {
            var filter = filters.metadata[filterName];
            var target = metadata[filterName];
            if (filter.length && !utils.textMatchesAny(target, filter)) {
              return false;
            }
          }
          
          // Apply date filters
          if (filters.date.from || filters.date.to) {
            var publicationDate = utils.parseDate(metadata.publicationDate);
            if (filters.date.from && publicationDate < filters.date.from) {
              return false;
            }
            if (filters.date.to && publicationDate >= filters.date.to) {
              return false;
            }
          }
          return true;
        });
      } 
      return videos;
    }, self);
    
    self.filteredSegments = ko.computed(function() {
      var segments = self.resultSet().segments;
      if (self.liveFilters()) {
        var videos = self.filteredVideos();
        return utils.objectGrep(segments, function(segment) {
          return segment.videoUri in videos;
        });
      }
      return segments;
    }, self);
    
    self.filteredResults = ko.computed(function() {
      if (self.liveFilters()) {
        var filteredVideos = self.filteredVideos();
        return $.grep(self.resultSet().ranking, function(item) {
          return item.videoUri() in filteredVideos;
        });
      }
      return self.resultSet().ranking.slice();
    }, self);
  
    self.sortedResults = ko.computed(function() {
      var results = self.filteredResults();
    
      var comparators = {
        'Relevance': function(a, b) {
          return b.score - a.score;
        },
        'Published Date': function(a, b) {
          var d1 = utils.parseDate(a.metadata().publicationDate);
          var d2 = utils.parseDate(b.metadata().publicationDate);
          return d2 - d1;
        },
        'View Count': function(a, b) {
          return b.viewCount() - a.viewCount();
        },
        'Video Duration': function(a, b) {
          return b.video().durationMillis - a.video().durationMillis;
        }
      };
    
      var compare = comparators[self.sortBy()];
      results.sort(compare);
      return results;
    }, this);
    
    self.clear = function() {
      self.resultSet(new model.ResultSet());
      self.displayCount(root.preferences().resultsPerPage());
    };
  
    self.programCount = ko.computed(function() {
      return Object.keys(self.filteredVideos()).length;
    }, this);
  
    self.segmentCount = ko.computed(function() {
      return Object.keys(self.filteredSegments()).length;
    }, this);
  
    self.countResults = function(type) {
      return type == 'Segment' ? self.segmentCount() : self.programCount();
    };
  
    self.resultCount = ko.computed(function() {
      return self.countResults(self.displayType());
    }, this);
  
    self.canDisplayMore = ko.computed(function() {
      return self.resultCount() > self.displayCount();
    }, this);
  
    self.undisplayedResultCount = ko.computed(function() {
      return Math.max(self.resultCount() - self.displayCount(), 0);
    }, this);
  
    self.displayMore = function() {
      var amountToDisplay = Math.min(self.displayCount() + 
        root.preferences().resultsPerPage(), self.resultCount());
      self.displayCount(amountToDisplay);
    };
  
    self.programs = ko.computed(function() {
      var results = self.sortedResults();
      var videoUris = {};
      return results.filter(function(result) {
        var videoUri = result.videoUri();
        if (videoUri in videoUris) {
          return false;
        }
        videoUris[videoUri] = true;
        return true;
      }).slice(0, self.displayCount());
    }, this);
  
    self.segments = ko.computed(function() {
      return self.sortedResults().slice(0, self.displayCount());;
    }, this);
  
    self.results = ko.computed(function() {
      var display = self.displayType();
      return (display == 'Segment') ? self.segments() : self.programs();
    }, this);
  
    self.drawSegmentPosition = function(canvas, context, result) {
      var video = result.video();
      var segment = result.segmentOrVideo();
      var duration = video.durationMillis.toFixed();
      var start = segment.startTimeMillis / duration;
      var end = segment.endTimeMillis / duration;
      var width = canvas.width;
      var height = canvas.height;
      context.save();
      context.fillStyle = '#EB4823';
      //context.fillStyle = '#01AEF0';
      context.fillRect(start * width, 0, end * width, height);
      context.restore();
    };
  
    self.submitFeedback = function(type) {
      var feedback = {
        queryId: self.resultSet().queryId,
        first: 0,
        count: self.results().length,
        type: type
      }
      root.server.post('/api/feedback/results/', feedback);
    };
  
    self.submitPositiveFeedback = function() {
      self.submitFeedback('positive');
    };
  
    self.submitNegativeFeedback = function() {
      self.submitFeedback('negative');
    };
  };
  
  var PopularVideosViewModel = function(root) {
    var self = this;
    
    self.availableTypes = [
      'viewed', 'downloaded', 'favorited', 'liked', 'disliked'];
      
    self.type = ko.observable('viewed');
    self.results = ko.observableArray([]);
    self.displayCount = ko.observable(root.preferences().resultsPerPage());
    
    self.results.subscribe(function(newValue) {
      self.displayCount(root.preferences().resultsPerPage());
    });
  
    self.canDisplayMore = ko.computed(function() {
      return self.results().length > self.displayCount();
    }, this);
  
    self.undisplayedResultCount = ko.computed(function() {
      return Math.max(self.results().length - self.displayCount(), 0);
    }, this);
  
    self.displayMore = function() {
      var amountMore = root.preferences().resultsPerPage();
      var amountToDisplay = Math.min(self.displayCount() + amountMore, 
        self.results().length);
      self.displayCount(amountToDisplay);
    };
    
    self.displayedResults = ko.computed(function() {
      return self.results.slice(0, self.displayCount());
    }, self);
  };

  var CollectionsViewModel = function(root) {
    var self = this;
    self.name = "Collections";
    self.link = '#/user/collections';
    self.collections = root.collections;
    self.newCollection = ko.observable(new model.Collection());
  
    self.createCollection = function() {
      var collection = self.newCollection();
      root.server.post('/api/collections/create/', collection, function(response) {
        console.log('collectionCreated', response);
        ko.mapping.fromJS(response, {}, collection);
        self.collections.push(collection);
        self.newCollection(new model.Collection());
      });
    };
  
    self.removeCollection = function(collection) {
      collection = ko.utils.unwrapObservable(collection);
      var name = ko.utils.unwrapObservable(collection.name);
      var msg = "Remove the " + name + " collection?";
      bootbox.confirm(msg, function(result) {
        if (result) {
          root.server.post('/api/collections/remove/', collection, function(result) {
            self.collections.remove(collection);
          });
        }
      }); 
    };
  };

  var FavoritesViewModel = function(root) {
    var self = this;
    self.name = "Favorites";
    self.link = '#/user/favorites';
    self.favorites = ko.observableArray([]);
  };

  var SearchHistoryViewModel = function(root) {
    var self = this;
    self.name = "Search History";
    self.link = '#/user/history';
    self.history = root.history;
  };

  var RecentViewModel = function(root) {
    var self = this;
    self.name = "Recently Viewed";
    self.link = '#/user/recent';
    self.recent = root.recent;
  };

  var NotesViewModel = function(root) {
    var self = this;
    self.name = "Notes";
    self.link = '#/user/notes';
    self.notes = root.notes;
  
    self.removeNote = function(note) {
      var msg = "Are you sure you wish to delete this note?";
      bootbox.confirm(msg, function(result) {
        if (result) {
          root.server.post('/api/notes/remove/', note, function(result) {
            self.notes.remove(note);
          });
        }
      }); 
    };
  };

  var PreferencesViewModel = function(root) {
    var self = this;
    self.name = "Preferences";
    self.link = '#/user/preferences';
    self.preferences = root.preferences;
  
    self.savePreferences = function() {
      var prefs = ko.mapping.toJS(self.preferences());
      prefs.resultsPerPage = parseInt(prefs.resultsPerPage);
      root.server.post('/api/preferences/update/', prefs, function(result) {
        ko.mapping.fromJS(result, {}, self.preferences);
      });
    };
  };
  
  var JobsViewModel = function(root) {
    var self = this;
    self.name = "Jobs";
    self.link = '#/user/jobs';
    self.jobs = root.jobs;
    
    self.refresh = function() {
    	root.server.get('/api/cutter/jobs/', function(data) {
        self.jobs(data);
      });
    };
    
    self.removeCompletedJobs = function() {
    	root.server.get('/api/cutter/clear/completed/', function(data) {
        self.jobs(data);
      });
    };
    
    self.removeJob = function(jobId) {
    	root.server.get('/api/cutter/remove/' + jobId, function(data) {
        self.jobs(data);
      });
    };
  };

  var UserViewModel = function(root) {
    var self = this;
  
    self.collectionsView = new CollectionsViewModel(root);
    self.favoritesView = new FavoritesViewModel(root);
    self.historyView = new SearchHistoryViewModel(root);
    self.recentView = new RecentViewModel(root);
    self.notesView = new NotesViewModel(root);
    self.preferencesView = new PreferencesViewModel(root);
    self.jobsView = new JobsViewModel(root);
    self.currentView = ko.observable(self.collectionsView);
  
    self.views = [
      self.collectionsView, 
      self.favoritesView,
      self.historyView, 
      self.recentView, 
      self.notesView, 
      self.jobsView,
      self.preferencesView ];
  };

  var AssetViewModel = function(root) {
    var self = this;
    self.asset = ko.observable(null);
    self.transcript = ko.observableArray([]);
    self.notes = ko.observableArray([]);
    self.relatedVideos = ko.observable(new model.ResultSet());
    self.relatedSegments = ko.observable(new model.ResultSet());
    self.faceTracks = ko.observableArray([]);
    self.newNoteText = ko.observable('');
    self.displayOnlyUserNotes = ko.observable(false);
    
    self.displayMetadata = [
      {name: 'keywords', title: 'Keywords', type:'list'},
      {name: 'genres', title: 'Genres', type:'list'},
      {name: 'contributors', title: 'Contributors', type:'list'},
      {name: 'entities', title: 'Entities', type:'list'},
      {name: 'objects', title: 'Objects', type:'list'},
      {name: 'persons', title: 'People', type:'list'},
      {name: 'places', title: 'Places', type:'list'}
    ];
    
    self.seekVideo = function(timeMillis) {
      var timeSecs = timeMillis / 1000.0;
      var video = $('#asset-page video')[0];
      video.currentTime = timeSecs;
      return false;
    };
    
    self.hasMetadata = function(name) {
      var meta = self.asset().video.metadata[name];
      meta = ko.utils.unwrapObservable(meta);
      return meta && meta.length > 0 && meta[0].length > 0;
    };
  
    self.annotate = function(action) {
      var videoUri = self.asset().video.uri;
      var url = ['/api', action, videoUri].join('/');
      root.server.get(url, function(stats) {
        self.asset().video.stats(new model.VideoStats(stats));
      });
    };
  
    self.like = function() {
      self.annotate('like');
    };
  
    self.dislike = function() {
      self.annotate('dislike');
    };
  
    self.favorite = function() {
      self.annotate('favorite');
    };
  
    self.download = function() {
      self.annotate('download');
      var url = self.asset().video.downloadUrl();
      var path = '/download?url=' + url;
      window.location.href = utils.urljoin(settings.baseUrl, path);
    };
    
    self.downloadMetadata = function(format) {
      format = format || 'json';
      var uri = self.asset().uri;
      var path = '/download_metadata?uri=' + uri + '&format=' + format;
      window.location.href = utils.urljoin(settings.baseUrl, path);
    };
    
    self.downloadTranscript = function(format) {
      var video = self.asset().video;
      var uri = video ? video.uri : self.asset().uri;
      var path = '/download_transcript?uri=' + uri;
      window.location.href = utils.urljoin(settings.baseUrl, path);
    };
    
    self.filteredNotes = ko.computed(function() {
      if (self.displayOnlyUserNotes()) {
        var currentUser = ko.utils.unwrapObservable(root.preferences().user);
        return ko.utils.arrayFilter(self.notes(), function(note) {
          return note.user == currentUser;
        });
      }
      return self.notes();
    }, this);
  
    self.newNote = function(type) {
      var note = {
        text: self.newNoteText(),
        type: type,
        videoUri: self.asset().video.uri,
        dateCreated: (new Date()).toISOString()
      };
      root.server.post('/api/notes/add/', note, function(newNote) {
        self.newNoteText('');
        console.log(newNote);
        self.notes.push(newNote);
      });
    };
  
    self.newPublicNote = function() {
      self.newNote('public');
    };
  
    self.newPrivateNote = function() {
      self.newNote('private');
    };
  };
  
  var CutterViewModel = function(root) {
    var self = this;
    self.asset = ko.observable(null);
    self.player = ko.observable(null);
    self.videoDuration = ko.observable(0);
    self.startTime = ko.observable(0);
    self.endTime = ko.observable(0);
    self.cutJob = ko.observable(null);
    
    self.videoDuration.subscribe(function(newValue) {
      var asset = self.asset();
      if (asset && asset.type == 'Segment') {
        // Use segment boundaries as defaults
        var segment = self.asset().segment;
        self.startTime(segment.startTimeMillis / 1000.0);
        self.endTime(segment.endTimeMillis / 1000.0);
      } else {
        // Use video duration as default
        self.endTime(newValue);
      }
    });
    
    self.reset = function() {
      self.asset(null);
      self.startTime(0);
      self.endTime(0);
      self.videoDuration(0);
      self.cutJob(null);
    };
    
    self.setStart = function() {
      self.startTime(self.player().currentTime());
    };
    
    self.setEnd = function() {
      self.endTime(self.player().currentTime());
    };
    
    self.jumpToStartOfSegment = function() {
      self.player().seek(self.startTime());
    };
    
    self.jumpToEndOfSegment = function() {
      self.player().seek(self.endTime());
    };
    
    self.playSegment = function() {
      self.player().seek(self.startTime(), true);
    };
    
    self.playingVideoUrl =function() {
      return self.player() ? self.player().currentSourceUrl() : null;
    };
    
    self.formattedStartTime = ko.computed(function() {
      return utils.formatTime(self.startTime()*1000);
    }, self);
    
    self.formattedEndTime = ko.computed(function() {
      return utils.formatTime(self.endTime()*1000);
    }, self);
    
    self.segmentDuration = ko.computed(function() {
      return self.endTime() - self.startTime();
    }, self);
    
    self.formattedDuration = ko.computed(function() {
      return utils.formatTime(self.segmentDuration()*1000);
    }, self);
    
    self.hasActiveJob = ko.computed(function() {
      return self.cutJob() ? self.cutJob().status() == "PENDING" : false;
    }, self);
    
    self.jobId = ko.computed(function() {
      return self.cutJob() ? self.cutJob()._id() : null;
    }, self);
    
    self.checkJobStatus = function() {
      var jobId = self.jobId();
      if (jobId) {
      	root.server.get('/api/cutter/check/' + jobId, function(job) {
          ko.mapping.fromJS(job, {}, self.cutJob); 
          
          // If still running, check again in 2 seconds
          if (self.hasActiveJob()) {
            setTimeout(function() {self.checkJobStatus()}, 2000);
          }
        }, true);
      }
    };
    
    self.cut = function() {
      var options = {
        startTime: self.startTime(),
        duration: self.segmentDuration(),
        videoUrl: self.playingVideoUrl()
      };
    	root.server.post('/api/cutter/cut/', options, function(job) {
        self.cutJob(ko.mapping.fromJS(job)); 
        setTimeout(function() {self.checkJobStatus()}, 2000);
      });
    };
  };

  var PreviewViewModel = function(root) {
    var self = this;
    self.visible = ko.observable(false);
    self.result = ko.observable(null);
  
    self.sources = ko.computed(function() {
      return self.result() ? self.result().video().sources : [];
    }, this);
  
    self.startTimeSeconds = ko.computed(function() {
      return self.result() ? self.result().segmentOrVideo().startTimeMillis / 1000.0 : 0;
    }, this);
  
    self.durationSeconds = ko.computed(function() {
      return self.result() ? self.result().segmentOrVideo().durationMillis / 1000.0 : 0;
    }, this);
  
    self.keyframeUrl = ko.computed(function() {
      return self.result() ? self.result().segmentOrVideo().keyframe.imageUrl : null;
    }, this);
  
    self.videoSpec = ko.computed(function() {
      var result = self.result();
    
      if (result) {
        var video = result.video();
        var segment = result.segment();
        if (!segment) {
          segment = video;
        }
        return {
          sources: video.sources,
          startTime: segment.startTimeMillis / 1000.0,
          duration: segment.durationMillis / 1000.0,
          poster: segment.keyframe.imageUrl,
          width: 320,
          height: 240
        };
      }
      return { };
    }, this);
  
    self.showForVideo = function(video) {
      var video = ko.toJS(video);
      self.show({
        video: ko.observable(video),
        segment: ko.observable(null),
        segmentOrVideo: ko.observable(video),
        keyframe: ko.observable(video.keyframe)
      });
    };
  
    self.show = function(result) {
      console.log(ko.toJS(result));
      self.result(result);
      self.visible(true);
    };
  
    self.hide = function(result) {
      self.visible(false);
    };
  };

  var KeyframeViewModel = function(root) {
    var self = this;
    self.video = ko.observable(null);
    self.keyframes = ko.observableArray([]);
    self.displayCount = ko.observable(root.preferences().resultsPerPage());
    
    self.keyframes.subscribe(function(newValue) {
      self.displayCount(root.preferences().resultsPerPage());
    });
  
    self.canDisplayMore = ko.computed(function() {
      return self.keyframes().length > self.displayCount();
    }, this);
  
    self.undisplayedResultCount = ko.computed(function() {
      return Math.max(self.keyframes().length - self.displayCount(), 0);
    }, this);
  
    self.displayMore = function() {
      var amountMore = root.preferences().resultsPerPage();
      var amountToDisplay = Math.min(self.displayCount() + amountMore, 
        self.keyframes().length);
      self.displayCount(amountToDisplay);
    };
    
    self.displayedKeyframes = ko.computed(function() {
      return self.keyframes.slice(0, self.displayCount());
    }, self);
  
    self.seekVideo = function(timeMillis) {
      var timeSecs = timeMillis / 1000.0;
      var video = $('#keyframe-video-player')[0];
      video.currentTime = timeSecs;
    };
    
    self.asset = ko.computed(function() {
      if (self.video()) {
        return new model.Asset({
          uri: self.video().uri,
          video: self.video(),
          type: 'Program'
        });
      }
      return null;
    }, this);
  };

  var ViewModel = function() {
    var self = this;
  
    // state
    self.server = new ajax.Endpoint(self, settings.apiPrefix);
    self.query = ko.observable(new model.Query());
    self.recent = ko.observableArray([]);
    self.history = ko.observableArray([]);
    self.resultSet = ko.observable(new model.ResultSet());
    self.collections = ko.observableArray([]);
    self.preferences = ko.observable(new model.Preferences());
    self.notes = ko.observableArray([]);
    self.jobs = ko.observableArray([]);
    self.statusText = ko.observable("");
    self.loading = ko.observable(false);
  
    // views
    self.previewView = new PreviewViewModel(self);
    self.queryView = new QueryViewModel(self);
    self.advancedQueryView = new AdvancedQueryViewModel(self);
    self.initialView = new InitialViewModel(self);
    self.userView = new UserViewModel(self);
    self.resultsView = new ResultsViewModel(self);
    self.assetView = new AssetViewModel(self);
    self.cutterView = new CutterViewModel(self);
    self.keyframeView = new KeyframeViewModel(self);
    self.filtersView = new FiltersViewModel(self);
    self.popularView = new PopularVideosViewModel(self);
  
    // selected view
    self.currentView = ko.observable(self.initialView);
    
    self.goHome = function() {
      self.query(new model.Query());
      self.resultSet(new model.ResultSet());
      self.currentView(self.initialView);
      location.hash = '/';
      return true;
    };
    
    self.highlightSearchTerms = function(text) {
      text = utils.escapeHTML(ko.utils.unwrapObservable(text));
      var terms = utils.tokenizeQueryString(self.query().text());
      
      for (var i = 0; i < terms.length; i++) {
        var term = terms[i].trim();
        if (term.length < 2) {
          // Skip over single character terms
          continue;
        }
        
        var regex = new RegExp("\\b(" + term + ")\\b", "gi");
        text = text.replace(regex, '<span class="highlighted">$1</span>');
      }
      return text;
    };
  
    self.addVideoToFavorites = function(video) {
      video = ko.utils.unwrapObservable(video);
      var uri = ko.utils.unwrapObservable(video.uri);
      var url = ['/api', 'favorite', uri].join('/');
      self.server.get(url, function(response) {
        console.log(response);
      });
    };
  
    self.addVideoToCollection = function(video, collection) {
      collection = ko.utils.unwrapObservable(collection);
      video = ko.utils.unwrapObservable(video);
      collection.videos.push(video);
      self.server.post('/api/collections/update/', collection,
        function(response) {
        console.log(response);
      });
    };
  
    self.removeVideoFromCollection = function(video, collection) {
      collection = ko.utils.unwrapObservable(collection);
      video = ko.utils.unwrapObservable(video);
      var msg = "Remove video from the " + 
        ko.utils.unwrapObservable(collection.name) + " collection?";
      bootbox.confirm(msg, function(result) {
        if (result) {
          collection.videos.remove(video);
          self.server.post('/api/collections/update/', collection,
            function(response) {
            console.log(response);
          });
        }
      }); 
    };
  
    self.addResultToSimilaritySearch = function(result) {
      result = ko.utils.unwrapObservable(result);
      var kf = ko.utils.unwrapObservable(result.keyframe);
      self.addKeyframeToSimilaritySearch(kf);
    };
  
    self.addKeyframeToSimilaritySearch = function(kf) {
      var queryImage = new model.Image(kf.imageUrl, kf.thumbnailUrl);
      self.query().queryImages.push(queryImage);
    };
    
    self.quickSearch = function(text) {
      console.log(text);
      
      // Enqoute text if multiword
      if (utils.isMultiWord(text)) {
        text = utils.enquote(text);
      }
      
      // Create metadata only search
      var query = new model.Query(text);
      query.textQueryType(model.QueryType.text[0]);
      
      self.query(query);
      self.queryView.search();
      return false;
    };
    
    self.quickSimilaritySearchForKeyframe = function(kf) {
      console.log(kf.imageUrl, kf.thumbnailUrl);
      var queryImage = new model.Image(kf.imageUrl, kf.thumbnailUrl);
      var query = new model.Query();
      query.queryImages.push(queryImage);
      self.query(query);
      self.queryView.search();
      return false;
    };
    
    self.quickSimilaritySearchForResult = function(result) {
      result = ko.utils.unwrapObservable(result);
      var kf = ko.utils.unwrapObservable(result.keyframe);
      return self.quickSimilaritySearchForKeyframe(kf);
    };
  
    self.showUserView = function() {
      location.hash = '/user';
    };
    
    self.hasSimpleQuery = ko.computed(function() {
      return self.query().type() != 'advanced';
    }, self);
    
    self.hasAdvancedQuery = ko.computed(function() {
      return self.query().type() == 'advanced';
    }, self);
  };
  
  return {Model: ViewModel};
})();

