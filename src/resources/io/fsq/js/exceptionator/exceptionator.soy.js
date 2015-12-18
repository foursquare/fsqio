// This file was automatically generated from exceptionator.soy.
// Please don't edit this file by hand.

if (typeof Exceptionator == 'undefined') { var Exceptionator = {}; }
if (typeof Exceptionator.Soy == 'undefined') { Exceptionator.Soy = {}; }


Exceptionator.Soy.kwLink = function(opt_data, opt_sb) {
  var output = opt_sb || new soy.StringBuilder();
  output.append('<a class="bucketLink" href="/search/?q=', soy.$$escapeHtml(opt_data.kw), '">', soy.$$escapeHtml(opt_data.kw), '</a>');
  return opt_sb ? '' : output.toString();
};


Exceptionator.Soy.bucketLink = function(opt_data, opt_sb) {
  var output = opt_sb || new soy.StringBuilder();
  output.append('<a class="bucketLink" href="/notices/', soy.$$escapeHtml(opt_data.b.nm_uri), '/', soy.$$escapeHtml(opt_data.b.k_uri), '" title="', soy.$$escapeHtml(opt_data.b.k), '">', soy.$$truncate(soy.$$escapeHtml(opt_data.b.k), 13, true), ' ', (opt_data.b.n) ? '(' + soy.$$escapeHtml(opt_data.b.n) + ')' : '', '</a>');
  return opt_sb ? '' : output.toString();
};


Exceptionator.Soy.bucketBody = function(opt_data, opt_sb) {
  var output = opt_sb || new soy.StringBuilder();
  output.append('<div class="header">First seen at</div><div class="items"><div class="item">', soy.$$escapeHtml(opt_data.b.df_fmt), '</div></div><div class="header">First seen version</div><div class="items"><div class="item">', soy.$$escapeHtml(opt_data.b.vf), '</div></div>');
  return opt_sb ? '' : output.toString();
};


Exceptionator.Soy.noticeBody = function(opt_data, opt_sb) {
  var output = opt_sb || new soy.StringBuilder();
  output.append('<div class="header">Message list</div><div class="items">');
  var iList33 = opt_data.n.msgs;
  var iListLen33 = iList33.length;
  for (var iIndex33 = 0; iIndex33 < iListLen33; iIndex33++) {
    var iData33 = iList33[iIndex33];
    output.append('<div class="item">', soy.$$escapeHtml(iData33), ' ', (! (iIndex33 == iListLen33 - 1)) ? ' Caused by: ' : '', '</div>');
  }
  output.append('</div><div class="header">Exception list</div><div class="items">');
  var iList43 = opt_data.n.excs;
  var iListLen43 = iList43.length;
  for (var iIndex43 = 0; iIndex43 < iListLen43; iIndex43++) {
    var iData43 = iList43[iIndex43];
    output.append('<div class="item">', soy.$$escapeHtml(iData43), ' ', (! (iIndex43 == iListLen43 - 1)) ? ' Caused by: ' : '', '</div>');
  }
  output.append('</div><div class="header">Backtrace</div><div class="items">');
  var btList53 = opt_data.n.bt;
  var btListLen53 = btList53.length;
  for (var btIndex53 = 0; btIndex53 < btListLen53; btIndex53++) {
    var btData53 = btList53[btIndex53];
    var iList54 = btData53;
    var iListLen54 = iList54.length;
    for (var iIndex54 = 0; iIndex54 < iListLen54; iIndex54++) {
      var iData54 = iList54[iIndex54];
      output.append('<div class="item">', soy.$$escapeHtml(iData54), '</div>');
    }
  }
  output.append('</div><div class="header">Matching buckets:</div><div class="items">');
  var kList61 = soy.$$getMapKeys(opt_data.n.bkts);
  var kListLen61 = kList61.length;
  for (var kIndex61 = 0; kIndex61 < kListLen61; kIndex61++) {
    var kData61 = kList61[kIndex61];
    if (kData61 != 'all') {
      output.append('<div class="item">', soy.$$escapeHtml(opt_data.n.bkts[kData61].friendlyName), ' -  ');
      Exceptionator.Soy.bucketLink({b: opt_data.n.bkts[kData61]}, output);
      output.append('</div>');
    }
  }
  output.append('</div>', (opt_data.n.h) ? '<div class="header">Host</div><div class="items"><div class="item">' + soy.$$escapeHtml(opt_data.n.h) + '</div></div>' : '', '<div class="header">Version</div><div class="items"><div class="item">', soy.$$escapeHtml(opt_data.n.v), '</div></div><div class="header">Session</div><div class="items">');
  var kList80 = soy.$$getMapKeys(opt_data.n.sess);
  var kListLen80 = kList80.length;
  for (var kIndex80 = 0; kIndex80 < kListLen80; kIndex80++) {
    var kData80 = kList80[kIndex80];
    output.append('<div class="item">', soy.$$escapeHtml(kData80), ': ', soy.$$escapeHtml(opt_data.n.sess[kData80]), '</div>');
  }
  output.append('</div><div class="header">Environment</div><div class="items">');
  var kList88 = soy.$$getMapKeys(opt_data.n.env);
  var kListLen88 = kList88.length;
  for (var kIndex88 = 0; kIndex88 < kListLen88; kIndex88++) {
    var kData88 = kList88[kIndex88];
    output.append('<div class="item">', soy.$$escapeHtml(kData88), ': ', soy.$$escapeHtml(opt_data.n.env[kData88]), '</div>');
  }
  output.append('</div><div class="header">Keywords</div><div class="items"><div class="item">');
  var kwList96 = opt_data.n.kw;
  var kwListLen96 = kwList96.length;
  for (var kwIndex96 = 0; kwIndex96 < kwListLen96; kwIndex96++) {
    var kwData96 = kwList96[kwIndex96];
    Exceptionator.Soy.kwLink({kw: kwData96}, output);
    output.append('  ');
  }
  output.append('</div></div>');
  return opt_sb ? '' : output.toString();
};


