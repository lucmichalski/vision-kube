'use strict';
// (c) Copyright 2015 Kevin McGuinness. All Rights Reserved. 

var controllers = angular.module('axeshome.controllers', []);

controllers.controller('MainController', 
  function($scope, $window, $location, loginService, user, uiSettings,
    pendingRequests) 
{
  $scope.titleText = uiSettings.titleText;
  $scope.isLoading = false;
  $scope.searchText = '';
  $scope.loginService = loginService;
  
  // Determines direction of slide animation
  $scope.slideAnimation = '';
  
  // Main nav bar
  $scope.nav = {
    title: 'AXES Home',
    menuAvailable: true,
    backAvailable: false
  };
  
  // Slide out drawer menu
  $scope.menu = {
    visible: false,
    items: [
      {text: 'Home', href: '#/', icon:'home'},
      {text: 'News Feeds', href: '#/news', icon:'rss'},
      {text: 'Browse', href: '#/browse', icon:'tileview'},
      {text: 'Bookmarks', href: '#/bookmarks', icon:'bookmark'},
      {text: 'Advanced Search', href: '#/advanced-search', icon:'search'},
      {text: 'History', href: '#/history', icon:'calendar'},
      {text: 'Profile', href: '#/profile', icon:'user'},
      {text: 'About', href: '#/about', icon:'question'}
    ]
  };
  
  // Auto login user
  user.getProfile({}, function(loginDetails) {
    loginService.login(loginDetails);
  });
  
  var normalizePath = function(path) {
    if (path[0] == '#') {
      path = path.substr(1);
    }
    var n = path.length;
    if (path[path.length-1] == '/') {
      path = path.substr(0, path.length-1);
    }
    return path;
  };

  // Check if the current page matches the href (used for menu highlight)
  $scope.isCurrentPage = function(href) {
    var path = normalizePath($location.path());
    var href = normalizePath(href);
    return path == href;
  };
  
  // Toggle menu drawer visibility
  $scope.toggleMenu = function() {
    $scope.menu.visible = !$scope.menu.visible;
  };
  
  // Hide menu drawer
  $scope.hideMenu = function() {
    $scope.menu.visible = false;
  };
  
  // Go back to previous page and animate transition
  $scope.goBack = function() {
    $scope.hideMenu();
    $scope.slideAnimation = 'slide-back';
    $window.history.back();
  };
  
  // Go forward to page and animate transition
  $scope.goForward = function(path) {
    //console.log(path);
    $scope.hideMenu();
    $scope.slideAnimation = 'slide-forward';
    $location.url(path);
  };
  
  // Go to a page without animation
  $scope.go = function(path) {
    $scope.hideMenu();
    $location.url(path);
  };
  
  // Initialize a primary view 
  $scope.initPrimary = function(title) {
    $scope.nav.title = title;
    $scope.nav.menuAvailable = true;
    $scope.nav.backAvailable = false;
  };
  
  // Initialize a secondary view
  $scope.initSecondary = function(title) {
    $scope.nav.title = title;
    $scope.nav.menuAvailable = false;
    $scope.nav.backAvailable = true;
  };
  
  $scope.setSearchText = function(text) {
    $scope.searchText = text;
  };
  
  $scope.search = function() {
    var query = encodeURIComponent($scope.searchText);
    $scope.go('/search/' + query);
  };
  
  $scope.setLoading = function(loading) {
    $scope.isLoading = loading;
  };
  
  $scope.stopLoading = function() {
    $scope.setLoading(false);
  };
  
  $scope.abortLoading = function() {
    pendingRequests.abortAll();
    $scope.stopLoading();
  };
  
  $scope.hideLoadingIndicatorWhenResolved = function(result) {
    result.$promise.finally($scope.stopLoading);
  };
  
  $scope.logWhenAvailable = function(result) {
    result.$promise.then(function(item) {
      console.log(item);
    });
  };
  
  $scope.loadData = function(func, a1, a2, a3, a4) {
    $scope.setLoading(true);
    var results = func(a1, a2, a3, a4);
    $scope.logWhenAvailable(results);
    $scope.hideLoadingIndicatorWhenResolved(results);
    return results;
  };
});

controllers.controller('HomeController', 
  function($scope, interestingItems, homeTopics) 
{
  $scope.topics = $scope.loadData(homeTopics.query);
  $scope.interestingItems = $scope.loadData(interestingItems.query);
  
});

