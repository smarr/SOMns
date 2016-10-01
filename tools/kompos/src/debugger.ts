import {Breakpoint, IdMap, Source, SourceSection, Method,
  SourceMessage} from './messages';

export class Debugger {
  public lastSuspendEventId?: string;

  private suspended: boolean;
  
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

  getMethods(sourceId): IdMap<Method> {
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
    this.suspended = false;
  }
}
