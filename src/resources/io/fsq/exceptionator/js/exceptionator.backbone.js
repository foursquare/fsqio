if (typeof Exceptionator == 'undefined') { var Exceptionator = {}; }

Exceptionator.GraphSpans = {
  LAST_HOUR: 'hour',
  LAST_DAY: 'day',
  LAST_MONTH: 'month'
}

Exceptionator.ListTypes = {
  BUCKET_KEY: 'bucket_key',
  BUCKET_GROUP: 'bucket_group',
  HISTORY: 'history',
  SEARCH: 'search'
}

Exceptionator.formatDate = function(value) {
  "use strict";
  var d = new Date(value)
  if(d.clone().clearTime().equals(new Date().clearTime())) {
    return d.toString('h:mm:ss tt');
  } else {
    return d.toString('dddd, MMMM dd, yyyy h:mm:ss tt');
  }
}

Exceptionator.GraphSpan = Exceptionator.GraphSpans.LAST_HOUR;
Exceptionator.Limit = 10;
Exceptionator.MouseDown = 0;

// http://phrogz.net/css/distinct-colors.html
// H: 0 -> 272, 8
// S: 90 -> 30, 30
// V: 90 -> 40, 5
// Sort: H(D), S(D) V(D)  I: 5
Exceptionator.COLOR_LIST = [
  '#e51717',
  '#99590f',
  '#d8e6a1',
  '#0b6c73',
  '#a1aae6',
  '#bf4d4d',
  '#e5a117',
  '#a1e617',
  '#17a1e6',
  '#a55ce6',
  '#e6a1a1',
  '#e6cfa1',
  '#17e64e',
  '#6b8a99',
  '#79628c',
  '#8c300e',
  '#66540a',
  '#42a65d',
  '#0e518c',
  '#806359',
  '#e5d817',
  '#74a695',
  '#176ae6',
  '#e56a17',
  '#7d8059',
  '#17e6bc',
  '#0d2b80',
  '#e6bca1',
  '#738c0e',
  '#17d8e6',
  '#1732e6'
];


/*
 * Notice
 */
Exceptionator.Notice = Backbone.Model.extend({
  initialize: function() {
    if(this.get('d')) {
      this.set({d_fmt: Exceptionator.formatDate(this.get('d'))})
    }
    _.each(this.get('bkts'), function(bucket, name) {
      if (name in Exceptionator.Config.friendlyNames) {
        bucket['friendlyName'] = Exceptionator.Config.friendlyNames[name];
      } else {
        bucket['friendlyName'] = name;
      }
      bucket['nm_uri'] = encodeURIComponent(name);
      bucket['k_uri'] = encodeURIComponent(bucket.k);
    });
    _.each(this.get('bt'), function(backtrace, index) {
      this.get('bt')[index] = backtrace.split('\n');
    }, this);
  },

  histogram: function(histogramMap) {
    var fieldName;
    if (Exceptionator.GraphSpan == Exceptionator.GraphSpans.LAST_HOUR) {
      fieldName = 'h';
    }
    if (Exceptionator.GraphSpan == Exceptionator.GraphSpans.LAST_DAY) {
      fieldName = 'd';
    }
    if (Exceptionator.GraphSpan == Exceptionator.GraphSpans.LAST_MONTH) {
      fieldName = 'm';
    }
    return histogramMap[fieldName];
  }
},{

  /** Mutates endDate to mark the last histogram entry. */
  emptyHistogram: function(endDate) {
    endDate.setMilliseconds(0);
    endDate.setSeconds(0);

    var step;
    var nSteps;
    if (Exceptionator.GraphSpan == Exceptionator.GraphSpans.LAST_HOUR) {
      step = 60 * 1000;
      nSteps = 60;
    }
    if (Exceptionator.GraphSpan == Exceptionator.GraphSpans.LAST_DAY) {
      endDate.setMinutes(0);
      step = 60 * 60 * 1000;
      nSteps = 24;
    }
    if (Exceptionator.GraphSpan == Exceptionator.GraphSpans.LAST_MONTH) {
      endDate.setMinutes(0);
      endDate.setHours(0);
      step = 24 * 60 * 60 * 1000;
      nSteps = 30;
    }

    var stop = endDate.getTime();
    var start = stop - (step * nSteps);
    stop = stop + step;
    var histo = {};
    _.each(_.range(start, stop, step), function(timestamp) { histo[timestamp] = 0; });
    return histo;
  }
});