// Base controller for result lists
controllers.controller('ResultsController', function(
  $scope, utilities, videoStats
) {
  $scope.results = [];
  $scope.videoCount = 0;
  $scope.groupingCriteria = 'Clips';
  $scope.maxResultsToDisplay = 21;
  $scope.viewMode = 'Grid';
  $scope.videoStats = {};
  
  
  
  $scope.fetchResults = function(query, params) {
    
    // reset state
    $scope.results = [];
    $scope.videoCount = 0;
    $scope.groupingCriteria = 'Clips';
    $scope.maxResultsToDisplay = 21;
    $scope.videoStats = {};
    
    // fetch results
    $scope.results = $scope.loadData(query, params);
    
    // Update state when results are retrieved
    $scope.results.$promise.then(function(results) {
      
      // Attach result explaination icons
      utilities.attachResultExplanations(results);
      
      var uniqueVideos = utilities.getUniqueVideos(results);
      $scope.videoCount = uniqueVideos.length;
      
      angular.forEach(uniqueVideos, function(result) {      
        // Fetch video stats
        var videoUri = result.asset.videoUri;
        videoStats.get({id: videoUri}, function(stats) {
          $scope.videoStats[videoUri] = stats;
        });
      });
    });
  };
  
  $scope.showGrid = function() {
    $scope.viewMode = 'Grid';
  };
  
  $scope.showList = function() {
    $scope.viewMode = 'List';
  };
  
  $scope.groupVideos = function() {
    $scope.groupingCriteria = 'Videos';
  };
  
  $scope.groupClips = function() {
    $scope.groupingCriteria = 'Clips';
  };
  
  $scope.getGroupedResultCount = function() {
    if ($scope.groupingCriteria == 'Videos') {
      return $scope.videoCount;
    }
    return $scope.results.length;
  };
  
  $scope.canShowMore = function() {
    var n = $scope.getGroupedResultCount();
    return n > $scope.maxResultsToDisplay;
  };
  
  $scope.showMore = function() {
    $scope.maxResultsToDisplay += 9;
    if ($scope.maxResultsToDisplay >= $scope.results.length) {
      $scope.maxResultsToDisplay = $scope.results.length;
    } 
  };
})

controllers.controller('SearchController', 
  function($scope, $controller, $routeParams, search) 
{
  $controller('ResultsController', {$scope: $scope}); 
  $scope.fetchResults(search.query, {q: $routeParams.query});
  $scope.setSearchText($routeParams.query);
  $scope.queryText = $routeParams.query;
});

controllers.controller('ImageSearchController', 
  function($scope, $controller, $routeParams, imageSearch) 
{
  $controller('ResultsController', {$scope: $scope}); 
  $scope.fetchResults(imageSearch.query, {q: $routeParams.imageUrl});
  $scope.setSearchText('');
  $scope.queryText = 'Image';
});

controllers.controller('AdvancedQueryController', 
  function($scope, availableServices) 
{
  $scope.metadataSearchText = "";
  $scope.speechSearchText = "";
  $scope.categorySearchText = "";
  $scope.faceSearchText = "";
  $scope.instanceSearchText = "";
  
  // Check available search modalities
  availableServices.query(function(services) {
    for (var i = 0; i < services.length; i++) {
      switch (services[i].value) {
      case '#meta':
        $scope.metaAvailable = true;
        break;  
      case '#speech':
        $scope.speechAvailable = true;
        break;
      case '#category-t':
        $scope.categoryAvailable = true;
        break;
      case '#face-t':
        $scope.faceAvailable = true;
        break;
      case '#instance-t':
        $scope.instanceAvailable = true;
        break;
      } 
    }
  });
  
  $scope.doAdvancedSearch = function(type, text) {
    var q = encodeURIComponent(type + ':' + text);
    $scope.go('/advanced-search/' + q);
  };
  
  $scope.searchMetadata = function() {
    $scope.doAdvancedSearch('meta', $scope.metadataSearchText);
  };
  
  $scope.searchSpeech = function() {
    $scope.doAdvancedSearch('speech', $scope.speechSearchText);
  };
  
  $scope.searchCategories = function() {
    $scope.doAdvancedSearch('category-t', $scope.categorySearchText);
  };
  
  $scope.searchFaces = function() {
    $scope.doAdvancedSearch('face-t', $scope.faceSearchText);
  };
  
  $scope.searchInstances = function() {
    $scope.doAdvancedSearch('instance-t', $scope.instanceSearchText);
  };
});

