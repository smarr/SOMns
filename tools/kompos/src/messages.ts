
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

export interface Frame {
  sourceSection: FullSourceCoordinate;
  methodName: string;
}

export interface TopFrame {
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

export interface AbstractBreakpointData {
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
