
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

export type Message = SourceMessage | InitializationResponse | ProgramInfoResponse |
  SymbolMessage | UpdateSourceSections | StoppedMessage |
  StackTraceResponse | ScopesResponse | VariablesResponse;

export interface SourceMessage {
  type:   "source";
  source: Source;
}

export type StoppedReason = "step" | "breakpoint" | "exception" | "pause";

/** The different types of active entities supported by the system. */
export enum ActivityType {}

/** The different kind of concurrency related entities, active, as well as
    passive, which are supported by the system. */
export enum EntityType {}

export interface EntityProperties {
  id:      number;
  origin?: FullSourceCoordinate;
  creationScope: number;  /// was causal message
}

export interface Entity extends EntityProperties {
  type:    EntityType;
}

export interface Activity extends EntityProperties {
  name:    string;
  type:    ActivityType;
  running: boolean;
}

export interface StoppedMessage {
  type: "StoppedMessage";

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
  type: "SymbolMessage";
  symbols: string[];
  ids:   number[];
  msgNumber: number;
}

export type BreakpointData = LineBreakpointData | SectionBreakpointData;

export interface AbstractBreakpointData {
  enabled:   boolean;
}

export interface LineBreakpointData extends AbstractBreakpointData {
  type: "LineBreakpoint";
  sourceUri: string;
  line:      number;
}

export interface SectionBreakpointData extends AbstractBreakpointData {
  type:  "SectionBreakpoint";
  coord: FullSourceCoordinate;  /** Source section to which breakpoint is applied. */
  bpType: string;               /** Name of the specific breakpoint type. */
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
    column: number, length: number, type: string,
    enabled: boolean) {
  let breakpoint: SectionBreakpointData = {
    type: "SectionBreakpoint",
    enabled: enabled,
    bpType: type,
    coord: {
      uri:         sourceUri,
      startLine:   line,
      startColumn: column,
      charLength:  length }};

  return breakpoint;
}

export type Respond = InitializeConnection | UpdateBreakpoint |
  StepMessage | StackTraceRequest | ScopesRequest | VariablesRequest |
  ProgramInfoRequest | TraceDataRequest;

export interface InitializeConnection {
  action: "InitializeConnection";
  breakpoints: BreakpointData[];
}

export interface BreakpointType {
  name:         string;    /** Identifier used for communication. */
  label:        string;    /** Label to use for display purposes. */
  applicableTo: string[];  /** Set of source section tags, for which the breakpoint is applicable. */
}

export interface SteppingType {
  name:  string;    /** Identifier used for communication. */
  label: string;    /** Label to use for display purposes. */
  group: string;    /** Group Label. */
  icon:  string;    /** Id of an icon known by the frontend. */
  applicableTo?: string[];  /** The source section tags this stepping operation applies to. If empty, it applies unconditionally. */
  forActivities?: ActivityType[]; /** Ids of the activities this stepping operation applies to. If empty, it applies unconditionally. */
  inScope: EntityType[]; /** Ids of the entities, which need to be in dynamic scope so this stepping type applies. If empty, it applies unconditionally. */
}

export interface EntityDef {
  id:         number;
  creation:   number;
  completion: number;
  label:      string;
  marker?:    string;
}

export interface ServerCapabilities {
  activityTypes:   EntityDef[];
  entityTypes:     EntityDef[];
  breakpointTypes: BreakpointType[];
  steppingTypes:   SteppingType[];
}

export interface InitializationResponse {
  type: "InitializationResponse";
  capabilities: ServerCapabilities;
}

export interface ProgramInfoRequest {
  action: "ProgramInfoRequest";
}

export interface ProgramInfoResponse {
  type: "ProgramInfoResponse";
  args: String[];
}

export interface TraceDataRequest {
  action: "TraceDataRequest";
}

interface UpdateBreakpoint {
  action: "updateBreakpoint";
  breakpoint: BreakpointData;
}

export interface StepMessage {
  action: "StepMessage";

  /** Id of the suspended activity. */
  activityId: number;

  /** Name of the stepping operation requested. */
  step: string;
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
  activityId:  number;
  stackFrames: StackFrame[];
  totalFrames: number;
  requestId:   number;
  concurrentEntityScopes?: EntityType[];
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
  variablesReference: number;
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
  variablesReference: number;
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

