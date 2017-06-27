import {
  IdMap, Source, StackFrame, SourceMessage, TaggedSourceCoordinate,
  getSectionId
} from "./messages";
import { Breakpoint } from "./breakpoints";


export class Debugger {

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
  private sections: IdMap<TaggedSourceCoordinate>;

  private breakpoints: IdMap<IdMap<Breakpoint>>;

  constructor() {
    this.uriToSourceId = {};
    this.sources = {};
    this.sections = {};
    this.breakpoints = {};
  }

  public getSourceId(uri: string): string {
    if (!(uri in this.uriToSourceId)) {
      this.uriToSourceId[uri] = "s" + Object.keys(this.uriToSourceId).length;
    }
    return this.uriToSourceId[uri];
  }

  public getSource(id: string): Source {
    return this.sources[id];
  }

  public getSectionIdFromFrame(sourceId: string, frame: StackFrame) {
    const line = frame.line,
      column = frame.column,
      length = frame.length;

    return getSectionId(sourceId,
      { startLine: line, startColumn: column, charLength: length });
  }

  public addSource(msg: SourceMessage): Source {
    const s = msg.source;
    let id = this.getSourceId(s.uri);
    this.sources[id] = s;
    this.addSections(s);
    this.addMethods(s);
    return s;
  }

  public getSection(id: string): TaggedSourceCoordinate {
    return this.sections[id];
  }

  private addSections(s: Source) {
    let sId = this.getSourceId(s.uri);
    for (let sc of s.sections) {
      let id = getSectionId(sId, sc);
      this.sections[id] = sc;
    }
  }

  private addMethods(s: Source) {
    let sId = this.getSourceId(s.uri);
    for (let meth of s.methods) {
      let ssId = getSectionId(sId, meth.sourceSection);
      if (!(ssId in this.sections)) {
        this.sections[ssId] = <any> meth.sourceSection;
      }
    }
  }

  public getBreakpoint(source: Source, key: any, newBp: () => Breakpoint): Breakpoint {
    let sId = this.getSourceId(source.uri);
    if (!this.breakpoints[sId]) {
      this.breakpoints[sId] = {};
    }

    let bp: Breakpoint = this.breakpoints[sId][key];
    if (!bp) {
      bp = newBp();
      this.breakpoints[sId][key] = bp;
    }
    return bp;
  }

  public getEnabledBreakpoints(): Breakpoint[] {
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

  public getEnabledBreakpointsForSource(sourceUri: string): Breakpoint[] {
    const bps = [];
    const lines = this.breakpoints[sourceUri];
    for (const line in lines) {
      const bp = lines[line];
      if (bp.isEnabled()) {
        bps.push(bp);
      }
    }
    return bps;
  }
}
