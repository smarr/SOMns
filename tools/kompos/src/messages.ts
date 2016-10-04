
export interface IdMap<T> {
  [key: string]: T;
}

export interface Source {
  id:         string;
  sourceText: string;
  mimeType:   string;
  name:       string;
  uri:        string;
}

export interface SimpleSourceSection {
  firstIndex?: number;
  length:      number;
  line:        number;
  column:      number;
}

export interface SourceSection extends SimpleSourceSection {
  id:           string;
  description?: string;
  sourceId?:    string;
}

export interface Method {
  name:          string;
  definition:    SimpleSourceSection[];
  sourceSection: SourceSection;
}

interface Frame {
  sourceSection: SourceSection;
  methodName: string;
}

interface TopFrame {
  arguments: string[];
  slots:     IdMap<string>;
}

export type Message = SourceMessage | SuspendEventMessage | MessageHistoryMessage;

export interface SourceMessage {
  type:     "source";
  sources:  IdMap<Source>;
  sections: IdMap<SourceSection>;
  methods:  Method[];
}

export interface SuspendEventMessage {
  type:     "suspendEvent";
  sourceId: string;
  sections: SourceSection[];
  stack:    Frame[];
  topFrame: TopFrame;
  id: string;
}

export interface MessageHistoryMessage {
  type: "messageHistory";
  messageHistory: any; // TODO
}

export interface SourceCoordinate {
  uri:         string;
  startLine:   number;
  startColumn: number;
  charLength:  number;
}

export type BreakpointData = LineBreakpointData | SectionBreakpointData;

export type SectionBreakpointType = "MessageSenderBreakpoint" |
  "MessageReceiveBreakpoint" | "AsyncMessageReceiveBreakpoint";

interface AbstractBreakpointData {
  enabled:   boolean;
}

export interface LineBreakpointData extends AbstractBreakpointData {
  type: "LineBreakpoint";
  sourceUri: string;
  line:      number;
}

export interface SectionBreakpointData extends AbstractBreakpointData {
  type:  SectionBreakpointType;
  coord: SourceCoordinate;
}

export type Breakpoint = LineBreakpoint | MessageBreakpoint |
  AsyncMethodRcvBreakpoint;

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
   * @return a unique id (for the corresponding source)
   */
  abstract getId(): string;

  toggle() {
    this.data.enabled = !this.data.enabled;
  }

  isEnabled() {
    return this.data.enabled;
  }
}

export class LineBreakpoint extends AbstractBreakpoint<LineBreakpointData> {
  lineNumSpan?: any;

  constructor(data: LineBreakpointData, source: Source, lineNumSpan?: any) {
    super(data, source);
    this.lineNumSpan = lineNumSpan;
  }

  getId(): string {
    return '' + this.data.line;
  }
}

export class MessageBreakpoint extends AbstractBreakpoint<SectionBreakpointData> {
  readonly sectionId: string;

  constructor(data: SectionBreakpointData, source: Source, sectionId: string) {
    super(data, source);
    this.sectionId = sectionId;
  }

  getId(): string {
    return this.sectionId;
  }
}

export class AsyncMethodRcvBreakpoint extends AbstractBreakpoint<SectionBreakpointData> {
  readonly sectionId: string;

  constructor(data: SectionBreakpointData, source: Source, sectionId: string) {
    super(data, source);
    this.sectionId = sectionId;
  }

  getId(): string {
    return this.sectionId + ":async-rcv";
  }
}

export function createLineBreakpoint(source: Source, line: number, clickedSpan) {
  return new LineBreakpoint({
    type: "LineBreakpoint", line: line, sourceUri: source.uri, enabled: false},
    source, clickedSpan);
}

export function createMsgBreakpoint(source: Source,
    sourceSection: SourceSection, type: SectionBreakpointType) {
  return new MessageBreakpoint({
    type: type,
    enabled: false,
    coord: {
      uri:         source.uri,
      startLine:   sourceSection.line,
      startColumn: sourceSection.column,
      charLength:  sourceSection.length}},
    source, sourceSection.id);
}

export function createAsyncMethodRcvBreakpoint(source: Source,
    sourceSection: SourceSection) {
  return new AsyncMethodRcvBreakpoint({
    type: "AsyncMessageReceiveBreakpoint", enabled: false,
    coord: {
      uri:         source.uri,
      startLine:   sourceSection.line,
      startColumn: sourceSection.column,
      charLength:  sourceSection.length}},
    source, sourceSection.id);
}

export type Respond = InitialBreakpointsResponds | UpdateBreakpoint |
  StepMessage; 

export interface InitialBreakpointsResponds {
  action: "initialBreakpoints";
  breakpoints: BreakpointData[];
}

interface UpdateBreakpoint {
  action: "updateBreakpoint";
  breakpoint: BreakpointData;
}

export type StepType = "stepInto" | "stepOver" | "return" | "resume" | "stop"; 

export interface StepMessage {
  action: StepType;
  // TODO: should be renamed to suspendEventId
  /** Id of the corresponding suspend event. */
  suspendEvent: string;
}
