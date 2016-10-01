/* jshint -W097 */
"use strict";

import {Breakpoint, IdMap, Source, SourceSection, Method,
  SourceMessage} from './messages';

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

export class Debugger {
  private suspended: boolean;
  private lastSuspendEventId?: string;

  private sourceObjects:  IdMap<Source>;
  private sectionObjects: IdMap<SourceSection>;
  private methods:        IdMap<IdMap<Method>>;
  private breakpoints:    IdMap<IdMap<Breakpoint>>;

  constructor() {
    this.suspended = false;
    this.lastSuspendEventId = null;
    this.sourceObjects  = {};
    this.sectionObjects = {};
    this.methods        = {};
    this.breakpoints    = {};
  }

  getSource(id): Source {
    for (var fileName in this.sourceObjects) {
      if (this.sourceObjects[fileName].id === id) {
        return this.sourceObjects[fileName];
      }
    }
    return null;
  }

  getSection(id): SourceSection {
    return this.sectionObjects[id];
  }

  addSources(msg: SourceMessage) {
    for (var sId in msg.sources) {
      this.sourceObjects[msg.sources[sId].name] = msg.sources[sId];
    }
  }

  addSections(msg: SourceMessage) {
    for (let ssId in msg.sections) {
      this.sectionObjects[ssId] = msg.sections[ssId];
    }
  }

  addMethods(msg: SourceMessage) {
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
  }

  getMethods(sourceId) {
    return this.methods[sourceId];
  }

  getBreakpoint(source, key, newBp): Breakpoint {
    if (!this.breakpoints[source.name]) {
      this.breakpoints[source.name] = {};
    }

    var bp = this.breakpoints[source.name][key];
    if (!bp) {
      bp = newBp(source);
      this.breakpoints[source.name][key] = bp;
    }
    return bp;
  }

  getEnabledBreakpoints(): Breakpoint[] {
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
  }

  getEnabledBreakpointsForSource(sourceName: string): Breakpoint[] {
    var bps = [];
    var lines = this.breakpoints[sourceName];
    for (var line in lines) {
      var bp = lines[line];
      if (bp.isEnabled()) {
        bps.push(bp);
      }
    }
    return bps;
  }

  setSuspended(eventId) {
    console.assert(!this.suspended);
    this.suspended = true;
    this.lastSuspendEventId = eventId;
  }

  setResumed() {
    console.assert(this.suspended);
    this.suspended = false;
  }
}
