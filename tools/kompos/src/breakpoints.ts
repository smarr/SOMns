import {Source, SourceCoordinate, AbstractBreakpointData, LineBreakpointData,
  SectionBreakpointData, SectionBreakpointType,
  createLineBreakpointData, createSectionBreakpointData} from "./messages";
import {getLineId} from "./view";

export type Breakpoint = LineBreakpoint | MessageBreakpoint;

export function getBreakpointId(sectionId: string, bpType: SectionBreakpointType) {
  return sectionId + "-" + bpType;
}

abstract class AbstractBreakpoint<T extends AbstractBreakpointData> {
  readonly data: T;
  checkbox: any;
  readonly source: Source;

  constructor(data: T, source: Source) {
    this.data     = data;
    this.checkbox = null;
    this.source   = source;
  }

  /**
   * @return a unique id for the breakpoint, to be used in the view as HTML id
   *         for the entry in the list
   */
  getListEntryId() {
    return "bp-";
  }

  abstract getSourceElementClass();

  toggle() {
    this.data.enabled = !this.data.enabled;
  }

  isEnabled() {
    return this.data.enabled;
  }
}

export class LineBreakpoint extends AbstractBreakpoint<LineBreakpointData> {
  readonly sourceId: string;

  constructor(data: LineBreakpointData, source: Source, sourceId: string) {
    super(data, source);
    this.sourceId = sourceId;
  }

  public getListEntryId(): string {
    return super.getListEntryId() + getLineId(this.data.line, this.sourceId);
  }

  public getSourceElementClass() {
    return getLineId(this.data.line, this.sourceId);
  }
}

export class MessageBreakpoint extends AbstractBreakpoint<SectionBreakpointData> {
  readonly sectionId: string;

  constructor(data: SectionBreakpointData, source: Source, sectionId: string) {
    super(data, source);
    this.sectionId = sectionId;
  }

  public getListEntryId(): string {
    return super.getListEntryId() + getBreakpointId(this.sectionId, this.data.type);
  }

  public getSourceElementClass() {
    return this.sectionId;
  }
}

export function createLineBreakpoint(source: Source, sourceId: string,
    line: number) {
  return new LineBreakpoint(createLineBreakpointData(source.uri, line, false),
    source, sourceId);
}

export function createMsgBreakpoint(source: Source,
    sourceSection: SourceCoordinate, sectionId: string,
    type: SectionBreakpointType) {
  return new MessageBreakpoint(
    createSectionBreakpointData(source.uri, sourceSection.startLine,
      sourceSection.startColumn, sourceSection.charLength, type, false),
    source, sectionId);
}
