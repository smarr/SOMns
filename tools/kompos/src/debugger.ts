import {Breakpoint, IdMap, Source, SourceCoordinate, SourceMessage,
  TaggedSourceCoordinate, getSectionId} from './messages';

export function isRelevant(sc: TaggedSourceCoordinate) {
  return -1 !== sc.tags.indexOf('EventualMessageSend');
}

export class Debugger {
  public lastSuspendEventId?: string;

  private suspended: boolean;
  
  /**
   * Mapping Source URIs to id used for easy access, and for short unique ids to
   * be used by {@link getSectionId}.
   */
  private uriToSourceId:  IdMap<string>;

  /**
   * Array of sources, indexed by id from {@link getSourceId}.
   */
  private sources:  IdMap<Source>;

  /**
   * All source sections relevant for the debugger, indexed by {@link getSectionId}.
   */
  private sections: IdMap<SourceCoordinate>;

  private breakpoints:    IdMap<Breakpoint>[];

  constructor() {
    this.suspended = false;
    this.lastSuspendEventId = null;
    this.uriToSourceId  = {};
    this.sources        = {};
    this.sections       = {};
    this.breakpoints    = [];
  }

  private getSourceId(uri: string): string {
    if (!(uri in this.uriToSourceId)) {
      this.uriToSourceId[uri] = 's' + Object.keys(this.uriToSourceId).length;
    }
    return this.uriToSourceId[uri];
  }

  getSource(id: string): Source {
    return this.sources[id];
  }

  addSources(msg: SourceMessage): IdMap<Source> {
    let newSources = {};
    for (let s of msg.sources) {
      let id = this.getSourceId(s.uri);
      this.sources[id] = s;
      this.addSections(s);
      this.addMethods(s);
      newSources[id] = s;
    }
    return newSources;
  }

  getSection(id: string): SourceCoordinate {
    return this.sections[id];
  }

  private addSections(s: Source) {
    let sId = this.getSourceId(s.uri);
    for (let sc of s.sections) {
      // Filter out all non-relevant source sections
      if (isRelevant(sc)) {
        let id = getSectionId(sId, sc);
        this.sections[id] = sc;
      }
    }
  }

  private addMethods(s: Source) {
    let sId = this.getSourceId(s.uri);
    for (let meth of s.methods) {
      let ssId = getSectionId(sId, meth.sourceSection);
      if (!(ssId in this.sections)) {
        this.sections[ssId] = meth.sourceSection;
      }
    }
  }

  getBreakpoint(source, key, newBp): Breakpoint {
    let sId = this.getSourceId(source.uri);
    if (!this.breakpoints[sId]) {
      this.breakpoints[sId] = {};
    }

    let bp: Breakpoint = this.breakpoints[sId][key];
    if (!bp) {
      bp = newBp(source);
      this.breakpoints[sId][key] = bp;
    }
    return bp;
  }

  getEnabledBreakpoints(): Breakpoint[] {
    let bps = [];
    for (let breakpoints of this.breakpoints) {
      for (let key in breakpoints) {
        let bp = breakpoints[key];
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
