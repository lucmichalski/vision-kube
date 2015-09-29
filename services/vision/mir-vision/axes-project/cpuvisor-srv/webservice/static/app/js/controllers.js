'use strict';

/* Controllers */

var cpuVisorControllers = angular.module('cpuVisorControllers', ['cpuVisorDirectives']);

cpuVisorControllers.controller('LandingCtrl', ['$scope', '$location',
  function ($scope, $location) {

    $scope.query_text = '';

    $scope.search = function(query_text) {
      if (query_text) {
        $location.path('/results/' + encodeURIComponent(query_text));
      }
    };

  }]);

cpuVisorControllers.controller('RankingCtrl', ['$scope', '$routeParams', '$location', '$http', '$timeout', '$modal', 'socketFactory',
  'StartQuery', 'AddTrs', 'TrainModel', 'Rank', 'Ranking', 'FreeQuery',
  function ($scope, $routeParams, $location, $http, $timeout, $modal, socketFactory, StartQuery, AddTrs, TrainModel,
            Rank, Ranking, FreeQuery) {

    $scope.StateEnum = {
      QS_DATACOLL: 0,
      QS_DATACOLL_COMPLETE: 1,
      QS_TRAINING: 2,
      QS_TRAINED: 3,
      QS_RANKING: 4,
      QS_RANKED: 5,
      QS_ERROR: 100
    };

    $scope.startQuery = function() {
      StartQuery.execute({}, function(queryIfo) {
        $scope.handleErrorMaybe(queryIfo);

        $scope.query_id = queryIfo.query_id;
        AddTrs.execute({qid: $scope.query_id, q: $scope.query}, $scope.handleErrorMaybe);
      });
    };

    $scope.startTraining = function() {
      TrainModel.execute({qid: $scope.query_id}, $scope.handleErrorMaybe);
    };

    $scope.startRanking = function() {
      Rank.execute({qid: $scope.query_id}, $scope.handleErrorMaybe);
    };

    // Start -----

    $scope.query = decodeURIComponent($routeParams.queryText);
    $scope.query_editable = $scope.query;
    $scope.query_id = null;
    $scope.last_processed_image = null;
    $scope.ranking = {'rlist': []};
    $scope.state = $scope.StateEnum.QS_DATACOLL;

    var io_namespace = '/api/query/notifications';
    console.log('Creating socket with namespace: ' + io_namespace);
    $scope.notification_socket = socketFactory({
      ioSocket: io.connect(io_namespace)
    });

    $scope.notification_socket.on('notification', function(notify_data) {
      console.log('Received notification: ' + notify_data);
      if (notify_data.id === $scope.query_id) {
        switch (notify_data.type) {
          case 'NTFY_STATE_CHANGE':
            $scope.state = $scope.StateEnum[notify_data.data];

            console.log('State changed to: ' + $scope.state);
            switch ($scope.state) {
              case $scope.StateEnum.QS_DATACOLL_COMPLETE:
                $scope.startTraining();
                break;
              case $scope.StateEnum.QS_TRAINED:
                $scope.startRanking();
                break;
              case $scope.StateEnum.QS_RANKED:
                Ranking.get({qid: $scope.query_id, page: 1}, function(ranking_obj) {
                  //console.log(ranking);
                  $scope.ranking = ranking_obj.ranking;
                  console.log($scope.ranking);
                  console.log('Ranking is now of length: ' + $scope.ranking.rlist.length);
                });
                break;
            }
            break;
          case 'NTFY_IMAGE_PROCESSED':
            console.log('Image processed: ' + notify_data.data);
            $scope.last_processed_image = notify_data.data;
            break;
          case 'NTFY_ALL_IMAGES_PROCESSED':
            console.log('All images processed!');
            break;
          case 'NTFY_ERROR':
            console.log('Caught error: ' + notify_data.data);
            $scope.handleError(notify_data.data);
        }
      }
    });

    $scope.startQuery();

    // timeout for image addition
    $timeout(function() {
      if ($scope.state <= $scope.StateEnum.QS_DATACOLL_COMPLETE) {
        $scope.startTraining()
      }
    }, 20000.0);

    $scope.getImgSrc = function(ritem) {
      return 'dsetimages/' + ritem.path;
    };

    $scope.loadMore = function() {
      var next_page = $scope.ranking.page + 1;
      console.log('Next page is: ' + next_page + ' out of: ' + $scope.ranking.page_count + ' pages');
      if (next_page <= $scope.ranking.page_count) {
        console.log('Getting the updated ranking...');
        Ranking.get({qid: $scope.query_id, page: next_page}, function(ranking_obj) {
          if (!ranking_obj.success) $scope.handleError("Ranking failed" + rankingobj.err_msg);
          $scope.ranking.rlist = $scope.ranking.rlist.concat(ranking_obj.ranking.rlist);
          $scope.ranking.page = ranking_obj.ranking.page;

          console.log($scope.ranking);
          console.log('Ranking is now of length: ' + $scope.ranking.rlist.length);
        });
      }
    };

    $scope.goHome = function() {
      $location.path('/');
    };

    $scope.handleError = function(err_msg) {
      $scope.state = $scope.StateEnum.QS_ERROR;

      console.log('Handling error with err_msg: ' + err_msg);
      $modal({
        title: 'Error',
        content: err_msg,
        show: true,
        animation: 'am-fade-and-scale',
        backdropAnimation: 'am-fade',
        placement: 'center',
        container: 'body',
        backdrop: 'static',
        prefixEvent: 'modal',
        scope: $scope
      });
    };

    $scope.handleErrorMaybe = function(err_obj) {
      if (!err_obj.success) $scope.handleError(err_obj.err_msg);
    };

    $scope.$on('modal.hide',function() {
      console.log('Going home!');
      $scope.$apply(function() {
        $location.path('/');
      });
    });

    $scope.$on('$locationChangeStart', function(event, newUrl, oldUrl) {
      // free query when leaving the page
      if ($scope.query_id !== null) {
        FreeQuery.execute({qid: $scope.query_id});
      }
    });

    $scope.search = function(query_text) {
      if (query_text) {
        $location.path('/results/' + encodeURIComponent(query_text));
      }
    };

  }]);
