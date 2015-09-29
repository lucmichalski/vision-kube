//
// Video player object
//
// Author: Kevin McGuinness <kevin.mcguinness@dcu.ie>
//
var video = (function() {
  var module = {};
  
  var sortSources = function(sources) {
    
    // Sadly, order matters in some browsers, so we need to sort them
    // Was a problem for me in Chrome (30.0.1573.2 dev)
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

    // Sort sources
    var sources = sources.slice();
    sources.sort(function(s1, s2) {
      return getOrder(s1) - getOrder(s2);
    });
    
    return sources;
  };
  
  module.sortSources = sortSources;
  
  var createVideoElement = function(settings) {
    var video = document.createElement("video");
    video.width = settings.width;
    video.height = settings.height;
    video.controls = settings.controls;
    video.preload = true;
  
    // Set poster
    if (self.settings.poster) {
      video.poster = settings.poster;
    }
    
    // Add sources
    var sources = sortSources(settings.sources);
    for (var i = 0; i < sources.length; i++) {
      var source = document.createElement("source");
      source.src = sources[i].url;
      source.type = sources[i].format;
      video.appendChild(source);
    }
    
    return video;
  };
  
  var getSettings = function(options) {
    
    // Default options
    var settings = {
      width: 640,
      height: 480,
      controls: true,
      autoplay: false,
      startTime: 0,
      sources: [],
      poster: null
    };
    
    // Update with supplied settings
    jQuery.extend(settings, options);
    return settings;
  };
  
  module.Player = function(options) {
    var self = this;
  
    self.settings = getSettings(options);
    self.video = createVideoElement(self.settings);
    
    self.seek = function(time, play) {
      if (self.video) {
        if (play && self.video.paused) {
          self.video.play();
        }
        self.video.currentTime = time;
      }
    };
    
    self.currentTime = function() {
      if (self.video) {
        return self.video.currentTime;
      }
      return 0;
    };
    
    self.currentSourceUrl = function() {
      if (self.video && self.video.currentSrc) {
        return self.video.currentSrc;
      }
      return null;
    };
  
    self.onError = function(event) {
      console.log('Video playback error', event)
    };
    
    self.onSeeking = function() {
      $('.video-loading-indicator', $(self.video).parent()).show();
    };
    
    self.onSeeked = function() {
      $('.video-loading-indicator', $(self.video).parent()).hide();
    };
  
    self.onMetadataLoaded = function() {
      if (self.video) {
        // Jump to start time
        if (self.video.currentTime < self.settings.startTime) {
          self.video.currentTime = self.settings.startTime;
        }
        
        // Call observable with duration, if set
        if (self.settings.duration) {
          self.settings.duration(self.video.duration);
        }
      }
    };
  
    self.onCanPlay = function() {
      if (self.video && self.settings.autoplay) {
        self.video.play();
      }
      $('.video-loading-indicator', $(self.video).parent()).hide();
    };
  
    self.appendTo = function(element) {
      element.appendChild(self.video);
      
      $(self.video).on('remove', function() {
        // Remove listeners etc. when video is removed from DOM. This also 
        // prevents a bug where audio from video remains playing in background
        // when the back button is pressed before the video can play event
        // is triggered
        
        self.remove();
      });
    
      // Add event listeners (must be done after adding to DOM)
      self.video.addEventListener('canplay', self.onCanPlay, false);
      self.video.addEventListener('loadedmetadata', self.onMetadataLoaded, false);
      self.video.addEventListener('onerror', self.onError, false);
      self.video.addEventListener('seeking', self.onSeeking, false);
      self.video.addEventListener('seeked', self.onSeeked, false);
    
      // Load the video
      $('.video-loading-indicator', $(self.video).parent()).show();
      self.video.load();
    };
  
    self.remove = function() {
      if (self.video) {
      
        // Remove event listeners
        self.video.removeEventListener('canplay', self.onCanPlay, false);
        self.video.removeEventListener('loadedmetadata', self.onMetadataLoaded, false);
        self.video.removeEventListener('onerror', self.onError, false);
        self.video.removeEventListener('seeking', self.onSeeking, false);
        self.video.removeEventListener('seeked', self.onSeeked, false);
      
        // Pause playback
        self.video.pause();
      
        // Remove from DOM
        $(self.video).remove();
        self.video = null;
      }
    };
    
    if (self.settings.owner) {
      self.settings.owner(self);
    }
  };
  
  return module;
})();

