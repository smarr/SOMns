/* jshint -W097 */
"use strict";

import {Breakpoint} from './messages';
import {VmConnection} from './vm-connection';
import {Controller} from './controller';
import {View} from './view';

var data = {};

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

export function dbgLog(msg) {
  var tzOffset = (new Date()).getTimezoneOffset() * 60000; // offset in milliseconds
  var localISOTime = (new Date(Date.now() - tzOffset)).toISOString().slice(0,-1);

  $("#debugger-log").html(localISOTime + ": " + msg + "<br/>" + $("#debugger-log").html());
}

export function Debugger() {
  this.suspended = false;
  this.lastSuspendEventId = null;
  this.sourceObjects  = {};
  this.sectionObjects = {};
  this.methods        = {};
  this.breakpoints    = {};
}

Debugger.prototype.getSource = function (id) {
  for (var fileName in this.sourceObjects) {
    if (this.sourceObjects[fileName].id === id) {
      return this.sourceObjects[fileName];
    }
  }
  return null;
};

Debugger.prototype.getSection = function (id) {
  return this.sectionObjects[id];
};

Debugger.prototype.addSources = function (msg) {
  for (var sId in msg.sources) {
    this.sourceObjects[msg.sources[sId].name] = msg.sources[sId];
  }
};

Debugger.prototype.addSections = function (msg) {
  for (let ssId in msg.sections) {
    this.sectionObjects[ssId] = msg.sections[ssId];
  }
};

Debugger.prototype.addMethods = function (msg) {
  for (let k in msg.methods) {
    let meth = msg.methods[k];
    let sId  = meth.sourceSection.sourceId;
    let ssId = meth.sourceSection.id;
    if (!this.methods[sId]) {
      this.methods[sId] = {};
    }
    this.methods[sId][ssId] = meth;

    // also register the source section for later lookups
    this.sectionObjects[ssId] = meth.sourceSection;
  }
};

Debugger.prototype.getMethods = function (sourceId) {
  return this.methods[sourceId];
};

Debugger.prototype.getBreakpoint = function (source, key, newBp): Breakpoint {
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