Exceptionator.NoticeView = Backbone.View.extend({
  tagName: "div",
  className: "exc exc_hidden",

  render: function() {
    this.$el.empty();
    this.$el.append(Exceptionator.Soy.notice({n: this.model.toJSON()}));

    return this;
  }
});

Exceptionator.StaticNoticeView = Backbone.View.extend({
  tagName: "div",
  className: "exc exc_hidden",
  initialize: function(options) {
    this.$el.empty();
    this.$el.append(Exceptionator.Soy.emptyNotice({text: options.text}));
  },

  render: function() {
    return this;
  }
});


/*
 * NoticeList
 */
Exceptionator.NoticeList = Backbone.Collection.extend({

  model: Exceptionator.Notice,

  initialize: function(models, options) {
    if (options.timestamp) {
      this.listType = Exceptionator.ListTypes.HISTORY;
      this.bucketName = options.bucketName;
      this.bucketKey = options.bucketKey;
      this.timestamp = Number(options.timestamp);
      this.timeOffset = Date.now() - this.timestamp;
      this.urlPart = '/api/history/' + encodeURIComponent(this.bucketName) + '/';
      this.id = 'history_' + this.bucketName.replace(/\W/g,'_') + '_';
      if (this.bucketName in Exceptionator.Config.friendlyNames) {
        this.title = Exceptionator.Config.friendlyNames[this.bucketName];
      } else {
        this.title = this.bucketName;
      }
      if (this.bucketKey) {
        this.urlPart += encodeURIComponent(this.bucketKey) + '/';
        this.id += this.bucketKey.replace(/\W/g,'_') + '_';
        this.title += ': ' + this.bucketKey;
      }
      this.id += this.timestamp;
      this.title += ' sampled history starting at ' + (new Date(this.timestamp).toUTCString());

    } else if (options.bucketName && options.bucketKey) {
      this.listType = Exceptionator.ListTypes.BUCKET_KEY;
      this.bucketKey = options.bucketKey;
      this.bucketName = options.bucketName;
      this.urlPart = '/api/notices/' + encodeURIComponent(this.bucketName) + '/' + encodeURIComponent(this.bucketKey) + '?';
      this.id = this.bucketName.replace(/\W/g,'_') + '_' + this.bucketKey.replace(/\W/g,'_');
      if (this.bucketName in Exceptionator.Config.friendlyNames) {
        this.title = Exceptionator.Config.friendlyNames[this.bucketName];
      } else {
        this.title = this.bucketName;
      }
      this.title += ': ' + this.bucketKey;

    } else if (options.bucketName) {
      this.listType = Exceptionator.ListTypes.BUCKET_GROUP;
      this.bucketName = options.bucketName;
      this.urlPart = '/api/notices/' + encodeURIComponent(this.bucketName) + '?';
      this.id = this.bucketName.replace(/\W/g,'_');
      if (this.bucketName in Exceptionator.Config.friendlyNames) {
        this.title = Exceptionator.Config.friendlyNames[this.bucketName];
      } else {
        this.title = this.bucketName;
      }

    } else {
      this.query = options.query;
      this.listType = Exceptionator.ListTypes.SEARCH;
      this.urlPart = '/api/search?q=' + encodeURIComponent(this.query) + '&';
      this.id = '_search_' + this.query.replace(/\W/g,'_');
      this.title = 'search: ' + this.query;
    }
  },

  url: function() {
    if (this.listType == Exceptionator.ListTypes.HISTORY) {
      return this.urlPart + (Date.now()-this.timeOffset) + '?limit=' + encodeURIComponent(Exceptionator.Limit);
    } else {
      return this.urlPart + 'limit=' + encodeURIComponent(Exceptionator.Limit);
    }
  },

  timeReverseSorted: function() {
    return this.sortBy(Exceptionator.NoticeList.reverseTimeIterator_);
  },

  histograms: function() {
    var empty = Exceptionator.Notice.emptyHistogram(new Date());
    var seriesList = [];

    if (this.listType == Exceptionator.ListTypes.BUCKET_KEY) {
      var mostRecent = this.min(Exceptionator.NoticeList.reverseTimeIterator_);
      if (mostRecent) {
        seriesList = [{label: '', values: mostRecent.histogram(mostRecent.get('bkts')[this.bucketName]['h'])}];
      }
    } else if (this.listType == Exceptionator.ListTypes.BUCKET_GROUP) {
      seriesList = _.map(this.timeReverseSorted(), function(model) {
        return {label: model.get('bkts')[this.bucketName]['k'], values: model.histogram(model.get('bkts')[this.bucketName]['h'])};
      }, this);
    } else if (this.listType == Exceptionator.ListTypes.HISTORY) {
      // Gives the impression the graph is updating in real time by scaling the most recent value
      var end = new Date(Date.now() - this.timeOffset);
      var endTime = end.getTime();
      var step = 60 * 1000;
      if (Exceptionator.GraphSpan == Exceptionator.GraphSpans.LAST_DAY) {
        step = 60 * 60 * 1000;
      } else if (Exceptionator.GraphSpan == Exceptionator.GraphSpans.LAST_MONTH) {
        step = 24 * 60 * 60 * 1000;
      }
      empty = Exceptionator.Notice.emptyHistogram(end);
      var lastKey = end.getTime();  // mutated by emptyHistogram

      if (this.bucketKey) {
        var mostRecent = this.min(Exceptionator.NoticeList.reverseTimeIterator_);
        if (mostRecent) {
          var histogram = mostRecent.histogram(mostRecent.get('bkts')[this.bucketName]['h']);
          if (lastKey in histogram) {
            histogram[lastKey] = histogram[lastKey] * ((endTime - lastKey) / step);
          }
          seriesList = [{label: '', values: histogram}];
        }
      } else {
        var labels = {};
        _.each(this.timeReverseSorted(), function(model) {
          var label = model.get('bkts')[this.bucketName]['k'];
          if (!(label in labels)) {
            var histogram = model.histogram(model.get('bkts')[this.bucketName]['h']);
            if (lastKey in histogram) {
              histogram[lastKey] = histogram[lastKey] * ((endTime - lastKey) / step);
            }
            labels[label] = null;
            seriesList.push({label: label, values: histogram});
          }
        }, this);
      }
    }

    var retval = [];
    _.each(seriesList, function(series) {
      var rawData = [];
      retval.push({label: series.label, data:rawData});
      _.each(_.extend({}, empty, series.values), function (value, timestamp) {
        if (empty.hasOwnProperty(timestamp)) {
          rawData.push([timestamp, value]);
        }
      });
    });
    return retval;
  }

}, {
  reverseTimeIterator_: function(notice) {
    return -notice.get('d');
  }
});