controllers.controller('AdvancedSearchController', 
  function($scope, $routeParams, $controller, advancedSearch) 
{
  console.log('advanced search', $routeParams.query);
  $controller('ResultsController', {$scope: $scope}); 
  $scope.setSearchText('');
  $scope.queryText = $routeParams.query.split(':', 2)[1];
  $scope.fetchResults(advancedSearch.query, {
    text: $scope.queryText,
    clauses: $routeParams.query
  });
});

controllers.controller('NewsItemController', 
  function($scope, $controller, $routeParams, 
    newsItem, newsItemSearch, utilities)
{
  $controller('ResultsController', {$scope: $scope}); 
  $scope.newsItem = newsItem.get({id: $routeParams.feedId});
  $scope.fetchResults(newsItemSearch.query, {id: $routeParams.feedId});
});

controllers.controller('NewsSourceController', 
  function($scope, $controller, $routeParams, newsSourceSearch, utilities)  
{
  $controller('ResultsController', {$scope: $scope}); 
  $scope.fetchResults(newsSourceSearch.query, {id: $routeParams.sourceId});
});

controllers.controller('NewsController', 
  function($scope, newsSources, newsItems, uiSettings) 
{
  $scope.newsSources = $scope.loadData(newsSources.query);
  $scope.newsItems = {};
  $scope.colors = ['#DE524A', '#34495e', '#1abc9c', '#39B3CD'];
  $scope.hideSummary = uiSettings.hideSummaryInNewsView;
  
  $scope.newsSources.$promise.then(function(newsSources) {
    angular.forEach(newsSources, function(source) {
      $scope.newsItems[source.uri] = newsItems.get({id: source.uri});
    });
  });
  
  $scope.searchNewsItem = function(uri) {
    $scope.goForward('/news-item/' + uri);
  };
  
  $scope.searchNewsSource = function(uri) {
    $scope.goForward('/news-source/' + uri);
  };
});

