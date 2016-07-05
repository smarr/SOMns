/* jshint -W097 */
"use strict";

function sourceToArray(source) {
  var arr = new Array(source.length),
    i;
  for (i = 0; i < source.length; i += 1) {
    arr[i] = source[i];
  }
  return arr;
}

function Begin(section) {
  this.section = section;
  this.type    = Begin;
}

Begin.prototype.toString = function () {
  return '<span id="' + this.section.id + '" class="' + this.section.tags.join(" ") + '">';
};

function End(section) {
  this.section = section;
  this.type    = End;
}

End.prototype.toString = function () {
  return '</span>';
};

function Annotation(char) {
  this.char   = char;
  this.before = [];
  this.after  = [];
}

Annotation.prototype.toString = function() {
  this.before.sort(function (a, b) {
    if (a.section.type !== b.section.type) {
      if (a.section.type === Begin) {
        return -1;
      } else {
        return 1;
      }
    }

    if (a.section.length === b.section.length) {
      return 0;
    }

    if (a.section.length < b.section.length) {
      return (a.section.type === Begin) ? 1 : -1;
    } else {
      return (a.section.type === Begin) ? -1 : 1;
    }
  });

  var result = this.before.join("");
  result += this.char;
  result += this.after.join("");
  return result;
};

function arrayToString(arr) {
  var result = "";
  for (var i = 0; i < arr.length; i++) {
    result += arr[i].toString();
  }
  return result;
}

function nodeFromTemplate(tplId) {
  var tpl = document.getElementById(tplId),
    result = tpl.cloneNode(true);
  result.removeAttribute("id");
  return result;
}

function createLineNumbers(cnt) {
  var result = "<span class='ln' onclick='ctrl.onToggleLineBreakpoint(1, this);'>1</span>",
    i;
  for (i = 2; i <= cnt; i += 1) {
    result = result + "\n<span class='ln' onclick='ctrl.onToggleLineBreakpoint("+ i +", this);'>" + i + "</span>";
  }
  return result;
}

function ensureItIsAnnotation(arr, idx) {
  if (!(arr[idx] instanceof Annotation)) {
    arr[idx] = new Annotation(arr[idx]);
  }
  return arr[idx];
}

function annotateArray(arr, sourceId, sections) {
  for (var sId in sections) {
    var s = sections[sId];
    if (s.sourceId === sourceId) {
      var start = ensureItIsAnnotation(arr, s.firstIndex),
        end   = ensureItIsAnnotation(arr, s.firstIndex + s.length);
      start.before.push(new Begin(s));
      end.before.push(new End(s));
    }
  }
}

function countNumberOfLines(str) {
  var cnt = 1;
  for (var i = 0; i < str.length; i++) {
    if (str[i] === "\n") {
      cnt += 1;
    }
  }
  return cnt;
}

function showSource(s, sections) {
  var tabListEntry = document.getElementById(s.id),
    aElem = document.getElementById("a" + s.id);
  if (tabListEntry) {
    if (aElem.innerText !== s.name) {
      $(tabListEntry).remove();
      $(aElem).remove();
      tabListEntry = null;
      aElem = null;
    } else {
      return; // source is already there, so, I think, we don't need to update it
    }
  }

  var annotationArray = sourceToArray(s.sourceText);
  annotateArray(annotationArray, s.id, sections);

  tabListEntry = nodeFromTemplate("tab-list-entry");

  if (aElem === null) {
    // create the tab "header/handle"
    aElem = $(tabListEntry).find("a");
    aElem.attr("href", "#" + s.id);
    aElem.attr("id", "a" + s.id);
    aElem.text(s.name);
    $("#tabs").append(tabListEntry);
  }

  // create tab pane
  var newFileElement = nodeFromTemplate("file");
  newFileElement.setAttribute("id", s.id);
  newFileElement.getElementsByClassName("line-numbers")[0].innerHTML = createLineNumbers(countNumberOfLines(s.sourceText));
  newFileElement.getElementsByClassName("source-file")[0].innerHTML = arrayToString(annotationArray);

  var files = document.getElementById("files");
  files.appendChild(newFileElement);
}

