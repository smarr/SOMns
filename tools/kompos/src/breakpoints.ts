import {
  Source, SourceCoordinate, AbstractBreakpointData, LineBreakpointData,
  SectionBreakpointData,
  createLineBreakpointData, createSectionBreakpointData
} from "./messages";
import { getLineId } from "./view";

export type Breakpoint = LineBreakpoint | SectionBreakpoint;

export function getBreakpointId(sectionId: string, bpType: string) {
  return sectionId + "-" + bpType;
}

abstract class AbstractBreakpoint<T extends AbstractBreakpointData> {
  public readonly data: T;
  public checkbox: any;
  public readonly source: Source;

  constructor(data: T, source: Source) {
    this.data = data;
    this.checkbox = null;
    this.source = source;
  }

  /**
   * @return a unique id for the breakpoint, to be used in the view as HTML id
   *         for the entry in the list
   */
  public getListEntryId() {
    return "bp-";
  }

  public abstract getSourceElementClass();

  public toggle() {
    this.data.enabled = !this.data.enabled;
  }

  public isEnabled() {
    return this.data.enabled;
  }
}

export class LineBreakpoint extends AbstractBreakpoint<LineBreakpointData> {
  private readonly sourceId: string;

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

export class SectionBreakpoint extends AbstractBreakpoint<SectionBreakpointData> {
  private readonly sectionId: string;

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

export function createSectionBreakpoint(source: Source,
  sourceSection: SourceCoordinate, sectionId: string, type: string) {
  return new SectionBreakpoint(
    createSectionBreakpointData(source.uri, sourceSection.startLine,
      sourceSection.startColumn, sourceSection.charLength, type, false),
    source, sectionId);
}
