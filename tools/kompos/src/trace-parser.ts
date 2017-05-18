import { ServerCapabilities, EntityDef, ActivityType } from "./messages";
import { ExecutionData, RawSourceCoordinate, RawActivity } from "./execution-data";

enum TraceRecords {
  ActivityCreation,
  ActivityCompletion,
  DynamicScopeStart,
  DynamicScopeEnd,
  PassiveEntityCreation,
  PassiveEntityCompletion,
  SendOp,
  ReceiveOp,
  ImplThread,
  ImplThreadCurrentActivity
}

const SOURCE_SECTION_SIZE = 8;

const IMPL_THREAD_MARKER = 20;
const IMPL_THREAD_CURRENT_ACTIVITY_MARKER = 21;

const RECORD_SIZE = {
  ActivityCreation   : 11 + SOURCE_SECTION_SIZE,
  ActivityCompletion : 1,
  DynamicScopeStart  : 9 + SOURCE_SECTION_SIZE,
  DynamicScopeEnd    : 1,
  PassiveEntityCreation   : 9 + SOURCE_SECTION_SIZE,
  PassiveEntityCompletion : undefined,
  SendOp     : 17,
  ReceiveOp  : 9,
  ImplThread : 9,
  ImplThreadCurrentActivity: 13
};

const SHIFT_HIGH_INT = 4294967296;
const MAX_SAFE_HIGH_BITS = 53 - 32;
const MAX_SAFE_HIGH_VAL  = (1 << MAX_SAFE_HIGH_BITS) - 1;

export class TraceParser {
  private readonly parseTable: TraceRecords[];
  private readonly activityType: ActivityType[];
  private readonly serverCapabilities: ServerCapabilities;
  private readonly execData: ExecutionData;

  constructor(serverCapabilities: ServerCapabilities, execData: ExecutionData) {
    this.serverCapabilities = serverCapabilities;
    this.parseTable   = this.createParseTable();
    this.activityType = this.createActivityMap();
    this.execData = execData;
  }

  private setInTable(parseTable: TraceRecords[], entities:  EntityDef[],
      creation: TraceRecords, completion: TraceRecords) {
    for (const entityType of entities) {
      if (entityType.creation) {
        parseTable[entityType.creation] = creation;
      }

      if (entityType.completion) {
        parseTable[entityType.completion] = completion;
      }
    }
  }

  private setOpsInTable(parseTable: TraceRecords[], ops, op: TraceRecords) {
    for (const opType of ops) {
      parseTable[opType.marker] = op;
    }
  }

  private createParseTable() {
    const parseTable = [];

    this.setInTable(parseTable, this.serverCapabilities.activities,
      TraceRecords.ActivityCreation, TraceRecords.ActivityCompletion);
    this.setInTable(parseTable, this.serverCapabilities.dynamicScopes,
      TraceRecords.DynamicScopeStart, TraceRecords.DynamicScopeEnd);
    this.setInTable(parseTable, this.serverCapabilities.passiveEntities,
      TraceRecords.PassiveEntityCreation, TraceRecords.PassiveEntityCompletion);

    this.setOpsInTable(parseTable, this.serverCapabilities.sendOps, TraceRecords.SendOp);
    this.setOpsInTable(parseTable, this.serverCapabilities.receiveOps, TraceRecords.ReceiveOp);

    console.assert(this.serverCapabilities.activityParseData.creationSize       === RECORD_SIZE.ActivityCreation);
    console.assert(this.serverCapabilities.activityParseData.completionSize     === RECORD_SIZE.ActivityCompletion);
    console.assert(this.serverCapabilities.passiveEntityParseData.creationSize  === RECORD_SIZE.PassiveEntityCreation);
    console.assert(this.serverCapabilities.passiveEntityParseData.completionSize === RECORD_SIZE.PassiveEntityCompletion);
    console.assert(this.serverCapabilities.dynamicScopeParseData.creationSize   === RECORD_SIZE.DynamicScopeStart);
    console.assert(this.serverCapabilities.dynamicScopeParseData.completionSize === RECORD_SIZE.DynamicScopeEnd);
    console.assert(this.serverCapabilities.sendReceiveParseData.creationSize    === RECORD_SIZE.SendOp);
    console.assert(this.serverCapabilities.sendReceiveParseData.completionSize  === RECORD_SIZE.ReceiveOp);

    console.assert(this.serverCapabilities.implementationData[0].marker === IMPL_THREAD_MARKER);
    console.assert(this.serverCapabilities.implementationData[0].size === RECORD_SIZE.ImplThread);
    console.assert(this.serverCapabilities.implementationData[1].marker === IMPL_THREAD_CURRENT_ACTIVITY_MARKER);
    console.assert(this.serverCapabilities.implementationData[1].size === RECORD_SIZE.ImplThreadCurrentActivity);

    parseTable[IMPL_THREAD_MARKER] = TraceRecords.ImplThread;
    parseTable[IMPL_THREAD_CURRENT_ACTIVITY_MARKER] = TraceRecords.ImplThreadCurrentActivity;

    return parseTable;
  }

