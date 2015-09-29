//
// knockout.js binding handlers
//
// Author: Kevin McGuinness <kevin.mcguinness@dcu.ie>
//

/*
 * List selector binding
 */  
ko.bindingHandlers.listSelector = {
  init: function(element, valueAccessor, allBindingsAccessor, viewModel, bindingContext) {
    var allBindings = allBindingsAccessor();
    
    
    // Create list items for each option 
    var options = allBindings.optionValues;
    var optionText = allBindings.optionText || 'description';
    var optionTooltip = allBindings.optionTooltip || 'tooltip';
    
    for (var i = 0; i < options.length; i++) {
      
      var text;
      if (typeof(optionText) == 'function') {
        text = optionText(options[i]);
      } else {
        text = options[i][optionText];
      }
      
      var tooltip;
      if (typeof(optionTooltip) == 'function') {
        tooltip = optionTooltip(options[i]);
      } else {
        tooltip = options[i][optionTooltip];
      }
      
      var html = '<li><a href="#" title="' + tooltip + '">' + text + '</a></li>';
      var li = $(html).appendTo(element);
      $('a', li).data('value', options[i]);
    }
    
    // On click, change the selected value
    $('a', element).click(function() {
      var option = $(this).data('value');
      var observable = valueAccessor();
      observable(option);
    });
  },
  
  update: function(element, valueAccessor, allBindingsAccessor, viewModel, bindingContext) {
    var observable = valueAccessor();
    $('a', element).each(function(index) {
      var selection = observable();
      var value = $(this).data('value');
      $(this).toggleClass('selected', value == selection);
    });
  }
};

/*
 * A button that toggles the visibility of a child element via sliding it 
 * in or out
 */
ko.bindingHandlers.slideToggle = {
  init: function(element, valueAccessor, allBindingsAccessor) {
    var allBindings = allBindingsAccessor();
    var selector = valueAccessor();
    var collapsibleContent = $(selector);
    
    // Hide collabsable if not initially set to visible
    var initiallyVisible = allBindings.initiallyVisible || false;
    if (!initiallyVisible) {
      collapsibleContent.hide();
    }
    
    var toggleSpeed = allBindings.toggleSpeed || 'fast';
    $(element).click(function() {
      collapsibleContent.slideToggle(toggleSpeed);
    });
  }
};

/*
 * Execute after 'enter' is pressed. Useful for triggering actions
 * when the user presses return in an input field.
 */
ko.bindingHandlers.executeOnEnter = {
  init: function (element, valueAccessor, allBindingsAccessor, viewModel) {
    var allBindings = allBindingsAccessor();
    $(element).keypress(function (event) {
      var keyCode = (event.which ? event.which : event.keyCode);
      if (keyCode === 13) {
        allBindings.executeOnEnter.call(viewModel);
        return false;
      }
      return true;
    });
  }
};

/*
 * Binding handler for a canvas element
 *
 * HTML:
 * <canvas id="canvas" width="100" height="100" 
 *         data-bind="draw: myObject using: model.drawObject"></canvas>
 *
 * JS:
 * var viewModel = {
 *    myObject: ko.observable(new MyObject()),
 *    drawObject: function(context, value) {
 *       context.save();
 *       //...
 *       context.restore();
 *    }
 * };
 *
 */ 
ko.bindingHandlers.draw = {
  init: function(element, valueAccessor, allBindingsAccessor, viewModel) {
  },
  
  update: function(element, valueAccessor, allBindingsAccessor, viewModel) {
    var allBindings = allBindingsAccessor();
    var context = element.getContext("2d");
    var itemToDraw = ko.utils.unwrapObservable(valueAccessor());
    var redrawCallback = ko.utils.unwrapObservable(allBindings.using);
    redrawCallback.call(viewModel, element, context, itemToDraw);
  }
};

ko.bindingHandlers.video = (function() {
  
  var createPlayer = function(asset, allBindingsAccessor) {
    var vid = ko.utils.unwrapObservable(asset.video);
    var segment = ko.utils.unwrapObservable(asset.segmentOrVideo);
    var keyframe = ko.utils.unwrapObservable(asset.keyframe);
    return new video.Player({
      sources: ko.utils.unwrapObservable(vid.sources),
      poster: ko.utils.unwrapObservable(keyframe.imageUrl),
      startTime: ko.utils.unwrapObservable(segment.startTimeMillis / 1000.0),
      width: allBindingsAccessor().width || 320,
      height: allBindingsAccessor().height || 240,
      autoplay: allBindingsAccessor().autoplay,
      controls: allBindingsAccessor().controls,
      duration: allBindingsAccessor().duration,
      owner: allBindingsAccessor().owner
    });
  };
  
  var removePlayer = function(element) {
    var player = $(element).data('player');
    if (player) {
      player.remove();
    }
    $(element).data('player', null);
  };
  
  var update = function(element, valueAccessor, allBindingsAccessor, viewModel) {
    var asset = ko.utils.unwrapObservable(valueAccessor());
    removePlayer(element);
    if (asset) {
      var player = createPlayer(asset, allBindingsAccessor);
      player.appendTo(element);
      $(element).data('player', player);
    } 
  };
  
  return {update: update};
})();

/**
 * Binding handler for twitter bootstrap typeahead
 */
