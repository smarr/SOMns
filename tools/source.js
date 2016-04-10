var data = {};

function countNumberOfLines(str) {
  var cnt = 1;
  for (var i = 0; i < str.length; i++) {
    if (str[i] == "\n") {
      cnt += 1;
    }
  }
  return cnt;
}

function loadAndProcessFile(f) {
  var reader = new FileReader();
  reader.onload = (function(theFile) {

    function receivedFile(e) {
      var o = JSON.parse(e.target.result);
      data[theFile.name] = o;
      displaySources(o);
    }

    return receivedFile;
  })(f);

  reader.readAsText(f);
}

/**
 * @param {DragEvent} e
 */
function handleFileSelect(e) {
  e.stopPropagation();
  e.preventDefault();

  var files = e.dataTransfer.files; // FileList object.

  // files is a FileList of File objects. List some properties.
  var output = [];
  for (var i = 0; i < files.length; i++) {
    loadAndProcessFile(files[i]);
  }
}

function sourceToArray(source) {
  var arr = new Array(source.length);
  for (var i = 0; i < source.length; i++) {
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
    if (a.section.type != b.section.type) {
      if (a.section.type == Begin) {
        return -1;
      } else {
        return 1;
      }
    }

    if (a.section.length == b.section.length) {
      return 0;
    }

    if (a.section.length < b.section.length) {
      return (a.section.type == Begin) ? 1 : -1;
    } else {
      return (a.section.type == Begin) ? -1 : 1;
    }
  });

  var result = this.before.join("");
  result += this.char;
  result += this.after.join("");
  return result;
};

function ensureItIsAnnotation(arr, idx) {
  if (!(arr[idx] instanceof Annotation)) {
    arr[idx] = new Annotation(arr[idx]);
  }
  return arr[idx];
}

function annotateArray(arr, sourceId, sections) {
  for (var sId in sections) {
    var s = sections[sId];
    if (s.sourceId == sourceId) {
      var start = ensureItIsAnnotation(arr, s.firstIndex),
          end   = ensureItIsAnnotation(arr, s.firstIndex + s.length);
      start.before.push(new Begin(s));
      end.before.push(new End(s));
    }
  }
}

function arrayToString(arr) {
  var result = "";
  for (var i = 0; i < arr.length; i++) {
    result += arr[i].toString();
  }
  return result;
}

function nodeFromTemplate(tplId) {
  var tpl = document.getElementById(tplId);
  var result = tpl.cloneNode(true);
  result.removeAttribute("id");
  return result;
}

function createLineNumbers(cnt) {
  var result = "<span class='ln' onclick='toggleBreakpoint(1, this);'>1</span>";
  for (var i = 2; i <= cnt; i += 1) {
    result = result + "\n<span class='ln' onclick='toggleBreakpoint("+ i +", this);'>" + i + "</span>"
  }
  return result;
}