Exceptionator.Soy.noticeHeader = function(opt_data, opt_sb) {
  var output = opt_sb || new soy.StringBuilder();
  output.append('<div class="exc_header">');
  var kList104 = soy.$$getMapKeys(opt_data.n.bkts);
  var kListLen104 = kList104.length;
  for (var kIndex104 = 0; kIndex104 < kListLen104; kIndex104++) {
    var kData104 = kList104[kIndex104];
    if (kData104 != 'all') {
      output.append('<div class="exc_n">', soy.$$escapeHtml(opt_data.n.bkts[kData104].friendlyName), ' -  ');
      Exceptionator.Soy.bucketLink({b: opt_data.n.bkts[kData104]}, output);
      output.append('</div>');
    }
  }
  output.append('<div class="timehost">', soy.$$escapeHtml(opt_data.n.d_fmt), ' ', (opt_data.n.h) ? ' on ' + soy.$$escapeHtml(opt_data.n.h) + '</div> ' : '', ' ', soy.$$escapeHtml(opt_data.n.msgs[0]), '</div>');
  return opt_sb ? '' : output.toString();
};


Exceptionator.Soy.notice = function(opt_data, opt_sb) {
  var output = opt_sb || new soy.StringBuilder();
  Exceptionator.Soy.noticeHeader(opt_data, output);
  output.append('<div class="exc_detail" >');
  Exceptionator.Soy.noticeBody(opt_data, output);
  output.append('</div>');
  return opt_sb ? '' : output.toString();
};


Exceptionator.Soy.emptyNotice = function(opt_data, opt_sb) {
  var output = opt_sb || new soy.StringBuilder();
  output.append('<div class="exc_header" style="text-align:center;border-bottom-width:1px;border-bottom-style:solid">', soy.$$escapeHtml(opt_data.text), '</div>');
  return opt_sb ? '' : output.toString();
};


Exceptionator.Soy.noticeList = function(opt_data, opt_sb) {
  var output = opt_sb || new soy.StringBuilder();
  output.append('<div id="outer_', soy.$$escapeHtml(opt_data.id), '"><div class="row"><div class="span12"><h4 id="title_', soy.$$escapeHtml(opt_data.id), '">', soy.$$escapeHtml(opt_data.title), '</h4></div></div>', (opt_data.showGraph) ? '<div class="row"><div class="span12"><div id="plot_' + soy.$$escapeHtml(opt_data.id) + '" style="width:940px;height:170px"></div></div></div>' : '', (opt_data.showList) ? '<div class="row"><div class="span12"><div id="' + soy.$$escapeHtml(opt_data.id) + '"></div></div></div>' : '', '</div>');
  return opt_sb ? '' : output.toString();
};