ko.bindingHandlers.typeahead = {
  init: function(element, valueAccessor) {
    var source = ko.utils.unwrapObservable(valueAccessor());
    $(element).typeahead({ source: source });
  },
  
  update: function(element, valueAccessor) {
    var source = ko.utils.unwrapObservable(valueAccessor());
    $(element).data('typeahead').source = source;
  }
};

ko.bindingHandlers.draggable = {
  init: function(element, valueAccessor, allBindingsAccessor) {
    $(element).draggable({
      containment: 'window',
      scroll: false,
      handle: allBindingsAccessor().handle,
      cancel: allBindingsAccessor().cancel
    });
  }
};

ko.bindingHandlers.rangeSlider = {
  init: function(element, valueAccessor, allBindingsAccessor, viewModel) {
    var allBindings = allBindingsAccessor();
    var options = allBindingsAccessor().sliderOptions || {};
    var low = ko.utils.unwrapObservable(allBindings.low);
    var high = ko.utils.unwrapObservable(allBindings.high);
    var min = ko.utils.unwrapObservable(allBindings.min);
    var max = ko.utils.unwrapObservable(allBindings.max);
    
    options.range = true;
    options.values = [low, high];
    
    if (typeof(min) !== "undefined") {
      options.min = min;
    }
    
    if (typeof(max) !== "undefined") {
      options.max = max;
    }
    
    $(element).slider(options);
    
    var sliderChanged = function(event, ui) {
      var low = allBindingsAccessor().low;
      var high = allBindingsAccessor().high;
      low(ui.values[0]);
      high(ui.values[1]);
    };
    
    //ko.utils.registerEventHandler(element, "slidechange", sliderChanged);
    ko.utils.registerEventHandler(element, "slide", sliderChanged);
    
    ko.utils.domNodeDisposal.addDisposeCallback(element, function() {
      $(element).slider("destroy");
    });
  },
  
  update: function (element, valueAccessor, allBindingsAccessor) {
    var allBindings = allBindingsAccessor();
    var low = ko.utils.unwrapObservable(allBindings.low);
    var high = ko.utils.unwrapObservable(allBindings.high);
    var min = ko.utils.unwrapObservable(allBindings.min);
    var max = ko.utils.unwrapObservable(allBindings.max);

    $(element).slider("values", 0, low);
    $(element).slider("values", 1, high);
    
    if (typeof(min) !== "undefined") {
      $(element).slider("option", "min", min);
    }
    
    if (typeof(max) !== "undefined") {
      $(element).slider("option", "max", max);
    }
  }
};

ko.bindingHandlers.seekVideo = (function() {
  var init = function(video, valueAccessor, allBindingsAccessor) {
    //console.log('seekVideo init');
    
    var onCanPlay = function() {
      //console.log('onCanPlay');
      if (ko.utils.unwrapObservable(allBindingsAccessor().autoplay)) {
        video.play();
      }
    };
    
    var onMetadataLoaded = function() {
      var startTime = ko.utils.unwrapObservable(valueAccessor());
      //console.log('onMetadataLoaded', startTime);
      if (video.currentTime < startTime) {
        video.currentTime = startTime;
      }
    };
    
    video.addEventListener('canplay', onCanPlay, false);
    video.addEventListener('loadedmetadata', onMetadataLoaded, false);
    
    ko.utils.domNodeDisposal.addDisposeCallback(video, function() {
      //console.log('domNodeDispose');
      video.removeEventListener('canplay', onCanPlay, false);
      video.removeEventListener('loadedmetadata', onMetadataLoaded, false);
    });
  };
  
  return {init: init}
})();

ko.bindingHandlers.videoLoadingIndicator = {
  init: function(video, valueAccessor, allBindingsAccessor) {
    var selector = valueAccessor();
    $(selector).show();
    
    var onCanPlay = function() {
      $(selector).hide();
    };
    
    video.addEventListener('canplay', onCanPlay, false);
  }
};

ko.bindingHandlers.onChange = {
  init: function(elem, valueAccessor) {
    elem.addEventListener('change', function() {
      var fn = valueAccessor();
      fn(elem);
    }, false);
  }
};

/*
 * Search box down menu binding (app specific)
 */
ko.bindingHandlers.searchBoxDropDown = {
  init: function(element, valueAccessor, allBindingsAccessor) {
    var selector = valueAccessor();
    var allBindings = allBindingsAccessor();
    var side = allBindings.side || '';
    var hideMenuOnClick = allBindings.hideMenuOnClick || false;
    var clickInside = false;
    
    var visible = function() {
      return $(selector).is(':visible');
    };
    
    var hide = function() {
      $(selector).slideUp('fast', function() {
        $(this).parents('.searchbox').removeClass(
          'expanded expanded-left expanded-right');
      });
    };
    
    var show = function() {
      
      // hide any visible drop down's first
      $('.menu:visible').each(function() {
        $(this).slideUp('fast');
      });
    
      var menu = $(selector);
      menu.parents('.searchbox').addClass('expanded' + side);
      menu.slideDown('fast');
    };
    
    var toggle = function() {
      if (visible()) {
        hide();
      } else {
        show();
      }
    };
    
    $(element).click(function() {
      toggle();
      return false;
    });
    
    $(selector).click(function(event) {
      clickInside = true;
    });
    
    // Hide for click outside
    $(document).click(function(e) {
      if (visible()) {
        if (hideMenuOnClick) {
          hide();
        } else {
          if (!clickInside) {
            hide();
          } else {
            clickInside = false;
          }
        }
      }
      return true;
    });
  }
};
