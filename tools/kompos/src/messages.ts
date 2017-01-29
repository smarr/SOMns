
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
  return sourceId + ":" + section.startLine + ":" + section.startColumn + ":" +
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
  SymbolMessage | UpdateSourceSections | StoppedMessage |
  StackTraceResponse | ScopesResponse;

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

export type StoppedReason = "step" | "breakpoint" | "exception" | "pause";
export type ActivityType  = "Actor";

export interface StoppedMessage {
  type: "StoppedEvent";

  reason:            StoppedReason;
  activityId:        number;
  activityType:      ActivityType;
  text:              string;
  allThreadsStopped: boolean;
}

export interface UpdateSourceSections {
  type: "UpdateSourceSections";
  updates: SourceInfo[];
}

export interface SourceInfo {
  sourceUri: string;
  sections:  TaggedSourceCoordinate[];
}

export interface SymbolMessage {
  type: "symbolMessage";
  symbols: string[];
  ids:   number[];
}

export type BreakpointData = LineBreakpointData | SectionBreakpointData;

export type SectionBreakpointType = "MessageSenderBreakpoint" |
  "MessageReceiverBreakpoint" | "AsyncMessageReceiverBreakpoint" |
  "PromiseResolverBreakpoint" | "PromiseResolutionBreakpoint" |
  "ChannelOppositeBreakpoint";

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

export function createLineBreakpointData(sourceUri: string, line: number,
    enabled: boolean): LineBreakpointData {
  return {
    type: "LineBreakpoint",
    line:      line,
    sourceUri: sourceUri,
    enabled:   enabled};
}

export function createSectionBreakpointData(sourceUri: string, line: number,
    column: number, length: number, type: SectionBreakpointType,
    enabled: boolean) {
  let breakpoint: SectionBreakpointData = {
    type: type,
    enabled: enabled,
    coord: {
      uri:         sourceUri,
      startLine:   line,
      startColumn: column,
      charLength:  length }};

  return breakpoint;
}

export type Respond = InitialBreakpointsResponds | UpdateBreakpoint |
  StepMessage | StackTraceRequest | ScopesRequest | VariablesRequest;

export interface InitialBreakpointsResponds {
  action: "initialBreakpoints";
  breakpoints: BreakpointData[];

  /**
   * Use a VSCode-like debugger protocol.
   */
  debuggerProtocol: boolean;
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

export interface StackTraceRequest {
  action: "StackTraceRequest";

  activityId: number;
  startFrame: number;
  levels:     number;

  requestId:  number;
}

export interface StackFrame {
  /** Id for the frame, unique across all threads. */
  id: number;

  /** Name of the frame, typically a method name. */
  name: string;

  /** Optional source of the frame. */
  sourceUri: string;

  /** Optional, line within the file of the frame. */
  line: number;

  /** Optional, column within the line. */
  column: number;

  /** Optional, end line of the range covered by the stack frame. */
  endLine: number;

  /** Optional, end column of the range covered by the stack frame. */
  endColumn: number;

  /** Optional, number of characters in the range */
  length: number;
}

export interface StackTraceResponse {
  type: "StackTraceResponse";
  stackFrames: StackFrame[];
  totalFrames: number;
  requestId:   number;
}

export interface ScopesRequest {
  action: "ScopesRequest";
  requestId: number;
  frameId:   number;
}

export interface Scope {
  /** Name of the scope such as 'Arguments', 'Locals'. */
  name: string;

  /**
   * The variables of this scope can be retrieved by passing the value of
   * variablesReference to the VariablesRequest.
   */
  variablesReference: number;

  /** If true, the number of variables in this scope is large or expensive to retrieve. */
  expensive: boolean;
}

export interface ScopesResponse {
  type: "ScopesResponse";
  scopes:    Scope[];
  requestId: number;
}

export interface VariablesRequest {
  action: "VariablesRequest";
  requestId: number;

  /** Reference of the variable container/scope. */
  variablesReference: number;
}

export interface VariablesResponse {
  type: "VariablesResponse";
  variables: Variable[];
  requestId: number;
}

export interface Variable {
  name: string;
  value: string;

  /**
   * If variablesReference is > 0, the variable is structured and its
   * children can be retrieved by passing variablesReference to the
   * VariablesRequest.
   */
  variablesReference: number;

  /** The number of named child variables. */
  namedVariables: number;
  /** The number of indexed child variables. */
  indexedVariables: number;
}

