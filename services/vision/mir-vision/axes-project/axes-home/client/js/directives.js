'use strict';
// (c) Copyright 2015 Kevin McGuinness. All Rights Reserved. 

var directives =  angular.module('axeshome.directives', []);

directives.directive('twitter', function($timeout) {
  return {
    link: function(scope, element, attr) {
      $timeout(function() {
        twttr.widgets.createShareButton(
          attr.url,
          element[0],
          function() {}, {
            count: attr.count,
            text: attr.text,
            via: attr.via,
            size: attr.size
          }
        );
      });
    }
  }
});
