'use strict';
// (c) Copyright 2015 Kevin McGuinness. All Rights Reserved. 

var settings = angular.module('axeshome.settings', []);

/*
 * API settings
 */
settings.constant('apiConfig', {
  prefix: 'api/',
  suffix: ''
});

/*
 * UI settings
 */
settings.constant('uiSettings', {
  titleText: 'Explore Archives',
  seekToSecsBeforeSegment: 5.0,
  hideNamesInTopicsView: false,
  hideSummaryInNewsView: true
});
