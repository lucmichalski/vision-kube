//
// Javascript entry point and sammy.js client side routing 
//
// Author: Kevin McGuinness <kevin.mcguinness@dcu.ie>
//
$(function() {
  
  var runApplication = function() {
    var viewModel = new view.Model();
    var router = createClientSideRouter(viewModel);
    router.run('#/');
    ko.applyBindings(viewModel);
  };
  
  var createClientSideRouter = function(view) {
    var router = Sammy(function() {
      var server = view.server;
    
      this.get('#/logout', function() {
        server.abortAllPendingRequests();
        server.get('/api/logout/', function(data) {
          window.location = data.redirect;
        });
      });
    
      this.get('#/user', function() {
        $('body').scrollTop(0);
        server.abortAllPendingRequests();
        server.get('/api/collections/list/', function(data) {
          ko.mapping.fromJS(data, {}, view.collections);
        });
        view.userView.currentView(view.userView.collectionsView);
        view.currentView(view.userView);
      });
    
      this.get('#/user/collections', function() {
        $('body').scrollTop(0);
        server.abortAllPendingRequests();
        server.get('/api/collections/list/', function(data) {
          ko.mapping.fromJS(data, {}, view.collections);
        });
        view.userView.currentView(view.userView.collectionsView);
        view.currentView(view.userView);
      });
    
      this.get('#/user/favorites', function() {
        $('body').scrollTop(0);
        server.abortAllPendingRequests();
        server.get('/api/favorites/', function(data) {
          view.userView.favoritesView.favorites(data);
        });
        view.userView.currentView(view.userView.favoritesView);
        view.currentView(view.userView);
      });
    
      this.get('#/user/collections/:collectionId', function() {
        var collectionId = this.params.collectionId;
        $('body').scrollTop(0);
        server.abortAllPendingRequests();
        server.get('/api/collections/get/' + collectionId, function(data) {
          // TODO: Add individual collection view 
          console.log('collection', data);
        });
        view.userView.currentView(view.userView.collectionsView);
        view.currentView(view.userView);
      });
    
      this.get('#/user/history', function() {
        $('body').scrollTop(0);
        server.abortAllPendingRequests();
        server.get('/api/searches/recent/', function(data) {
          view.history(data);
        });
        view.userView.currentView(view.userView.historyView);
        view.currentView(view.userView);
      });
    
      this.get('#/user/recent', function() {
        $('body').scrollTop(0);
        server.abortAllPendingRequests();
        server.get("/api/videos/recent/", function(data) {
          view.recent(data);
        });
        view.userView.currentView(view.userView.recentView);
        view.currentView(view.userView);
      });
    
      this.get('#/user/notes', function() {
        $('body').scrollTop(0);
        server.abortAllPendingRequests();
        server.get('/api/notes/list/user/', function(data) {
          view.notes(data);
        });
        view.userView.currentView(view.userView.notesView);
        view.currentView(view.userView);
      });
      
      this.get('#/user/jobs', function() {
        $('body').scrollTop(0);
        server.abortAllPendingRequests();
      	server.get('/api/cutter/jobs/', function(data) {
          view.jobs(data);
        });
      	view.userView.currentView(view.userView.jobsView);
        view.currentView(view.userView);
      });
    
      this.get('#/user/preferences', function() {
        $('body').scrollTop(0);
        server.abortAllPendingRequests();
        server.get('/api/preferences/', function(preferences) {
          ko.mapping.fromJS(preferences, {}, view.preferences);
        });
        view.userView.currentView(view.userView.preferencesView);
        view.currentView(view.userView);
      });
      
      this.get('#/most/:type', function() {
        var type = this.params.type;
        $('body').scrollTop(0);
        server.abortAllPendingRequests();
        view.popularView.type(type);
        view.popularView.results([]);
        server.get('/api/most/' + type, function(results) {
          console.log('popular', results);
          ko.mapping.fromJS(results, {}, view.popularView.results);
        });
        view.currentView(view.popularView);
      });
      
      this.get('#/advanced-search', function() {
        $('body').scrollTop(0);
        
        server.abortAllPendingRequests();
        if (view.hasAdvancedQuery()) {
          view.advancedQueryView.query(view.query());
        }
        view.currentView(view.advancedQueryView);
      });
    
      this.get(/\#\/asset\/(.*)/, function() {
        var assetId = this.params.splat;
        $('body').scrollTop(0);
        
        server.abortAllPendingRequests();
        server.get("/api/asset/" + assetId, function(data) { 
          console.log('asset', data);
        
          // Create asset
          var asset = new model.Asset(data);
          view.assetView.relatedVideos(new model.ResultSet());
          view.assetView.notes([]);
          view.assetView.asset(asset);  
          view.assetView.faceTracks([]);
          var videoUri = asset.video.uri;
          
          server.get('/api/preferences/', function(preferences) {
            ko.mapping.fromJS(preferences, {}, view.preferences);
          });
        
          // Need list of collections to render this view
          server.get('/api/collections/list/', function(collections) {
            ko.mapping.fromJS(collections, {}, view.collections);
          });
        
          // Get notes for the video
          server.get("/api/notes/list/video/" + videoUri, function(notes) {
            view.assetView.notes(notes);
          });
      
          // Get transcripts
          server.get("/api/transcript/" + videoUri, function(result) {
            view.assetView.transcript(result.transcript);
          });
        
          // Get related videos
          server.get('/api/related/videos/' + asset.uri, function(related) {
            view.assetView.relatedVideos(new model.ResultSet(related));
          });
          
          // Get related segments
          server.get('/api/related/segments/' + asset.uri, function(related) {
            view.assetView.relatedSegments(new model.ResultSet(related));
          });
          
          // Get face tracks
          /*server.get('/api/facetracks/' + videoUri, function(faces) {
            console.log('facetracks', faces.tracks);
            view.assetView.faceTracks(faces.tracks);
          });*/
        
          view.currentView(view.assetView);
        });
      });
      
      this.get(/\#\/cutter\/(.*)/, function() {
        var assetId = this.params.splat;
        $('body').scrollTop(0);
      
        // Remove previous data
        view.cutterView.reset();
      
        // Fetch new data
        server.get("/api/asset/" + assetId, function(data) { 
          
          // Fetch asset and switch to cutter view
          var asset = new model.Asset(data);
          view.cutterView.asset(asset);
          view.currentView(view.cutterView);
        });
      });
    
      this.get(/\#\/keyframes\/(.*)/, function() {
        var assetId = this.params.splat;
        $('body').scrollTop(0);
        
        // Abort any pending requests
        server.abortAllPendingRequests();
      
        // Remove previous data
        view.keyframeView.video(null);
        view.keyframeView.keyframes([]);
      
        // Fetch new data
        server.get("/api/keyframes/" + assetId, function(result) { 
          console.log(result);
        
          // Sort by start time
          result.keyframes.sort(function(k1, k2) {
            return k1.startTimeMillis - k2.startTimeMillis;
          });
        
          view.keyframeView.video(result.video);
          view.keyframeView.keyframes(result.keyframes);
        
          // Initialize photo gallery
          $("a[rel^='prettyPhoto']").prettyPhoto({
            deeplinking: false,
            show_title: false,
            social_tools: ''
          });
        });
      
        // Show view
        view.currentView(view.keyframeView);
      });
    
      this.get('#/search/:queryId', function() {
        var queryId = this.params.queryId;
        $('body').scrollTop(0);
        
        // Abort any pending requests
        server.abortAllPendingRequests();
        
        view.resultsView.clear();
      
        // Pull user preferences
        server.get('/api/preferences/', function(preferences) {
          ko.mapping.fromJS(preferences, {}, view.preferences);
          var prefs = view.preferences();
          if (!view.resultsView.viewType()) {
            view.resultsView.viewType(prefs.defaultResultView());
          }
          view.resultsView.displayType(prefs.defaultResultType());
          view.resultsView.displayCount(prefs.resultsPerPage());
        });
      
        // Pull list of collections to render this view
        server.get('/api/collections/list/', function(collections) {
          ko.mapping.fromJS(collections, {}, view.collections);
        });
      
        // Get the details of the query
        server.get('/api/query/get/' + queryId, function(query) {
          console.log('query', query);
          if (query.type != 'advanced') {
            // TODO: parenthesis after query needed?
            ko.mapping.fromJS(query, {}, view.query());
          } else {
            view.query(new model.AdvancedQuery(query.clauses));
          }
          
        });
      
        // Get query suggestions
        server.get('/api/suggest/' + queryId, function(suggestions) {
          console.log('suggestions', suggestions);
          view.queryView.relatedTerms(suggestions.entities);
        });
      
        // Perform the search
        server.get("/api/search/" + queryId, function(results) {
          console.log('results', results);
          view.resultSet(new model.ResultSet(results));
          
          // Reset the filters when new results arrive
          view.filtersView.resetFilters();
          view.loading(false);
        });
      
        view.currentView(view.resultsView);
        view.loading(true);
      });
    
      this.get('#/', function() {
        $('body').scrollTop(0);
        server.abortAllPendingRequests();
      
        // Fetch recent videos
        server.get("/api/videos/recent/", function(data) {
          view.recent(data);
        });
      
        // Fetch recent searches
        server.get('/api/searches/recent/', function(data) {
          view.history(data);
        });
      
        view.currentView(view.initialView);
      });  
    });
    
    return router;
  };
  
  runApplication();
});