Exceptionator.NoticeListView = Backbone.View.extend({

  tagName: "div",

  events: {
    "click .exc_header": "toggleBody",
    "click .bucketLink": "handleLinkClick",
    "plotclick": "handlePlotClick"
  },

  toggleBody: function(e) {
    $(e.target).parents('.exc').toggleClass('exc_hidden');
  },

  handleClick: function(e, url) {
    // neat trick from http://dev.tenfarms.com/posts/proper-link-handling
    if (!e.altKey && !e.ctrlKey && !e.metaKey && !e.shiftKey) {
      e.preventDefault();
      Exceptionator.Router.navigate(url, { trigger: true });
    } else {
      e.stopPropagation();
    }
  },

  handleLinkClick: function(e) {
    this.handleClick(e, $(e.target).attr("href").replace(/^\//, ""));
  },

  handlePlotClick: function (e, pos, item) {
    var url = '/history/' + encodeURIComponent(this.collection.bucketName) + '/';
    if (this.collection.bucketKey) {
      url += encodeURIComponent(this.collection.bucketKey) + '/';
    }
    url += Math.floor(pos.x);
    this.handleClick(e, url);
  },

  getTitleEl: function() {
    return $('#title_' + this.id, this.el);
  },

  getGraphEl: function() {
    return $('#plot_' + this.id, this.el);
  },

  getListEl: function() {
    return $('#' + this.id, this.el);
  },

  initialize: function(options) {
    this.collection.on('add', this.add, this);
    this.collection.on('reset', this.reset, this);
    this.id = 'view_' + this.collection.id;
    if (options.hasOwnProperty('showGraph')) {
      this.showGraph = options.showGraph;
    } else {
      this.showGraph = true;
    }

    if (this.collection.listType == Exceptionator.ListTypes.SEARCH) {
      this.showGraph = false;
    }
    if (options.hasOwnProperty('showList')) {
      this.showList = options.showList;
    } else {
      this.showList = true;
    }
    this.noticeViews = {};
    this.colorQueue = Exceptionator.COLOR_LIST.slice(0); // Copy color list

    this.labelColors = new LRUCache(Exceptionator.COLOR_LIST.length + 1);
    _.each(Exceptionator.COLOR_LIST, function(color) {
      this.labelColors.put(color, color);
    }, this);

  },

  shouldShowGraph: function() {
    return this.showGraph;
  },

  shouldShowList: function() {
    if (this.collection.isEmpty()) {
      return true;
    } else {
      return this.showList;
    }
  },

  reset: function() {
    this.getListEl().empty();
    if (this.collection.isEmpty()) {
      var view = new Exceptionator.StaticNoticeView({text: "No results"})
      this.getListEl().append(view.el);
    } else {
      this.addNotices(this.collection.models);
    }
    this.trigger('Exceptionator:NoticeListView:reset', this);
  },

  add: function(toAdd) {
    if ($.isArray(toAdd)) {
      this.addNotices(toAdd);
    } else {
      this.addNotice(toAdd);
    }
  },

  addNotices: function(notices) {
    _.each(notices, function(notice) {
      this.addNotice(notice);
    }, this);
  },

  addNotice: function(notice) {
    var el;
    if (this.noticeViews.hasOwnProperty(notice.id)) {
      el = this.noticeViews[notice.id].el;
    } else {
      var view = new Exceptionator.NoticeView({model: notice});
      this.noticeViews[notice.id] = view;
      el = view.render().el;
    }
    this.getListEl().append(el);
  },

  renderGraph: function() {
    if (this.shouldShowGraph()) {
      var options = {
        lines: { show: true },
        legend: { position: "nw" },
        xaxis: { mode: "time", timezone: "browser" },
        grid: { clickable: true }
      };
      var histograms = _.first(this.collection.histograms(), Exceptionator.NoticeListView.MAX_GRAPH_LINES);

      // reuse existing color, or pick a color at the end of the cache
      _.each(histograms, function(h) {
        var color = this.labelColors.get(h.label);
        if (typeof color == 'undefined') {
          // sigh, this is what shift() should be doing
          color = this.labelColors.head.value;
          this.labelColors.remove(this.labelColors.head.key);
          if (typeof color == 'undefined') {
            console.log(this.labelColors);
          }
          this.labelColors.put(h.label, color);
        }
        h.color = color;
      }, this);


      $.plot(
        this.getGraphEl(),
        histograms,
        options);

    }
    return this;
  },

  renderFetch: function() {
    if (this.collection.isEmpty()) {
      shouldShowList = true;
      shouldShowGraph = false;
      this.$el.empty();
      this.$el.append(Exceptionator.Soy.noticeList({
        id: this.id,
        showList: this.shouldShowList(),
        showGraph: this.shouldShowGraph(),
        title: this.collection.title}));
      var view = new Exceptionator.StaticNoticeView({text: "Fetching results..."})
      this.getListEl().append(view.el);
    }
    return this;
  },

  render: function() {
    this.$el.empty();
    this.$el.append(Exceptionator.Soy.noticeList({
      id: this.id,
      showList: this.shouldShowList(),
      showGraph: this.shouldShowGraph(),
      title: this.collection.title}));
    this.reset();
    return this;
  }

}, {
  MAX_GRAPH_LINES: 8,
});

Exceptionator.AppView = Backbone.View.extend({
  initialize: function(options) {
    this.noticeListViews = [];
    if (options.initialConfigs) {
      _.each(options.initialConfigs, this.addList, this);
    }
  },

  clear: function() {
    this.noticeListViews = [];
    this.canRenderGraphs = false;
  },

  addList: function(listOptions) {
    var list = new Exceptionator.NoticeList([], listOptions.list);
    var view = new Exceptionator.NoticeListView(_.extend({collection: list}, listOptions.view));
    view.on('Exceptionator:NoticeListView:reset', this.renderGraph, this);
    this.noticeListViews.push(view);
  },

  render: function() {
    this.$el.empty();
    _.each(this.noticeListViews, function(view) {
      this.$el.append(view.render().el);
    }, this);
    this.canRenderGraphs = true;
    return this;
  },

  renderGraph: function(view) {
    if (this.canRenderGraphs) {
      view.renderGraph();
    }
  },

  renderGraphs: function() {
    _.each(this.noticeListViews, function(view) {
      this.renderGraph(view);
    }, this);
    return this;
  },

  fetchError: function(collection, xhr, options) {
    // Log 5XXs, refresh the page on anything else
    if (xhr.status / 100 === 5) {
      console.log(
        'Server error while fetching data for collection: ' + collection.id +
        '. This probably means exceptionator and/or its mongod are under high load right now.'
      );
    } else {
      window.location.reload();
    }
  },

  fetch: function() {
    _.each(this.noticeListViews, function(view) {
      view.renderFetch().collection.fetch({error: this.fetchError});
    }, this);
  }
});


/*
 * UserFilter
 */
Exceptionator.UserFilter = Backbone.Model.extend({
  idAttribute: '_id',
  urlRoot: '/api/filters',
  arrayFields: {'cc': true, 'c': true},

  defaults: function() {
    return {
      'lm': 0,
      'mute': 0,
      'u': Exceptionator.Config.userId
    }
  },

  initialize: function() {
    this.calculateLabels();
  },

  calculateLabels: function() {
    this.set({lm_fmt: Exceptionator.formatDate(this.get('lm'))})
    var mute = +(this.get('mute'));
    if (mute > new Date().getTime()) {
      this.set({mute_fmt: Exceptionator.formatDate(mute)});
    } else {
      this.set({mute_fmt: ''});
    }
  },

  mute: function(muteMinutes) {
    this.set({mute: (new Date().getTime() + muteMinutes * 60 * 1000).toString() });
    this.save();
  },

  setValues: function(serializedFormArray, view) {
    _.each(serializedFormArray, function(elem) {
      if (elem.name in this.arrayFields) {
        this.set(elem.name, _.filter(elem.value.split(/\s+/), function(t) { return t; }));
      } else {
        this.set(elem.name, elem.value);
      }
    }, this);
    this.save();
    this.fetch({success: function() { view.render(); }})
  }
})

/*
 * UserFilterView
 */
Exceptionator.UserFilterView = Backbone.View.extend({
  tagName: "div",

  events: {
    "click .btn": "processAction",
    "change .triggerType": "triggerTypeChange",
    "keyup .liveTitle": "titleChange"
  },

  mutePrefix: "mute_",


  baseRender: function(template) {
    this.$el.empty();
    this.$el.append(template({
      f: this.model.toJSON(),
      u: Exceptionator.Config.userId
    }));
  },

  render: function() {
    this.baseRender(Exceptionator.Soy.userFilter);
    this.renderConfig(this.model.get('tt'));
    this.renderTitle(this.model.get('n'));
    return this;
  },

  renderCompact: function() {
    this.baseRender(Exceptionator.Soy.userFilterCompact);
    return this;
  },

  renderConfig: function(triggerType) {
    if (triggerType === 'threshold') {
      this.$el.find('.thresholdCfg').show();
    } else {
      this.$el.find('.thresholdCfg').hide();
    }
  },

  triggerTypeChange: function(e) {
    this.renderConfig(e.currentTarget.value);
  },

  renderTitle: function(title) {
    var id = this.model.get('_id');
    if (id && !title) {
        title = id;
    } else if (!title) {
      title = 'New Filter';
    }
    this.$el.find('legend').text(title);
  },

  titleChange: function(e) {
    this.renderTitle(e.currentTarget.value);
  },

  processAction: function(e) {
    var actionType = e.currentTarget.value
    if (actionType === "delete") {
      this.model.destroy({success: function() {Exceptionator.Router.navigate('/filters', {trigger: true});}});
    } else {
      if (actionType.substring(0, this.mutePrefix.length) === this.mutePrefix) {
        var muteMinutes = +(actionType.substring(this.mutePrefix.length))
        this.model.mute(muteMinutes);
      } else if (actionType == 'update' || actionType == 'save') {
        this.model.setValues(this.$el.find('form').serializeArray(), this);
      }
      this.model.calculateLabels();
      this.render();
    }
    return false;
  }
});

/*
 * UserFilterList
 */
Exceptionator.UserFilterList = Backbone.Collection.extend({
  model: Exceptionator.UserFilter,
  url: function() {
    return '/api/filters'
  }
});

/*
 * UserFilterListView
 */
Exceptionator.UserFilterListView = Backbone.View.extend({
  tagName: "div",
  events: {
    "click .filterAdd": "addNew"
  },

  initialize: function(options) {
    this.collection.on('add', this.add, this);
    this.collection.on('reset', this.render, this);
  },

  addNew: function() {
    var model = new Exceptionator.UserFilter();
    model.on('sync', this.render, this);
    this.collection.add(model);
  },

  add: function(m, compact) {
    var view = new Exceptionator.UserFilterView({model: m});
    if (compact === true) {
      view.renderCompact();
    } else {
      view.render();
    }
    this.$el.find('.filters').append(view.el);
  },

  render: function() {
    this.$el.empty();
    this.$el.append(Exceptionator.Soy.userFilterList({
      u: Exceptionator.Config.userId
    }));
    _.each(this.collection.models, function(m) {
      this.add(m, true);
    }, this);
    return this;
  }
});


Exceptionator.Routing = Backbone.Router.extend({
  initialize: function(options) {
    this.app = options.app;
    this.homepageConfig = options.homepage;
  },

  routes: {
    "":                                          "index",
    "search/?q=:query":                          "search",
    "notices/:bucketName/:bucketKey":            "bucket",
    "notices/:bucketName":                       "bucketGroup",
    "filters":                                   "filters",
    "filters/":                                  "filters",
    "filters/:filterId":                         "filters",
    "history/:bucketName/:bucketKey/:timestamp": "historyBucket",
    "history/:bucketName/:timestamp":            "historyGroup"
  },

  route_: function(listDefs) {
    this.app.clear();
    _.each(listDefs, function(listDef) {
      this.app.addList(listDef);
    }, this);
    this.app.render();

    if (Exceptionator.Paused) {
      this.app.fetch();
    }
  },

  filters: function(filterId) {
    this.app.clear();
    if (filterId) {
      var filter = new Exceptionator.UserFilter({_id: filterId});
      filter.fetch();
      var view = new Exceptionator.UserFilterView({
        el: this.app.el, // take over the main view
        model: filter
      });
      filter.fetch({ success: function() { view.render(); }});
    } else {
      var filterList = new Exceptionator.UserFilterList();
      var view = new Exceptionator.UserFilterListView({
        el: this.app.el, // take over the main view
        collection: filterList
      });
      filterList.fetch({ success: function() { console.log('success'); view.render(); console.log('rendered');},
        error: function() {
          console.log('badddd');
        }
      });
    }
  },

  index: function() {
    this.route_(this.homepageConfig);
  },

  search: function(query) {
    this.route_([{list: {query: decodeURIComponent(query).toLowerCase()}}]);
  },

  bucket: function(bucketName, bucketKey) {
    this.route_([{list: {bucketName: decodeURIComponent(bucketName), bucketKey: decodeURIComponent(bucketKey)}}]);
  },

  bucketGroup: function(bucketName) {
    this.route_([{list: {bucketName: decodeURIComponent(bucketName)}}]);
  },

  historyBucket: function(bucketName, bucketKey, timestamp) {
    this.route_([{list: {
      bucketName: decodeURIComponent(bucketName),
      bucketKey: decodeURIComponent(bucketKey),
      timestamp: timestamp
    }}]);
  },

  historyGroup: function(bucketName, timestamp) {
    this.route_([{list: {
      bucketName: decodeURIComponent(bucketName),
      timestamp: timestamp
    }}]);
  }
});