  private createActivityMap() {
    const result: ActivityType[] = [];
    for (const actT of this.serverCapabilities.activities) {
      if (actT.creation) {
        result[actT.creation] = actT.id;
      }
    }
    return result;
  }

  /** Read a long within JS int range */
  private readLong(d: DataView, offset: number) {
    const high = d.getUint32(offset);
    console.assert(high <= MAX_SAFE_HIGH_VAL, "expected 53bit, but read high int as: " + high);
    return high * SHIFT_HIGH_INT + d.getUint32(offset + 4);
  }

  private readSourceSection(data: DataView, i: number): RawSourceCoordinate {
    const fileId:    number = data.getUint16(i);
    const startLine: number = data.getUint16(i + 2);
    const startCol:  number = data.getUint16(i + 4);
    const charLen:   number = data.getUint16(i + 6);
    return new RawSourceCoordinate(fileId, charLen, startLine, startCol);
  }

  private readActivityCreation(i: number, data: DataView,
      currentActivityId: number, currentScopeId: number) {
    const marker = data.getUint8(i);
    const activityId = this.readLong(data, i + 1);
    const symbolId   = data.getUint16(i + 9);
    const sourceSection = this.readSourceSection(data, i + 11);
    this.execData.addRawActivity(new RawActivity(
      this.activityType[marker], activityId, symbolId, sourceSection,
      currentActivityId, currentScopeId));
    return i + RECORD_SIZE.ActivityCreation;
  }

  public parseTrace(data: DataView) {
    let i = data.byteOffset;
    console.assert(i === 0);

    let currentActivityId = null;
    let currentScopeId = null;
    let currentImplThreadId = null;
    let currentThreadLocalBufferId = null;

    while (i < data.byteLength) {
      const marker = data.getUint8(i);
      switch (this.parseTable[marker]) {
        case TraceRecords.ActivityCreation:
          i += this.readActivityCreation(i, data, currentActivityId, currentScopeId);
          break;
        case TraceRecords.ActivityCompletion:
          throw new Error("Not Yet Implemented");
        case TraceRecords.DynamicScopeStart:
          throw new Error("Not Yet Implemented");
        case TraceRecords.DynamicScopeEnd:
          throw new Error("Not Yet Implemented");
        case TraceRecords.PassiveEntityCreation:
          throw new Error("Not Yet Implemented");
        case TraceRecords.PassiveEntityCompletion:
          throw new Error("Not Yet Implemented");
        case TraceRecords.SendOp:
          throw new Error("Not Yet Implemented");
        case TraceRecords.ReceiveOp:
          throw new Error("Not Yet Implemented");
        case TraceRecords.ImplThread: {
          currentImplThreadId = this.readLong(data, i + 1);
          i += RECORD_SIZE.ImplThread;
          break;
        }
        case TraceRecords.ImplThreadCurrentActivity: {
          currentActivityId = this.readLong(data, i + 1);
          currentThreadLocalBufferId = data.getUint32(i + 9);
          i += RECORD_SIZE.ImplThreadCurrentActivity;
          break;
        }
        default:
          throw new Error("Unexpected marker in trace: " + marker);
      }
    }
  }
}