controllers.controller('AssetController', 
  function($scope, $routeParams, assets, utilities, videoStats, imageStore,
    bookmarks, ngDialog, availableServices) 
{
  var displayMetadata = [
    {name: 'keywords', title: 'Keywords', type:'list'},
    {name: 'genres', title: 'Genres', type:'list'},
    {name: 'contributors', title: 'Contributors', type:'list'},
    {name: 'entities', title: 'Entities', type:'list'},
    {name: 'objects', title: 'Objects', type:'list'},
    {name: 'persons', title: 'People', type:'list'},
    {name: 'places', title: 'Places', type:'list'}
  ];
  
  $scope.metadata = [];
  $scope.asset = $scope.loadData(assets.get, {id: $routeParams.assetId});
  $scope.videoSources = [];
  $scope.stats = null;
  $scope.assetSubview = 'faces';
  
  // Check if instance search is avalable
  availableServices.query(function(services) {
    for (var i = 0; i < services.length; i++) {
      if (services[i].value == '#instance-i') {
        $scope.instanceSearchAvailable = true;
      } 
    }
  });
  
  $scope.whenAssetAvailable = function(callback) {
    return $scope.asset.$promise.then(callback);
  };
  
  $scope.whenAssetAvailable(function(asset) {
    
    // fetch video stats
    $scope.stats = videoStats.get({id: asset.videoUri});
    
    // clean up video sources
    $scope.videoSources = utilities.markSourcesAsTrusted(
      utilities.sortVideoSources(asset.videoSources));
    
    // get metadata
    angular.forEach(displayMetadata, function(m) {
      var title = m.title;
      var metadata = asset.metadata[m.name];
      if (metadata && metadata.length > 0 && metadata[0].length > 0) {
        $scope.metadata.push({title: title, value: metadata});
      }
    });
  });
  
  $scope.setSubview = function(name) {
    $scope.assetSubview = name;
  };
  
  $scope.bookmark = function() {
    if ($scope.asset.bookmarked) {
      bookmarks.removeBookmark({id: $scope.asset.videoUri}, function() {
        $scope.asset.bookmarked = false;
      });
    } else {
      bookmarks.addBookmark({id: $scope.asset.videoUri}, function() {
        $scope.asset.bookmarked = true;
      });
    }
  };
  
  $scope.share = function() {
    ngDialog.open({ 
      template: 'views/share.html',
      controller: 'ShareController'
    });
  };
  
  $scope.like = function() {
    var data = {increment: 'likes'};
    if (!$scope.asset.liked) {
      videoStats.update({id: $scope.asset.videoUri}, data, function(stats) {
        $scope.stats = stats;
        $scope.asset.liked = true;
      });
    }
  };
  
  var getCurrentKeyframeImageData = function() {
    var video = $('video')[0];
    
    // The only way to get image pixels is to draw them on a canvas, so create
    // a canvas that is the same size as the video
    var canvas = document.createElement('canvas');
    canvas.width = video.width;
    canvas.height = video.height;
    
    // Render the video on the canvas
    var context = canvas.getContext('2d');
    context.drawImage(video, 0, 0, video.width, video.height);
    
    // And pull the image data for the video
    var imageData = context.getImageData(0, 0, video.width, video.height);
    
    return imageData;
  };
  
  var getCurrentKeyframeDataUrl = function() {
    var video = $('video')[0];
    var canvas = document.createElement('canvas');
    canvas.width = video.width;
    canvas.height = video.height;
    var context = canvas.getContext('2d');
    context.drawImage(video, 0, 0, video.width, video.height);
    return canvas.toDataURL("image/jpeg");
  };
  
  var convertImageDataToBase64 = function(imageData) {
    
    // Encode RGB values as binary string
    var binaryString = '';
    var len = imageData.data.length;
    var pixels = imageData.data;
    
    for (var i = 0; i < len; i+=4) {
      binaryString += String.fromCharCode(pixels[i]);
      binaryString += String.fromCharCode(pixels[i+1]);
      binaryString += String.fromCharCode(pixels[i+2]);
    }
    
    // Convert to base 64
    return btoa(binaryString);
  };
  
  $scope.search = function() {
    $scope.setLoading(true);
    
    // Generate a data URL (e.g. data:image/png;base64,...) for the current 
    // keyframe. Note: this will *only* work if the video comes from the 
    // same origin, or is served with appropriate CORS headers. Otherwise, a
    // security exception will be thrown here.
    var dataUrl = null;
    try {
      dataUrl = getCurrentKeyframeDataUrl();
      
    } catch (err) {
      $scope.setLoading(false);
      console.log(err)
      return;
    }
    
    // Upload the image to the server and get a URL for the uploaded image
    imageStore.save({}, { dataUrl: dataUrl }, function(response) {
      
      // Perform an image search with the resulting URL
      $scope.go('/image-search/' + response.url);
    });
    
  };
});

controllers.controller('ShareController', function($scope) {
  $scope.url = window.location.href;
});

controllers.controller('RelatedContentController', function(
  $scope, relatedSegments, relatedVideos
) {
  $scope.relatedSegments = [];
  $scope.relatedVideos = [];
  
  $scope.whenAssetAvailable(function(asset) {
    $scope.relatedSegments = relatedSegments.query({id: asset.videoUri});
    $scope.relatedVideos = relatedVideos.query({id: asset.videoUri});
  });
});

controllers.controller('TranscriptController', function($scope, transcript) {
  $scope.videoTranscript = [];
  
  $scope.whenAssetAvailable(function(asset) {
    $scope.videoTranscript = transcript.get({id: asset.videoUri});
  });
});

controllers.controller('KeyframesController', function($scope, keyframes) {
  $scope.videoKeyframes = [];
  
  $scope.whenAssetAvailable(function(asset) {
    $scope.videoKeyframes = keyframes.get({id: asset.videoUri});
  });
});


controllers.controller('FaceTracksController', function($scope, facetracks) {
  $scope.faceTracks = [];
  
  $scope.whenAssetAvailable(function(asset) {
    $scope.faceTracks = facetracks.get({id: asset.videoUri});
  });
});


controllers.controller('FeedController', 
  function($scope, $routeParams, newsItems)  
{
  
});

controllers.controller('BrowseController', 
  function($scope, $routeParams) 
{
  $scope.browseSubview = 'topics';
  $scope.topicType = $routeParams.topicType || 'categories';
  
  if ($routeParams.subview) {
    $scope.browseSubview = $routeParams.subview;
  }
});