function showSource(s, sections, first) {
  var annotationArray = sourceToArray(s.sourceText);
  annotateArray(annotationArray, s.id, sections);

  var tabListEntry = nodeFromTemplate("tab-list-entry");
  if (first) {
    $(tabListEntry).addClass("active");
  }
  var aElem = $(tabListEntry).find("a");
  aElem.attr("href", "#" + s.id);
  aElem.text(s.shortName);
  $("#tabs").append(tabListEntry);


  var newFileElement = nodeFromTemplate("file");
  newFileElement.setAttribute("id", s.id);
  newFileElement.getElementsByClassName("line-numbers")[0].innerHTML = createLineNumbers(countNumberOfLines(s.sourceText));
  newFileElement.getElementsByClassName("source-file")[0].innerHTML = arrayToString(annotationArray);

  if (first) {
    $(newFileElement).addClass("active");
  }

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

function getActiveSourceId() {
  return $(".tab-pane.active").attr("id");
}

var firstSource = true; // global, to remember whether we showed already sources
var sourceObjects = {};
var breakpoints = {};

  function getSource(id) {
    for (var fname in sourceObjects) {
      if (sourceObjects[fname].id == id) {
        return sourceObjects[fname];
      }
    }
    return null;
  }



function Breakpoint(source, line, lineNumSpan) {
  this.source = source;
  this.line   = line;
  var bpId = "bp:" + source.id + ":" + line;
  this.entry = nodeFromTemplate("breakpoint-tpl");
  this.entry.setAttribute("id", bpId);

  var tds = $(this.entry).find("td");
  tds[0].innerHTML = source.shortName;
  tds[1].innerHTML = line;

  this.checkbox = $(this.entry).find("input");
  this.checkbox.attr("id", bpId + "chk");

  var list = document.getElementById("breakpoint-list");
  list.appendChild(this.entry);

  this.lineNumSpan = $(lineNumSpan);
  this.lineNumSpan.addClass("breakpoint-active");
}

Breakpoint.prototype.toggle = function () {
  var oldState = this.isEnabled();

  // flip checkbox in debugger view
  this.checkbox.prop('checked', !oldState);

  // flip marker in source view
  if (oldState) {
    $(this.lineNumSpan).removeClass("breakpoint-active");
  } else {
    $(this.lineNumSpan).addClass("breakpoint-active");
  }

  dbg.updateBreakpoint(this);
};

Breakpoint.prototype.isEnabled = function () {
  return this.checkbox.prop('checked');
};

function toggleBreakpoint(line, clickedSpan) {
  var sourceId = getActiveSourceId();
  var source   = getSource(sourceId);
  if (!breakpoints[source]) {
    breakpoints[source] = {};
  }
  if (!breakpoints[source][line]) {
    breakpoints[source][line] = new Breakpoint(source, line, clickedSpan);
    dbg.updateBreakpoint(breakpoints[source][line]);
  } else {
    breakpoints[source][line].toggle();
  }
}

function displaySources(o) {
  for (var sId in o.sources) {
    if (sourceObjects[o.sources[sId].name]) {
      continue;
    }
    showSource(o.sources[sId], o.sections, firstSource);
    sourceObjects[o.sources[sId].name] = o.sources[sId];
    firstSource = false;
  }

  for (var ssId in o.sections) {
    showSectionData(o.sections[ssId]);
  }

  showHistogramOfAccess();

  // TODO: remove: activate Mandelbrot tab
  $('.nav-tabs a[href="#s-2"]').tab('show');
}

function showSectionData(section) {
  if (section.data && section.data.methodInvocationProfile) {
    showInvocationProfile(section.data.methodInvocationProfile,
      document.getElementById(section.id));
  }

  if (section.data && (section.data.localReads != null || section.data.localWrites != null)) {
    if (section.data.localReads != null && section.data.localWrites != null) {
      throw "this is unexpected, adapt program";
    }
    showAccessProfile(section.data.localReads || section.data.localWrites,
      document.getElementById(section.id));
  }
}

function handleDragOver(evt) {
  evt.stopPropagation();
  evt.preventDefault();
  evt.dataTransfer.dropEffect = 'copy'; // Explicitly show this is a copy.
}

function dbgLog(msg) {
  var tzOffset = (new Date()).getTimezoneOffset() * 60000; // offset in milliseconds
  var localISOTime = (new Date(Date.now() - tzOffset)).toISOString().slice(0,-1);

  $("#debugger-log").html(localISOTime + ": " + msg + "<br/>" + $("#debugger-log").html());
}
function Debugger() {
  this.socket = connect();
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

function showVar(name, value, list) {
  var entry = nodeFromTemplate("frame-state-tpl");
  var t = $(entry).find("th");
  t.html(name);
  t = $(entry).find("td");
  t.html(value);
  list.appendChild(entry);
}

function displaySuspendEvent(data) {
  var list = document.getElementById("stack-frames");
  while (list.lastChild) {
    list.removeChild(list.lastChild)
  }

  for (var i = 0; i < data.stack.length; i++) {
    showFrame(data.stack[i], i, list);
  }

  list = document.getElementById("frame-state");
  while (list.lastChild) {
    list.removeChild(list.lastChild)
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
  }

  // highlight current node
  var ssId = data.stack[0].sourceSection.id;
  var ss = document.getElementById(ssId);
  $(ss).addClass("DbgCurrentNode");

  dbg.currentDomNode   = ss;
  dbg.currentSectionId = ssId;
  dbg.lastSuspendEventId = data.id;
}

function connect() {
  var socket = new WebSocket("ws://localhost:8889");
  socket.onopen = function(e) {
    dbgLog("[WS] open");
    $("#dbg-connect-btn").html("Connected");

  };
  socket.onclose = function(e) {
    dbgLog("[WS] close");
    $("#dbg-connect-btn").html("Reconnect");
  };
  socket.onerror   = function(e) {
    dbgLog("[WS] error");
  };
  socket.onmessage = function(e) {
    dbgLog("[WS] msg");
    var data = JSON.parse(e.data);

    switch (data.type) {
      case "source":
        displaySources(data);
        break;
      case "suspendEvent":
        displaySuspendEvent(data);
        break;
    }
  };
  return socket;
}

Debugger.prototype.connectBtn = function() {
  var CONNECTING = 0,
    OPEN = 1,
    CLOSING = 2,
    CLOSED = 3;

  if (this.socket.readyState == CLOSED) {
    // reconnect
    this.socket = connect();
  }

  this.currentSectionId   = null;
  this.currentDomNode     = null;
  this.lastSuspendEventId = null;
};

Debugger.prototype.resume = function() {
  this.socket.send("resume");
};
Debugger.prototype.pause = function() {
  this.socket.send("pause");
};
Debugger.prototype.stop = function() {
  this.socket.send("stop");
};

Debugger.prototype.stepInto = function() {
  $(this.currentDomNode).removeClass("DbgCurrentNode");
  this.socket.send(JSON.stringify({
    action:'stepInto',
    suspendEvent: this.lastSuspendEventId}));
};
Debugger.prototype.stepOver = function() {
  this.socket.send("stepOver");
};
Debugger.prototype.return = function() {
  this.socket.send("return");
};

Debugger.prototype.updateBreakpoint = function (breakpoint) {
  dbgLog("updateBreakpoint");
  this.socket.send(JSON.stringify({
    action:     "updateBreakpoint",
    sourceId:   breakpoint.source.id,
    sourceName: breakpoint.source.name,
    line:       breakpoint.line,
    enabled:    breakpoint.isEnabled()}));
};

function initializeDebugger() {
  dbg = new Debugger();
}

function init() {
  // Connect to debugger server
  initializeDebugger();

  // Init drag and drop
  var fileDrop = document.getElementById('file-drop');
  fileDrop.addEventListener('dragover', handleDragOver,   false);
  fileDrop.addEventListener('drop',     handleFileSelect, false);
}

function blobToFile(blob, name) {
  blob.lastModifiedDate = new Date();
  blob.name = name;
  return blob;
}

function getFileObjectFromPath(pathOrUrl, callback) {
  var request = new XMLHttpRequest();
  request.open("GET", pathOrUrl);
  request.responseType = "blob";
  request.addEventListener('load', function () {
    callback(blobToFile(request.response, pathOrUrl));
  });
  request.send();
}

function loadStandardFile() {
  getFileObjectFromPath("highlight.json",
    function(file) {
      loadAndProcessFile(file);
    });
}

var heatmapStyleNode = null;
function toggleHeatMap() {
  if (heatmapStyleNode == null) {
    heatmapStyleNode = $("#style-heatmap").detach();
  } else {
    $("#style-main").after(heatmapStyleNode);
  }
}
