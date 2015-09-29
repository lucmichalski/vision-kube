'use strict';
// (c) Copyright 2015 Kevin McGuinness. All Rights Reserved. 

/*
 * Application root module
 */
var app = angular.module('axeshome', [
  'ngRoute',
  'ngAnimate',
  'ngCookies',
  'ngSanitize',
  'ngDialog',
  'nsPopover',
  'axeshome.filters',
  'axeshome.services',
  'axeshome.directives',
  'axeshome.controllers',
  'axeshome.settings',
  "com.2fdevs.videogular",
  "com.2fdevs.videogular.plugins.controls",
  "com.2fdevs.videogular.plugins.overlayplay",
  "com.2fdevs.videogular.plugins.buffering",
  "com.2fdevs.videogular.plugins.poster"
]);

/*
 * Configure application
 */
app.config(['$routeProvider', '$httpProvider',
  function($routeProvider, $httpProvider) 
{
  
  // Setup CSRF stuff
  $httpProvider.defaults.xsrfHeaderName = 'X-CSRFToken';
  $httpProvider.defaults.xsrfCookieName = 'csrftoken';
  $httpProvider.defaults.withCredentials = true;
  
  $httpProvider.interceptors.push('ajaxAbortInterceptor');

  // Setup application routes
  $routeProvider
    .when('/', {
      templateUrl   : 'views/home.html',
      controller    : 'HomeController'
    })
    .when('/advanced-search', {
      templateUrl   : 'views/search.html',
      controller    : 'AdvancedQueryController'
    })
    .when('/search/:query*', {
      templateUrl   : 'views/results.html',
      controller    : 'SearchController'
    })
    .when('/image-search/:imageUrl*', {
      templateUrl   : 'views/results.html',
      controller    : 'ImageSearchController'
    })
    .when('/advanced-search/:query*', {
      templateUrl   : 'views/results.html',
      controller    : 'AdvancedSearchController'
    })
    .when('/asset/:assetId*', {
      templateUrl   : 'views/asset.html',
      controller    : 'AssetController'
    })
    .when('/news', {
      templateUrl   : 'views/news.html',
      controller    : 'NewsController'
    })
    .when('/browse', {
      templateUrl   : 'views/browse.html',
      controller    : 'BrowseController'
    })
    .when('/browse/:subview', {
      templateUrl   : 'views/browse.html',
      controller    : 'BrowseController'
    })
    .when('/browse/topics/:topicType', {
      templateUrl   : 'views/browse.html',
      controller    : 'BrowseController'
    })
    .when('/history', {
      templateUrl   : 'views/history.html',
      controller    : 'HistoryController'
    })
    .when('/bookmarks', {
      templateUrl   : 'views/bookmarks.html',
      controller    : 'BookmarksController'
    })
    .when('/profile', {
      templateUrl   : 'views/profile.html',
      controller    : 'ProfileController'
    })
    .when('/about', {
      templateUrl   : 'views/about.html',
      controller    : 'AboutController'
    })
    .when('/feed/:feedId*', {
      templateUrl   : 'views/feed.html',
      controller    : 'FeedController'
    })
    .when('/news-item/:feedId*', {
      templateUrl   : 'views/newsitem-results.html',
      controller    : 'NewsItemController'
    })
    .when('/news-source/:sourceId*', {
      templateUrl   : 'views/results.html',
      controller    : 'NewsSourceController'
    })
    .when('/login', {
      templateUrl   : 'views/login.html',
      controller    : 'LoginController'
    })
    .when('/register', {
      templateUrl   : 'views/register.html',
      controller    : 'RegistrationController'
    })
    .otherwise({
      redirectTo: '/'
    });
}]);


app.run(function($rootScope, pendingRequests) {
  
  /*
   * Intercept route changes and abort pending AJAX requests
   */
  $rootScope.$on('$routeChangeStart', function(event, next, current) {
    pendingRequests.abortAll();
  });
  
  /*
   * Force scroll to top when page changes
   */
  $rootScope.$on("$routeChangeSuccess", function() {
    $('.page').scrollTop(0);
  });
  
});
