import { EntityDef, ActivityType, EntityType, DynamicScopeType,
  PassiveEntityType, SendOpType, ReceiveOpType } from "./messages";
import { ExecutionData, RawSourceCoordinate, RawActivity, RawScope,
  RawPassiveEntity, RawSendOp, RawReceiveOp } from "./execution-data";
import { KomposMetaModel } from "./meta-model";

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
  private readonly typeCreation: EntityType[];
  private readonly sendOps:    SendOpType[];
  private readonly receiveOps: ReceiveOpType[];
  private readonly metaModel: KomposMetaModel;
  private readonly execData: ExecutionData;

  constructor(metaModel: KomposMetaModel, execData: ExecutionData) {
    this.metaModel    = metaModel;
    this.parseTable   = [];
    this.typeCreation = [];
    this.sendOps      = [];
    this.receiveOps   = [];

    this.initMetaData();
    this.execData = execData;
  }

  private setInTable(entities:  EntityDef[],
      creation: TraceRecords, completion: TraceRecords) {
    for (const entityType of entities) {
      if (entityType.creation) {
        this.parseTable[entityType.creation]   = creation;
        this.typeCreation[entityType.creation] = entityType.id;
      }

      if (entityType.completion) {
        this.parseTable[entityType.completion] = completion;
      }
    }
  }

  private setOpsInTable(ops, op: TraceRecords, idTable) {
    for (const opType of ops) {
      this.parseTable[opType.marker] = op;
      idTable[opType.marker] = opType.marker;
    }
  }

  private initMetaData() {
    this.setInTable(this.metaModel.serverCapabilities.activities,
      TraceRecords.ActivityCreation, TraceRecords.ActivityCompletion);
    this.setInTable(this.metaModel.serverCapabilities.dynamicScopes,
      TraceRecords.DynamicScopeStart, TraceRecords.DynamicScopeEnd);
    this.setInTable(this.metaModel.serverCapabilities.passiveEntities,
      TraceRecords.PassiveEntityCreation, TraceRecords.PassiveEntityCompletion);

    this.setOpsInTable(this.metaModel.serverCapabilities.sendOps, TraceRecords.SendOp, this.sendOps);
    this.setOpsInTable(this.metaModel.serverCapabilities.receiveOps, TraceRecords.ReceiveOp, this.receiveOps);

    console.assert(this.metaModel.serverCapabilities.activityParseData.creationSize       === RECORD_SIZE.ActivityCreation);
    console.assert(this.metaModel.serverCapabilities.activityParseData.completionSize     === RECORD_SIZE.ActivityCompletion);
    console.assert(this.metaModel.serverCapabilities.passiveEntityParseData.creationSize  === RECORD_SIZE.PassiveEntityCreation);
    console.assert(this.metaModel.serverCapabilities.passiveEntityParseData.completionSize === RECORD_SIZE.PassiveEntityCompletion);
    console.assert(this.metaModel.serverCapabilities.dynamicScopeParseData.creationSize   === RECORD_SIZE.DynamicScopeStart);
    console.assert(this.metaModel.serverCapabilities.dynamicScopeParseData.completionSize === RECORD_SIZE.DynamicScopeEnd);
    console.assert(this.metaModel.serverCapabilities.sendReceiveParseData.creationSize    === RECORD_SIZE.SendOp);
    console.assert(this.metaModel.serverCapabilities.sendReceiveParseData.completionSize  === RECORD_SIZE.ReceiveOp);

    console.assert(this.metaModel.serverCapabilities.implementationData[0].marker === IMPL_THREAD_MARKER);
    console.assert(this.metaModel.serverCapabilities.implementationData[0].size === RECORD_SIZE.ImplThread);
    console.assert(this.metaModel.serverCapabilities.implementationData[1].marker === IMPL_THREAD_CURRENT_ACTIVITY_MARKER);
    console.assert(this.metaModel.serverCapabilities.implementationData[1].size === RECORD_SIZE.ImplThreadCurrentActivity);

    this.parseTable[IMPL_THREAD_MARKER] = TraceRecords.ImplThread;
    this.parseTable[IMPL_THREAD_CURRENT_ACTIVITY_MARKER] = TraceRecords.ImplThreadCurrentActivity;
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
      <ActivityType> this.typeCreation[marker], activityId, symbolId,
      sourceSection, currentActivityId, currentScopeId));

    return i + RECORD_SIZE.ActivityCreation;
  }

  private readScopeStart(i: number, data: DataView,
      currentActivityId: number, currentScopeId: number) {
    const marker = data.getUint8(i);
    const id = this.readLong(data, i + 1);
    const source = this.readSourceSection(data, i + 9);

    this.execData.addRawScope(new RawScope(
      <DynamicScopeType> this.typeCreation[marker], id, source,
      currentActivityId, currentScopeId));

    return i + RECORD_SIZE.DynamicScopeStart;
  }

  private readEntityCreation(i: number, data: DataView,
      currentActivityId: number, currentScopeId: number) {
    const marker = data.getUint8(i);
    const id = this.readLong(data, i + 1);
    const source = this.readSourceSection(data, i + 9);

    this.execData.addRawPassiveEntity(new RawPassiveEntity(
      <PassiveEntityType> this.typeCreation[marker], id, source,
      currentActivityId, currentScopeId));

    return i + RECORD_SIZE.PassiveEntityCreation;
  }

  private readSendOp(i: number, data: DataView,
      currentActivityId: number, currentScopeId: number) {
    const marker = data.getUint8(i);
    const entityId = this.readLong(data, i + 1);
    const targetId = this.readLong(data, i + 9);

    this.execData.addRawSendOp(new RawSendOp(
      this.sendOps[marker], entityId, targetId, currentActivityId,
      currentScopeId));

    return i + RECORD_SIZE.SendOp;
  }

  private readReceiveOp(i: number, data: DataView,
      currentActivityId: number, currentScopeId: number) {
    const marker = data.getUint8(i);
    const sourceId = this.readLong(data, i + 1);

    this.execData.addRawReceiveOp(new RawReceiveOp(
      this.receiveOps[marker], sourceId, currentActivityId,
      currentScopeId));

    return i + RECORD_SIZE.ReceiveOp;
  }

  public parseTrace(data: DataView) {
    let i = data.byteOffset;
    console.assert(i === 0);

    let currentActivityId = null;
    let currentScopeId = null;
    let currentImplThreadId = null;
    let currentThreadLocalBufferId = null;

    let prevMarker = null;

    while (i < data.byteLength) {
      const marker = data.getUint8(i);
      switch (this.parseTable[marker]) {
        case TraceRecords.ActivityCreation:
          i = this.readActivityCreation(i, data, currentActivityId, currentScopeId);
          break;
        case TraceRecords.ActivityCompletion:
          this.execData.completeActivity(currentActivityId);
          i += RECORD_SIZE.ActivityCompletion;
          break;
        case TraceRecords.DynamicScopeStart:
          i = this.readScopeStart(i, data, currentActivityId, currentScopeId);
          break;
        case TraceRecords.DynamicScopeEnd:
          this.execData.endScope(currentScopeId);
          i += RECORD_SIZE.DynamicScopeEnd;
          break;
        case TraceRecords.PassiveEntityCreation:
          i = this.readEntityCreation(i, data, currentActivityId, currentScopeId);
          break;
        case TraceRecords.PassiveEntityCompletion:
          throw new Error("Should never be reached. Is not generated by Trace Buffer.");
        case TraceRecords.SendOp:
          i = this.readSendOp(i, data, currentActivityId, currentScopeId);
          break;
        case TraceRecords.ReceiveOp:
          i = this.readReceiveOp(i, data, currentActivityId, currentScopeId);
          break;
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
          throw new Error("Unexpected marker in trace: " + marker + " prev: " + prevMarker);
      }
      prevMarker = marker;
    }
  }
}
