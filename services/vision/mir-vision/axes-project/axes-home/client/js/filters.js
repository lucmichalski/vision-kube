'use strict';
// (c) Copyright 2015 Kevin McGuinness. All Rights Reserved. 

var filters =  angular.module('axeshome.filters', ['axeshome.services']);

/*
 * Filter to convert duration in h:mm:ss
 */
filters.filter('duration', function() {
  
  return function(seconds) {
    
    // Missing values get blank
    if (angular.isUndefined(seconds) || seconds == null) {
      return '';
    }
    
    // remaining unaccounted seconds
    var secs = seconds;
    
    // compute hours
    var hours = Math.floor(secs / 3600.0);
    
    secs -= hours * 3600;
    
    // compute minutes
    var minutes = Math.floor(secs / 60.0);
    
    secs = Math.floor(secs - minutes * 60);
    
    // create string
    var text = '';
    if (hours > 0) {
      text += hours + ':'
    }
    
    if (minutes < 10) {
      text += '0';
    }
    text += minutes + ':'
    
    if (secs < 10) {
      text += '0';
    }
    text += secs;
  
    return text;
  };
});

filters.filter('groupResults', function(utilities) {
  
  return function(results, criteria) {
    if (criteria == 'Videos') {
      return utilities.getUniqueVideos(results);
    }
    return results;
  };
  
});

filters.filter('avatar', function() {
  
  return function(url) {
    if (!url) {
      return 'img/avatar_default_light.png';
    }
    return url;
  };
});

filters.filter('timeago', function() {
  return function(date) {
    return jQuery.timeago(new Date(date));
  };
});