Exceptionator.Soy.userFilter = function(opt_data, opt_sb) {
  var output = opt_sb || new soy.StringBuilder();
  output.append('<form><fieldset><legend></legend>', (opt_data.f._id) ? '<input type="hidden" name="_id" value="' + soy.$$escapeHtml(opt_data.f._id) + '" />' : '', '<label>Name</label><input type="text" name="n" value="', (opt_data.f.n) ? soy.$$escapeHtml(opt_data.f.n) : '', '" class="liveTitle" /><label>Owner: ', (opt_data.f.u) ? soy.$$escapeHtml(opt_data.f.u) : soy.$$escapeHtml(opt_data.u), '</label><input type="hidden" name="u" value="', (opt_data.f.u) ? soy.$$escapeHtml(opt_data.f.u) : '', '" />', (opt_data.f.lm && opt_data.f.lm_fmt) ? '<label>Last matched: ' + soy.$$escapeHtml(opt_data.f.lm_fmt) + '</label>' : '', (opt_data.f.mute_fmt) ? '<label>Muted until: ' + soy.$$escapeHtml(opt_data.f.mute_fmt) + ' &nbsp;<button type="button" class="btn" value="mute_0">Unmute</button></label>' : '', (opt_data.f._id) ? '<label> Mute: &nbsp;<button type="button" class="btn" value="mute_60">1 hour</button>&nbsp;<button type="button" class="btn" value="mute_1440">1 day</button></label>' : '', '<label>CC (one per line)</label><textarea rows="3" name="cc">');
  if (opt_data.f.cc) {
    var ccList191 = opt_data.f.cc;
    var ccListLen191 = ccList191.length;
    for (var ccIndex191 = 0; ccIndex191 < ccListLen191; ccIndex191++) {
      var ccData191 = ccList191[ccIndex191];
      output.append(soy.$$escapeHtml(ccData191), '\n');
    }
  }
  output.append('</textarea><label>Alert condition</label><select name="ft"><option value="kw" ', (opt_data.f.ft == 'kw') ? ' selected="selected" ' : '', '>keywords</option><!--<option value="b" ', (opt_data.f.ft == 'b') ? ' selected="selected" ' : '', '>buckets</option>--></select><label>Criteria to match (conjunct of space-separated terms)</label><textarea rows="3" name="c">');
  if (opt_data.f.c) {
    var critList206 = opt_data.f.c;
    var critListLen206 = critList206.length;
    for (var critIndex206 = 0; critIndex206 < critListLen206; critIndex206++) {
      var critData206 = critList206[critIndex206];
      output.append(soy.$$escapeHtml(critData206), ' ');
    }
  }
  output.append('</textarea><label>Alert condition</label><select name="tt" class="triggerType"><option value="always" ', (opt_data.f.tt == 'always') ? ' selected="selected" ' : '', '>always</option><option value="never" ', (opt_data.f.tt == 'never') ? ' selected="selected" ' : '', '>never (I\'m here for the bucket)</option><option value="pwr2" ', (opt_data.f.tt == 'pwr2') ? ' selected="selected" ' : '', '>on powers of 2</option><option value="threshold" ', (opt_data.f.tt == 'threshold') ? ' selected="selected" ' : '', '>threshold is crossed</option></select><div class="thresholdCfg" style="display:none;"><label>Threshold</label>If the exception rate exceeds &nbsp;<input type="text" name="tl" value="', (opt_data.f.tl || opt_data.f.tl == 0) ? soy.$$escapeHtml(opt_data.f.tl) : '', '" style="width: 6em;"/> exceptions per minute, &nbsp;<input type="text" name="tc" value="', (opt_data.f.tc || opt_data.f.tc == 0) ? soy.$$escapeHtml(opt_data.f.tc) : '', '"  style="width: 4em;"/> (&lt;=60) times in the last &nbsp;<input type="text" name="p" value="', (opt_data.f.p || opt_data.f.p == 0) ? soy.$$escapeHtml(opt_data.f.p) : '', '" style="width: 4em;"/> (&lt;=60) minutes, send an alert.</div><div>', (opt_data.f._id) ? '<button type="button" class="btn" value="update">Update</button><button type="button" class="btn" value="delete">Delete</button>' : '<button type="button" class="btn" value="save">Save</button>', '</div></fieldset></form>');
  return opt_sb ? '' : output.toString();
};


Exceptionator.Soy.userFilterCompact = function(opt_data, opt_sb) {
  var output = opt_sb || new soy.StringBuilder();
  output.append((opt_data.f.n) ? soy.$$escapeHtml(opt_data.f.n) : '', ' -', (opt_data.f._id) ? ((opt_data.f.lm) ? '&nbsp; matched: ' + soy.$$escapeHtml(opt_data.f.lm_fmt) + ' -' : '') + '&nbsp; <a class="bucketLink" onclick="Exceptionator.Router.navigate(\'/notices/uf/' + soy.$$escapeHtml(opt_data.f._id) + '\', {trigger: true})">view bucket</a> - &nbsp;<a class="bucketLink" onclick="Exceptionator.Router.navigate(\'/filters/' + soy.$$escapeHtml(opt_data.f._id) + '\', {trigger: true})">edit</a>' : '');
  return opt_sb ? '' : output.toString();
};


Exceptionator.Soy.userFilterList = function(opt_data, opt_sb) {
  var output = opt_sb || new soy.StringBuilder();
  output.append('<div><h4>', (opt_data.u) ? soy.$$escapeHtml(opt_data.u) + '\'s' : '', ' filters</h4><p>You can use filters to define buckets for one or more search terms, and receive an email based on the rate of occurance.  Buckets are a unit of aggregation for notices.  Their primary purpose is to provide a histogram so that the rate of occurance can be tracked.</p><div class="filters"></div><button class="btn filterAdd" value="add">add a new filter</button></div>');
  return opt_sb ? '' : output.toString();
};
