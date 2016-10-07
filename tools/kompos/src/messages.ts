
export interface IdMap<T> {
  [key: string]: T;
}

export interface Source {
  sourceText: string;
  mimeType:   string;
  name:       string;
  uri:        string;
  sections:   TaggedSourceCoordinate[];
  methods:    Method[];
}

export function getSectionId(sourceId: string, section: SourceCoordinate) {
  return sourceId + ':' + section.startLine + ':' + section.startColumn + ':' +
    section.charLength;
}

// TODO: rename
export interface SourceCoordinate {
  charLength:       number;
  startLine:        number;
  startColumn:      number;
}

// TODO: rename
export interface FullSourceCoordinate extends SourceCoordinate {
  uri: string;
}

export interface TaggedSourceCoordinate extends SourceCoordinate {
  tags: string[];
}

export interface Method {
  name:          string;
  definition:    SourceCoordinate[];
  sourceSection: SourceCoordinate;
}

interface Frame {
  sourceSection: FullSourceCoordinate;
  methodName: string;
}

interface TopFrame {
  arguments: string[];
  slots:     IdMap<string>;
}

export type Message = SourceMessage | SuspendEventMessage |
  MessageHistoryMessage | UpdateSourceSections;

export interface SourceMessage {
  type:     "source";
  sources:  Source[];
}

export interface SuspendEventMessage {
  type:     "suspendEvent";
  
  /** id of SuspendEvent, to be recognized in backend. */
  id:        string;
  sourceUri: string;

  stack:    Frame[];
  topFrame: TopFrame;
}

export interface UpdateSourceSections {
  type: "UpdateSourceSections";
  updates: SourceInfo[];
}

export interface SourceInfo {
  sourceUri: string;
  sections:  TaggedSourceCoordinate[];
}

export interface MessageHistoryMessage {
  type: "messageHistory";
  messages: MessageData[];
  actors:   FarRefData[];
}

export interface MessageData {
  id:       string;
  senderId: string;
  targetId: string;
}

export interface FarRefData {
  id:       string;
  typeName: string;
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
  coord: FullSourceCoordinate;
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
   * @return a unique id for the breakpoint, to be used in the view as HTML id
   */
  getId() {
    return 'bp:';
  }

  toggle() {
    this.data.enabled = !this.data.enabled;
  }

  isEnabled() {
    return this.data.enabled;
  }
}

export class LineBreakpoint extends AbstractBreakpoint<LineBreakpointData> {
  readonly lineNumSpan: Element;
  readonly sourceId: string;

  constructor(data: LineBreakpointData, source: Source, sourceId: string,
      lineNumSpan: Element) {
    super(data, source);
    this.lineNumSpan = lineNumSpan;
    this.sourceId    = sourceId;
  }

  getId(): string {
    return super.getId() + this.sourceId + ':' + this.data.line;
  }
}

export class MessageBreakpoint extends AbstractBreakpoint<SectionBreakpointData> {
  readonly sectionId: string;

  constructor(data: SectionBreakpointData, source: Source, sectionId: string) {
    super(data, source);
    this.sectionId = sectionId;
  }

  getId(): string {
    return super.getId() + this.sectionId;
  }
}

export class AsyncMethodRcvBreakpoint extends AbstractBreakpoint<SectionBreakpointData> {
  readonly sectionId: string;

  constructor(data: SectionBreakpointData, source: Source, sectionId: string) {
    super(data, source);
    this.sectionId = sectionId;
  }

  getId(): string {
    return super.getId() + this.sectionId + ":async-rcv";
  }
}

export function createLineBreakpoint(source: Source, sourceId: string,
    line: number, clickedSpan: Element) {
  return new LineBreakpoint({
    type: "LineBreakpoint", line: line, sourceUri: source.uri, enabled: false},
    source, sourceId, clickedSpan);
}

export function createMsgBreakpoint(source: Source,
    sourceSection: SourceCoordinate, sectionId: string,
    type: SectionBreakpointType) {
  return new MessageBreakpoint({
    type: type,
    enabled: false,
    coord: {
      uri:         source.uri,
      startLine:   sourceSection.startLine,
      startColumn: sourceSection.startColumn,
      charLength:  sourceSection.charLength }},
    source, sectionId);
}

export function createAsyncMethodRcvBreakpoint(source: Source,
    sourceSection: SourceCoordinate, sectionId: string) {
  return new AsyncMethodRcvBreakpoint({
    type: "AsyncMessageReceiveBreakpoint", enabled: false,
    coord: {
      uri:         source.uri,
      startLine:   sourceSection.startLine,
      startColumn: sourceSection.startColumn,
      charLength:  sourceSection.charLength }},
    source, sectionId);
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
