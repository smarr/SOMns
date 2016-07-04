/* jshint -W097 */
"use strict";

var data = {};

/**
 * Dummy definition for IDE support. These objects are
 * generally read from the web socket connection.
 * @constructor
 */
function SourceSection() {
  this.firstIndex = 0;
  this.sourceText = "";
  this.shortName  = "";
}

/**
 * Dummy definition for IDE support. These objects are
 * generally read from the web socket connection.
 * @constructor
 */
function InvocationProfile() {
  this.invocations = 0;
  this.somTypes = [];
}

/**
 * Dummy definition for IDE support. These objects are
 * generally read from the web socket connection.
 * @constructor
 */
function CountingProfile() {
  this.count = 0;
}

function Actor(id, name, typeName) {
  this.id       = id;
  this.name     = name;
  this.typeName = typeName;
}

function MessageHistory(messages, actors) {
  this.messages = messages; // per actor
  this.actors   = actors;
}

function Message(id, sender, receiver) {
  this.id       = id;
  this.sender   = sender;
  this.receiver = receiver;
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

function Breakpoint(source) {
  this.type     = "abstract-breakpoint";
  this.source   = source;
  this.enabled  = false;
  this.checkbox = null;
}

Breakpoint.prototype.toggle = function () {
  this.enabled = !this.enabled;
};

Breakpoint.prototype.isEnabled = function () {
  return this.enabled;
};

/**
 * @returns a unique id (for the corresponding source)
 */
Breakpoint.prototype.getId = function () {
  return null;
};

/**
 * @returns object for JSON serialization
 */
Breakpoint.prototype.toJsonObj = function () {
  return {
    type:       this.type,
    sourceId:   this.source.id,
    sourceName: this.source.name,
    enabled:    this.isEnabled()
  };
};

function LineBreakpoint(source, line, lineNumSpan) {
  Breakpoint.call(this, source);

  this.type        = "lineBreakpoint";
  this.line        = line;
  this.lineNumSpan = lineNumSpan;
}
LineBreakpoint.prototype = Object.create(Breakpoint.prototype);

LineBreakpoint.prototype.getId = function () {
  return this.line;
};

LineBreakpoint.prototype.toJsonObj = function () {
  var obj = Breakpoint.prototype.toJsonObj.call(this);
  obj.line = this.line;
  return obj;
};

function SendBreakpoint(source, sectionId) {
  Breakpoint.call(this, source);

  this.type = "sendBreakpoint";
  this.sectionId = sectionId;
}
SendBreakpoint.prototype = Object.create(Breakpoint.prototype);

SendBreakpoint.prototype.getId = function () {
  return this.sectionId;
};

SendBreakpoint.prototype.toJsonObj = function () {
  var obj = Breakpoint.prototype.toJsonObj.call(this);
  obj.sectionId = this.sectionId;
  return obj;
};

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
  this.suspended = false;
  this.lastSuspendEventId = null;
  this.sourceObjects = {};
  this.breakpoints = {};
}

Debugger.prototype.getSource = function (id) {
  for (var fileName in this.sourceObjects) {
    if (this.sourceObjects[fileName].id === id) {
      return this.sourceObjects[fileName];
    }
  }
  return null;
};

Debugger.prototype.addSources = function (msg) {
  for (var sId in msg.sources) {
    this.sourceObjects[msg.sources[sId].name] = msg.sources[sId];
  }
};

Debugger.prototype.getBreakpoint = function (source, key, newBp) {
  if (!this.breakpoints[source.name]) {
    this.breakpoints[source.name] = {};
  }

  var bp = this.breakpoints[source.name][key];
  if (!bp) {
    bp = newBp(source);
    this.breakpoints[source.name][key] = bp;
  }
  return bp;
};

Debugger.prototype.getEnabledBreakpoints = function () {
  var bps = [];
  for (var sourceName in this.breakpoints) {
    var lines = this.breakpoints[sourceName];
    for (var line in lines) {
      var bp = lines[line];
      if (bp.isEnabled()) {
        bps.push(bp);
      }
    }
  }
  return bps;
};

Debugger.prototype.getEnabledBreakpointsForSource = function (sourceName) {
  var bps = [];
  var lines = this.breakpoints[sourceName];
  for (var line in lines) {
    var bp = lines[line];
    if (bp.isEnabled()) {
      bps.push(bp);
    }
  }
  return bps;
};

Debugger.prototype.setSuspended = function(eventId) {
  console.assert(!this.suspended);
  this.suspended = true;
  this.lastSuspendEventId = eventId;
};

Debugger.prototype.setResumed = function () {
  console.assert(this.suspended);
  this.suspended = false;
};


/* globals View, Controller, VmConnection */
var ctrl;

function init() {
  var view = new View(),
    vmConnection = new VmConnection(),
    dbg = new Debugger();
  ctrl = new Controller(dbg, view, vmConnection);
  ctrl.toggleConnection();

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