function showInvocationProfile(profileData, rootSpan) {
  if (profileData.length < 1) {
    return;
  }

  var profiles = nodeFromTemplate("invocation-profiles");

  for (var i = 0; i < profileData.length; i++) {
    var profile = nodeFromTemplate("invocation-profile");
    profile.getElementsByClassName("counter")[0].textContent = profileData[i].invocations;
    profile.getElementsByClassName("types")[0].textContent = profileData[i].somTypes.join(", ");
    profiles.appendChild(profile);
  }

  var html = $(profiles).html();

  $(rootSpan).attr("data-toggle", "popover");
  $(rootSpan).attr("data-trigger", "focus hover");
  $(rootSpan).attr("title", "Invocation Profile");
  $(rootSpan).attr("data-content", html);
  $(rootSpan).attr("data-html", "true");
  $(rootSpan).attr("data-placement", "auto top");
  $(rootSpan).popover();
}

var accessProfiles = [],
  maxAccessCount = -1;

function showAccessProfile(profileData, rootSpan) {
  if (profileData.count < 1) {
    return;
  }

  $(rootSpan).attr("data-toggle", "popover");
  $(rootSpan).attr("data-trigger", "focus hover");
//     $(rootSpan).attr("title", "");
  $(rootSpan).attr("data-content", "Accesses: " + profileData.count);
  $(rootSpan).attr("data-placement", "auto top");
  $(rootSpan).popover();

  // collect data for histogram of variable accesses
  maxAccessCount = Math.max(maxAccessCount, profileData.count);
  profileData.spanId = rootSpan.id;
  accessProfiles.push(profileData);
}

function showHistogramOfAccess() {
  var numBins = 6,
    binSize = Math.ceil(Math.log(maxAccessCount) / numBins),
    bins = new Array(numBins);
  bins.fill(0);

  accessProfiles.forEach(function(profileData) {
    var bin = Math.floor(Math.log(profileData.count) / binSize);
    bins[bin] += 1;
    $("#" + profileData.spanId).addClass("bucket-" + bin);
  });

  // generateGraph();
}

function generateGraph() {
  // A formatter for counts.
  var formatCount = d3.format(",.0f");
  var margin = {top: 10, right: 30, bottom: 30, left: 30},
    width = 960 - margin.left - margin.right,
    height = 500 - margin.top - margin.bottom;

  var x = d3.scale.linear()
    .domain([0, d3.max(accessProfiles, function(profileData) { return profileData.count; })])
    .range([0, width]);
  // Generate a histogram using 10 uniformly-spaced bins.
  var hist = d3.layout.histogram()
    .bins(10)
    .value(function(profileData) { return profileData.count; });
  var data = hist(accessProfiles);

  var y = d3.scale.linear()
    .domain([0, d3.max(data, function(d) { return d.y; })])
    .range([height, 0]);

  var xAxis = d3.svg.axis()
    .scale(x)
    .orient("bottom");

  var svg = d3.select("body").append("svg")
    .attr("width", width + margin.left + margin.right)
    .attr("height", height + margin.top + margin.bottom)
    .append("g")
    .attr("transform", "translate(" + margin.left + "," + margin.top + ")");
  var bar = svg.selectAll(".bar")
    .data(data)
    .enter().append("g")
    .attr("class", "bar")
    .attr("transform", function(d) { return "translate(" + x(d.x) + "," + y(d.y) + ")"; });
  bar.append("rect")
    .attr("x", 1)
    .attr("width", x(data[0].dx) - 1)
    .attr("height", function(d) { return height - y(d.y); });
  bar.append("text")
    .attr("dy", ".75em")
    .attr("y", 6)
    .attr("x", x(data[0].dx) / 2)
    .attr("text-anchor", "middle")
    .text(function(d) { return formatCount(d.y); });
  svg.append("g")
    .attr("class", "x axis")
    .attr("transform", "translate(0," + height + ")")
    .call(xAxis);
}