controllers.controller('PopularVideosController',
  function($scope, popularVideos) 
{
  $scope.popularVideos = $scope.loadData(popularVideos.query);
});

controllers.controller('TopicsController',
  function($scope, interestingTopics, uiSettings) 
{
  $scope.hideNames = uiSettings.hideNamesInTopicsView;
  
  $scope.fetchTopics = function() {
    var q = interestingTopics.query;
    var args = { limit: 20 };
    if ($scope.topicType) {
      args.type = $scope.topicType;
    }
    $scope.interestingTopics = $scope.loadData(q, args);
    $('.page').scrollTop(0);
  };
  
  $scope.fetchTopics();
});

controllers.controller('HistoryController', 
  function($scope, loginService, user) 
{
  $scope.loggedIn = loginService.user != null;
  $scope.history = $scope.loadData(user.history);
});

controllers.controller('BookmarksController', 
  function($scope, loginService, user) 
{
  $scope.loggedIn = loginService.user != null;
  $scope.bookmarks = $scope.loadData(user.bookmarks);
});


controllers.controller('ProfileController', 
  function($scope, loginService, user) 
{
  $scope.profile = loginService.user;
  
  $scope.logout = function() {
    user.logout({}, function() {
      loginService.logout();
      $scope.profile = null;
    });
  };
});

controllers.controller('LoginController', 
  function($scope, $location, user, loginService)  
{
  // Entered username and password
  $scope.username = '';
  $scope.password = '';
  
  // Validation error, if any
  $scope.error = '';
  
  // Fill in username if we have a user logged in
  if (loginService.user) {
    $scope.username = loginService.user.username;
  }
  
  // Called when user clicks login
  $scope.login = function() {
    
    // Log the user in
    user.login({
      username: $scope.username,
      password: $scope.password
      
    }, function(loginDetails) {
      
      //console.log(loginDetails);
      
      // Login and redirect to home
      loginService.login(loginDetails);
      $location.path('/');
      
    }, function(error) {
      
      // Login error
      console.log(error);
      if (error.status == 403) {
        if (error.data) {
          $scope.error = error.data.detail || error.data.message || error.data;
        } else {
          $scope.error = "Invalid username or password"
        }
        
      } else {
        $scope.error = 'Login failed';
      }
      
    });
  };
});

controllers.controller('RegistrationController', 
  function($scope, $location, user, loginService)  
{
  // Registration details
  $scope.registrationDetails = {
    username: '',
    password1: '',
    password2: '',
    firstName: '',
    lastName: '',
    email: ''
  };
  
  // Validation error, if any
  $scope.error = '';
  
  // Validate user input. Sets $scope.error
  $scope.validate = function() {
    var form = $scope.registrationDetails;
    
    if (form.username.length < 3) {
      $scope.error = 'Username too short (min 3 characters)';
      return false;
    }
    
    if (form.password1.length < 5) {
      $scope.error = 'Password too short (min 5 characters)';
      return false;
    }
    
    if (form.password1 != form.password2) {
      $scope.error = 'Passwords do not match';
      return false;
    }
    
    if (form.email.length < 4) {
      $scope.error = 'Email address too short (min 4 characters)';
      return false;
    }
    
    $scope.error = '';
    return true;
  };
  
  // Called when user clicks register
  $scope.register = function() {
    
    // Validate form data
    if (!$scope.validate()) {
      return;
    }
    
    // Copy profile and base 64 encode password
    var details = {
      username: $scope.registrationDetails.username,
      password: $scope.registrationDetails.password1,
      firstName: $scope.registrationDetails.firstName,
      lastName: $scope.registrationDetails.lastName,
      email: $scope.registrationDetails.email
    };
    
    // Register the user
    user.register(details, function(loginDetails) {
      
      //console.log(loginDetails);
      
      // Login and redirect
      loginService.login(loginDetails);
      $location.path('/');
    
    }, function(error) {
      
      // Registration error
      console.log(error);
      if (error.status == 400) {
        
        if (error.data) {
          $scope.error = error.data.detail || error.data.message || error.data;
        } else {
          $scope.error = "Invalid registration details"
        }
      } else {
        $scope.error = 'Registration failed';
      }
      
    });
  };
});

