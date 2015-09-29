//
// Generic utility functions
//
// Author: Kevin McGuinness <kevin.mcguinness@dcu.ie>
//
var utils = (function() {
  var module = {};
  
  module.formatTime = function(millis, fixedWidth) {
    var durationSeconds = millis / 1000.0;
    var hrs = Math.floor(durationSeconds / 3600);
    var mins = Math.floor((durationSeconds - hrs * 3600) / 60);
    var secs = Math.floor(durationSeconds - hrs * 3600 - mins * 60);
    
    var fmt = function(num) {
      return num < 10 ? "0" + num : "" + num;
    };
    
    if (hrs > 0 || fixedWidth) {
      return hrs + ":" + fmt(mins) + ":" + fmt(secs);
    } else if (mins > 0) {
      return mins + ":" + fmt(secs);
    } else {
      return secs + " sec";
    }
  };
  
  module.pluralize = function(quantity, word) {
    if (quantity == 1) {
      return word;
    } else {
      return word + 's';
    }
  };
  
  module.get = function(obj, name, defaultValue) {
    var value = null;
    if (typeof(defaultValue) !== "undefined") {
      value = defaultValue;
    } 
    if (obj) {
      if (obj[name]) {
        value = obj[name];
      }
    }
    return value;
  };

  module.set = function(target, obj, spec) {
    $.each(spec, function(k, v) {
      target[k] = module.get(obj, k, v);
    });
  };
  
  var htmlEscapes = {
    '&': '&amp;',
    '<': '&lt;',
    '>': '&gt;',
    '"': '&quot;',
    "'": '&#x27;',
    '/': '&#x2F;'
  };

  var htmlEscaper = /[&<>"'\/]/g;

  module.escapeHTML = function(string) {
    return ('' + string).replace(htmlEscaper, function(match) {
      return htmlEscapes[match];
    });
  };
  
  module.escapeRegExp = function(str) {
    return str.replace(/[\-\[\]\/\{\}\(\)\*\+\?\.\\\^\$\|]/g, "\\$&");
  };
  
  module.removeSpecialCharacters = function(str) {
    return str.replace(/[\-\[\]\/\{\}\(\)\*\+\?\.\\\^\$\|]/g, "");
  };
  
  module.urljoin = function(base, path) {
    if (base[base.length-1] != '/') {
      base = base + '/';
    }
    if (path && path[0] == '/') {
      path = path.substr(1);
    }
    return (base + path);
  };
  
  module.textMatches = function(object, text) {
    var target;
    if (typeof(object) === 'string') {
      target = object;
    } else {
      target = object.join(' ');
    }
    target = target.toLowerCase();
    return target.indexOf(text.toLowerCase()) >= 0;
  };
  
  module.textMatchesAny = function(object, values) {
    for (var i  = 0; i < values.length; i++) {
      if (module.textMatches(object, values[i])) {
        return true;
      }
    }
    return false;
  };
  
  module.parseDate = function(dateStr) {
    var parts = dateStr.split('-');
    var dd = parseInt(parts[0]);
    // zero based months in Javascript -- fuckin idiotic.
    var mm = parseInt(parts[1])-1;
    var yyyy = parseInt(parts[2]);
    return new Date(yyyy, mm, dd);
  };
  
  module.objectGrep = function(object, predicate) {
    var results = {};
    for (var key in object) {
      var value = object[key];
      if (predicate(value, key)) {
        results[key] = value;
      }
    }
    return results;
  };
  
  module.enquote = function(text) {
    return '"' + text + '"';
  };
  
  module.dequote = function(text) {  
    var isQuoteChar = function(x) {
      return x == '"' || x == "'";
    };
    
    if (text.length > 0 && isQuoteChar(text[0])) {
      text = text.substr(1);
    }
    
    if (text.length > 0 && isQuoteChar(text[text.length-1])) {
      text = text.substr(0, text.length - 1)
    }
    
    return text;
  };
  
  module.isMultiWord = function(text) {
    return text.indexOf(" ") > 0;
  };
  
  module.tokenizeQueryString = function(text) {
    
    // Clean string
    text = module.removeSpecialCharacters(text.trim());
    
    var tokens = [];
    var re = /([^\s"']+)|(?:"([^"]*)")|(?:'([^']*)')/g;
    var match = re.exec(text);
    
    while (match) {
      
      // Find matching group
      var matchText = null;
      for (var i = 1; i < match.length; i++) {
        matchText = match[i];
        if (matchText) {
          break;
        }
      }
      
      // Save token
      if (matchText) {
        tokens.push(matchText);
      }
      
      match = re.exec(text);
    }
    
    return tokens;
  };
  
  return module;
})();
