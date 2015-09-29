//
// Ajax API endpoint access
//
// Author: Kevin McGuinness <kevin.mcguinness@dcu.ie>
//
var ajax = (function() {
  var module = {};
  
  module.Endpoint = function(root, prefix) {
    var self = this;
    self.root = root;
    self.prefix = prefix;
    self.xhrPool = [];
    
    var beforeSend = function(jqXHR) {
      // Add query to active pool
      self.xhrPool.push(jqXHR);
      self.root.statusText("Loading ...");
    };
    
    var beforeSendBackground = function(jqXHR) {
      // Add query to active pool
      self.xhrPool.push(jqXHR);
    };
    
    var complete = function(jqXHR) {
      self.root.statusText("");
      
      // Remove query from active pool
      var index = self.xhrPool.indexOf(jqXHR);
      if (index >= 0) {
        self.xhrPool.splice(index, 1);
      }
    };
    
    var error = function(xhr, textStatus, errorThrown) {
      console.log("AJAX error:", textStatus, errorThrown);
      self.root.loading(false);
    };
    
    self.absolute_url = function(url) {
      if (self.prefix) {
        if (url.length > 0 && url[0] == '/') {
          url = url.substr(1);
        }
        
        if (self.prefix[self.prefix.length-1] == '/') {
          return self.prefix + url;
        } else {
          return self.prefix + '/' + url;
        }
      }
      return url;
    };
    
    // Abort all pending requests
    self.abortAllPendingRequests = function() {
      $(self.xhrPool).each(function(idx, jqXHR) {
        jqXHR.abort();
      });
      self.xhrPool.length = 0
    };
    
    self.get = function(url, success, background) {
      var absolute_url = self.absolute_url(url);
      $.ajax({
        url: absolute_url,
        type: 'GET',
        dataType: 'json',
        xhrFields: { withCredentials: true },
        success: success,
        beforeSend: background ? beforeSendBackground : beforeSend,
        complete: complete,
        error: error
      });
    };
    
    self.post = function(url, data, success, background) {
      var absolute_url = self.absolute_url(url);
      $.ajax({
        url: absolute_url,
        type: 'POST',
        contentType: 'application/json; charset=utf-8',
        data: ko.toJSON(data),
        dataType: 'json',
        xhrFields: { withCredentials: true },
        success: success,
        beforeSend: background ? beforeSendBackground : beforeSend,
        complete: complete,
        error: error
      });
    };
  };
  
  return module;
})();