var heatmapStyleNode = null;
function toggleHeatMap() {
  if (heatmapStyleNode === null) {
    heatmapStyleNode = $("#style-heatmap").detach();
  } else {
    $("#style-main").after(heatmapStyleNode);
  }
}

/**
 * The HTML View, which realizes all access to the DOM for displaying
 * data and reacting to events.
 * @constructor
 */
function View() {
  this.currentSectionId = null;
  this.currentDomNode   = null;
  this.debuggerButtonJQNodes = null;
}

View.prototype.onConnect = function () {
  $("#dbg-connect-btn").html("Connected");
};

View.prototype.onClose = function () {
  $("#dbg-connect-btn").html("Reconnect");
};

View.prototype.displaySources = function (msg) {
  var sId;
  for (sId in msg.sources) {
    showSource(msg.sources[sId], msg.sections);
  }

  for (var ssId in msg.sections) {
    this.showSectionData(msg.sections[ssId]);
  }

  showHistogramOfAccess();

  $('.nav-tabs a[href="#' + sId + '"]').tab('show');

  /*
   if (first) {
   $(tabListEntry).addClass("active");
   }
   if (first) {
   $(newFileElement).addClass("active");
   }
   */
};

View.prototype.showSectionData = function (section) {
  if (section.data && section.data.methodInvocationProfile) {
    showInvocationProfile(section.data.methodInvocationProfile,
      document.getElementById(section.id));
  }

  if (section.data && (section.data.localReads !== null || section.data.localWrites !== null)) {
    if (section.data.localReads !== null && section.data.localWrites !== null) {
      throw "this is unexpected, adapt program";
    }
    showAccessProfile(section.data.localReads || section.data.localWrites,
      document.getElementById(section.id));
  }
};

function showVar(name, value, list) {
  var entry = nodeFromTemplate("frame-state-tpl");
  var t = $(entry).find("th");
  t.html(name);
  t = $(entry).find("td");
  t.html(value);
  list.appendChild(entry);
}

function showFrame(frame, i, list) {
  var stackEntry = frame.methodName;
  if (frame.sourceSection) {
    stackEntry += ":" + frame.sourceSection.line + ":" + frame.sourceSection.column;
  }
  var entry = nodeFromTemplate("stack-frame-tpl");
  entry.setAttribute("id", "frame-" + i);

  var tds = $(entry).find("td");
  tds[0].innerHTML = stackEntry;
  list.appendChild(entry);
}

View.prototype.displaySuspendEvent = function (data, getSource) {
  var list = document.getElementById("stack-frames");
  while (list.lastChild) {
    list.removeChild(list.lastChild);
  }

  for (var i = 0; i < data.stack.length; i++) {
    showFrame(data.stack[i], i, list);
  }

  list = document.getElementById("frame-state");
  while (list.lastChild) {
    list.removeChild(list.lastChild);
  }

  showVar("Arguments", data.topFrame.arguments.join(", "), list);

  for (var varName in data.topFrame.slots) {
    showVar(varName, data.topFrame.slots[varName], list);
  }

  // update the source sections for the sourceId
  if (data.sections) {
    var pane = document.getElementById(data.sourceId);
    var sourceFile = $(pane).find(".source-file");

    // remove all spans
    sourceFile.find("span").replaceWith($(".html"));

    // apply new spans
    var source = getSource(data.sourceId);
    var annotationArray = sourceToArray(source.sourceText);
    annotateArray(annotationArray, source.id, data.sections);
    sourceFile.html(arrayToString(annotationArray));

    // enable clicking on EventualSendNodes
    sourceFile.find(".EventualMessageSend").click(function (e) {
      ctrl.onToggleMessageSendBreakpoint(e)
      })
  }

  // highlight current node
  var ssId = data.stack[0].sourceSection.id;
  var sourceId = data.stack[0].sourceSection.sourceId;
  var ss = document.getElementById(ssId);
  $(ss).addClass("DbgCurrentNode");

  this.currentDomNode   = ss;
  this.currentSectionId = ssId;
  this.showSourceById(sourceId);

  // scroll to the statement
  $('html, body').animate({
    scrollTop: $(ss).offset().top
  }, 300);
};

