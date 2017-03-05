import {IdMap, Source, SourceCoordinate, SourceMessage, TaggedSourceCoordinate,
  Activity, getSectionId} from "./messages";
import {Breakpoint} from "./breakpoints";

export function isRelevant(sc: TaggedSourceCoordinate) {
  // use ExpressionBreakpoint tag, since it is implied by ChannelRead,
  // ChannelWrite, and EventualMessageSend
  return -1 !== sc.tags.indexOf("ExpressionBreakpoint");
}

export class Debugger {
  private suspendedActivities: boolean[];

  /** Current set of activities in the system. */
  private activities: Activity[];

  /**
   * Mapping Source URIs to id used for easy access, and for short unique ids to
   * be used by {@link getSectionId}.
   */
  private uriToSourceId: IdMap<string>;

  /**
   * Array of sources, indexed by id from {@link getSourceId}.
   */
  private sources: IdMap<Source>;

  /**
   * All source sections relevant for the debugger, indexed by {@link getSectionId}.
   */
  private sections: IdMap<SourceCoordinate>;

  private breakpoints: IdMap<IdMap<Breakpoint>>;

  constructor() {
    this.suspendedActivities = [];
    this.activities     = [];
    this.uriToSourceId  = {};
    this.sources        = {};
    this.sections       = {};
    this.breakpoints    = {};
  }

  getSourceId(uri: string): string {
    if (!(uri in this.uriToSourceId)) {
      this.uriToSourceId[uri] = "s" + Object.keys(this.uriToSourceId).length;
    }
    return this.uriToSourceId[uri];
  }

  getSource(id: string): Source {
    return this.sources[id];
  }

  addSource(msg: SourceMessage): Source {
    const s = msg.source;
    let id = this.getSourceId(s.uri);
    this.sources[id] = s;
    this.addSections(s);
    this.addMethods(s);
    return s;
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
    for (let sId in this.breakpoints) {
      for (let key in this.breakpoints[sId]) {
        let bp = this.breakpoints[sId][key];
        if (bp.isEnabled()) {
          bps.push(bp);
        }
      }
    }
    return bps;
  }

  getEnabledBreakpointsForSource(sourceName: string): Breakpoint[] {
    const bps = [];
    const lines = this.breakpoints[sourceName];
    for (const line in lines) {
      const bp = lines[line];
      if (bp.isEnabled()) {
        bps.push(bp);
      }
    }
    return bps;
  }

  setSuspended(activityId: number) {
    console.assert(!this.suspendedActivities[activityId]);
    this.suspendedActivities[activityId] = true;
  }

  setResumed(activityId: number) {
    this.suspendedActivities[activityId] = false;
  }

  isSuspended(activityId: number) {
    return this.suspendedActivities[activityId];
  }

  public addActivities(activities: Activity[]) {
    for (const a of activities) {
      if (this.activities[a.id] === undefined) {
        this.activities[a.id] = a;
      } else {
        console.assert(this.activities[a.id].name === a.name,
          "Don't expect names of activities to change over time");
      }
    }
  }
}