controllers.controller('AboutController', 
  function($scope, serviceInfo, versionInfo, datasetInfo) 
{
  $scope.serviceInfo = serviceInfo.query();
  $scope.versionInfo = versionInfo.get();
  $scope.datasetInfo = datasetInfo.get();
  $scope.logWhenAvailable($scope.serviceInfo);
  $scope.logWhenAvailable($scope.versionInfo);
  $scope.logWhenAvailable($scope.datasetInfo);
});

controllers.controller('VideoController', ['$scope', 'uiSettings', 
  function($scope, uiSettings) 
{
  $scope.currentTime = 0;
  $scope.totalTime = 0;
  $scope.state = null;
  $scope.volume = 1;
  $scope.isCompleted = false;
  $scope.api = null;
  $scope.videoElement = null;
  $scope.segmentStartTime = 0;
  $scope.segmentDuration = null;
  $scope.segmentEndTime = null;
  
  $scope.$on('$destroy', function(event) {
    
    // Stop playback
    $scope.api.pause();
    
    //
    // work around for problem with HTML5 video in single page applications
    // we explicitly unload the video by setting source to blank and calling
    // load. This ensures that video is properly reloaded when a new video
    // element is added.
    //
    // See bug: https://code.google.com/p/chromium/issues/detail?id=234779
    //
    
    $scope.api.videoElement.attr('src', '');
    $($scope.api.videoElement, 'source').remove();
    $scope.videoElement.load();
  });
  
  $scope.onPlayerReady = function(api) {
    $scope.api = api;
    $scope.videoElement = $scope.api.videoElement[0];

    $scope.whenAssetAvailable(function(asset) {
      $scope.segmentStartTime = asset.startTime / 1000.0;
      $scope.segmentEndTime = asset.endTime / 1000.0;
      $scope.segmentDuration = asset.segmentDuration / 1000.0;
      
      $scope.videoElement.oncanplay = function() {
        $scope.seekSegmentStart();
        $scope.videoElement.oncanplay = null;
      };
    });
  };

  $scope.onCompleteVideo = function() {
    $scope.isCompleted = true;
  };

  $scope.onUpdateState = function(state) {
    $scope.state = state;
  };

  $scope.onUpdateTime = function(currentTime, totalTime) {
    $scope.currentTime = currentTime;
    $scope.totalTime = totalTime;
  };

  $scope.onUpdateVolume = function(newVol) {
    $scope.volume = newVol;
  };

  $scope.onUpdateSize = function(width, height) {
    $scope.config.width = width;
    $scope.config.height = height;
  };
  
  $scope.seek = function(time) {
    $scope.api.seekTime(time);
  };
  
  $scope.seekSegmentStart = function() {
    //
    // This is a bit of a hack. Sometimes the segments are very short and 
    // the start time can miss the part of the video that actually contains
    // the keyframe corresponding to the clip. So we jump back a few seconds
    // before the clip start time to make sure we include the keyframe
    //
    var startTime = Math.max(0, 
      $scope.segmentStartTime - uiSettings.seekToSecsBeforeSegment);
    $scope.api.seekTime(startTime);
  };
  
  $scope.seekSegmentEnd = function() {
    $scope.api.seekTime($scope.segmentEndTime);
  };
  
  $scope.setPlaybackRate = function(speed) {
    $scope.videoElement.playbackRate = speed;
  };
  
  $scope.increasePlaybackRate = function() {
    var newRate = Math.min($scope.videoElement.playbackRate + 0.5, 5);
    $scope.videoElement.playbackRate = newRate;
  };
  
  $scope.decreasePlaybackRate = function() {
    var newRate = Math.max($scope.videoElement.playbackRate - 0.5, 0.5);
    $scope.videoElement.playbackRate = newRate;
  };
  
  $scope.config = {
    width: 640,
    height: 410,
    autoHide: false,
    autoHideTime: 3000,
    autoPlay: false,
    responsive: false,
    stretch: { label: 'fit', value: 'fit' },
    theme: {
      url: "css/videogular.css",
      playIcon: "&#xe000;",
      pauseIcon: "&#xe001;",
      volumeLevel3Icon: "&#xe002;",
      volumeLevel2Icon: "&#xe003;",
      volumeLevel1Icon: "&#xe004;",
      volumeLevel0Icon: "&#xe005;",
      muteIcon: "&#xe006;",
      enterFullScreenIcon: "&#xe007;",
      exitFullScreenIcon: "&#xe008;"
    }
  };
}]);