View.prototype.showSourceById = function (sourceId) {
  if (this.getActiveSourceId() !== sourceId) {
    $(document.getElementById("a" + sourceId)).tab('show');
  }
};

View.prototype.getActiveSourceId = function () {
  return $(".tab-pane.active").attr("id");
};

View.prototype.ensureBreakpointListEntry = function (breakpoint) {
  if (breakpoint.checkbox !== null) {
    return;
  }

  var bpId = "bp:" + breakpoint.source.id + ":" + breakpoint.getId();
  var entry = nodeFromTemplate("breakpoint-tpl");
  entry.setAttribute("id", bpId);

  var tds = $(entry).find("td");
  tds[0].innerHTML = breakpoint.source.name;
  tds[1].innerHTML = breakpoint.getId();

  breakpoint.checkbox = $(entry).find("input");
  breakpoint.checkbox.attr("id", bpId + "chk");

  var list = document.getElementById("breakpoint-list");
  list.appendChild(entry);
};

View.prototype.updateBreakpoint = function (breakpoint, highlightElem,
                                            highlightClass) {
  this.ensureBreakpointListEntry(breakpoint);
  var enabled = breakpoint.isEnabled();

  breakpoint.checkbox.prop('checked', enabled);
  if (enabled) {
    highlightElem.addClass(highlightClass);
  } else {
    highlightElem.removeClass(highlightClass);
  }
};

View.prototype.updateLineBreakpoint = function (bp) {
  var lineNumSpan = $(bp.lineNumSpan);
  this.updateBreakpoint(bp, lineNumSpan, "breakpoint-active");
};

View.prototype.updateSendBreakpoint = function (bp) {
  var bpSpan = $("#" + bp.sectionId);
  this.updateBreakpoint(bp, bpSpan, "send-breakpoint-active");
};

View.prototype.lazyFindDebuggerButtons = function () {
  if (!this.debuggerButtonJQNodes) {
    this.debuggerButtonJQNodes = {};
    this.debuggerButtonJQNodes.resume = $(document.getElementById("dbg-btn-resume"));
    this.debuggerButtonJQNodes.pause  = $(document.getElementById("dbg-btn-pause"));
    this.debuggerButtonJQNodes.stop   = $(document.getElementById("dbg-btn-stop"));

    this.debuggerButtonJQNodes.stepInto = $(document.getElementById("dbg-btn-step-into"));
    this.debuggerButtonJQNodes.stepOver = $(document.getElementById("dbg-btn-step-over"));
    this.debuggerButtonJQNodes.return   = $(document.getElementById("dbg-btn-return"));
  }
};

View.prototype.switchDebuggerToSuspendedState = function () {
  this.lazyFindDebuggerButtons();

  this.debuggerButtonJQNodes.resume.removeClass("disabled");
  this.debuggerButtonJQNodes.pause.addClass("disabled");
  this.debuggerButtonJQNodes.stop.removeClass("disabled");

  this.debuggerButtonJQNodes.stepInto.removeClass("disabled");
  this.debuggerButtonJQNodes.stepOver.removeClass("disabled");
  this.debuggerButtonJQNodes.return.removeClass("disabled");
};

View.prototype.switchDebuggerToResumedState = function () {
  this.lazyFindDebuggerButtons();

  this.debuggerButtonJQNodes.resume.addClass("disabled");
  this.debuggerButtonJQNodes.pause.removeClass("disabled");
  this.debuggerButtonJQNodes.stop.removeClass("disabled");

  this.debuggerButtonJQNodes.stepInto.addClass("disabled");
  this.debuggerButtonJQNodes.stepOver.addClass("disabled");
  this.debuggerButtonJQNodes.return.addClass("disabled");
};

View.prototype.onContinueExecution = function () {
  this.switchDebuggerToResumedState();
  $(this.currentDomNode).removeClass("DbgCurrentNode");
};
