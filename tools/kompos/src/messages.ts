
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

export type BreakpointData = LineBreakpointData | SendBreakpointData | AsyncMethodRcvBreakpointData;

interface AbstractBreakpointData {
  sourceUri: string;
  enabled:   boolean;
}

export interface LineBreakpointData extends AbstractBreakpointData {
  type: "lineBreakpoint";
  line: number;
}

export interface SendBreakpointData extends AbstractBreakpointData {
  type:        "sendBreakpoint";
  sectionId:   string;
  startLine:   number;
  startColumn: number;
  charLength:  number;
  role:        SendBreakpointType;
}

export interface AsyncMethodRcvBreakpointData extends AbstractBreakpointData {
  type:        "asyncMsgRcvBreakpoint";
  sectionId:   string;
  startLine:   number;
  startColumn: number;
  charLength:  number;
}

export type Breakpoint = LineBreakpoint | SendBreakpoint | AsyncMethodRcvBreakpoint;

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

export type SendBreakpointType = "receiver" | "sender";

export class SendBreakpoint extends AbstractBreakpoint<SendBreakpointData> {
  constructor(data: SendBreakpointData, source: Source) {
    super(data, source);
  }

  getId(): string {
    return this.data.sectionId;
  }
}

// TODO: refactor protocol, and just include a simple source section here
export class AsyncMethodRcvBreakpoint extends AbstractBreakpoint<AsyncMethodRcvBreakpointData> {
  constructor(data: AsyncMethodRcvBreakpointData, source: Source) {
    super(data, source);
  }

  getId(): string {
    return this.data.sectionId + ":async-rcv";
  }
}

export function createLineBreakpoint(source: Source, line: number, clickedSpan) {
  return new LineBreakpoint({
    type: "lineBreakpoint", line: line, sourceUri: source.uri, enabled: false},
    source, clickedSpan);
}

export function createSendBreakpoint(source: Source,
    sourceSection: SourceSection, role: SendBreakpointType) {
  return new SendBreakpoint({
    type: "sendBreakpoint", sourceUri: source.uri, enabled: false,
    sectionId:   sourceSection.id,
    startLine:   sourceSection.line,
    startColumn: sourceSection.column,
    charLength:  sourceSection.length,
    role: role
  }, source);
}

export function createAsyncMethodRcvBreakpoint(source: Source,
    sourceSection: SourceSection) {
  return new AsyncMethodRcvBreakpoint({
    type: "asyncMsgRcvBreakpoint", sourceUri: source.uri, enabled: false,
    sectionId:   sourceSection.id,
    startLine:   sourceSection.line,
    startColumn: sourceSection.column,
    charLength:  sourceSection.length
  }, source);
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
