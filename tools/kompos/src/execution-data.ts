import { SymbolMessage, FullSourceCoordinate, ActivityType, PassiveEntityType,
  DynamicScopeType, SendOpType, ReceiveOpType } from "./messages";
import { TraceParser } from "./trace-parser";
import { KomposMetaModel, EntityRefType } from "./meta-model";


export interface EntityProperties {
  id:      number;
  origin?: FullSourceCoordinate;
  creationScope?:   DynamicScope;
  creationActivity: Activity;
}

export interface PassiveEntity extends EntityProperties {
  type: PassiveEntityType;
}

export interface Activity extends EntityProperties {
  type:      ActivityType;
  name:      string;
  running:   boolean;
  completed: boolean;
}

export interface DynamicScope extends EntityProperties {
  type: DynamicScopeType;
  active: boolean;
}

export interface SendOp extends EntityProperties {
  type: SendOpType;
  entity: Activity | PassiveEntity | DynamicScope | number;
  target: Activity | PassiveEntity | DynamicScope | number;
}

export interface ReceiveOp extends EntityProperties {
  type: ReceiveOpType;
  source: Activity | PassiveEntity | DynamicScope | number;
}

/** Some raw data, which is only available partially and contains ids that need
    to be resolved. */
abstract class RawData {
  /** Returns the resolved datum, or false if not all data is available. */
  public abstract resolve(data: ExecutionData);
}

export class RawSourceCoordinate extends RawData {
  private fileUriId:  number;  // needs to be looked up in the string id table
  private charLength: number;
  private startLine:   number;
  private startColumn: number;

  constructor(fileUriId: number, charLength: number, startLine: number,
      startColumn: number) {
    super();
    this.fileUriId   = fileUriId;
    this.charLength  = charLength;
    this.startLine   = startLine;
    this.startColumn = startColumn;
  }

  public resolve(data: ExecutionData): FullSourceCoordinate | false {
    const uri = data.getSymbol(this.fileUriId);
    if (uri === undefined) { return false; }
    return {
      uri:         uri,
      charLength:  this.charLength,
      startLine:   this.startLine,
      startColumn: this.startColumn
    };
  }
}

export abstract class RawEntity extends RawData {
  private creationActivity?: number;
  private creationScope?: number;

  constructor(creationActivity: number, creationScope: number) {
    super();
    this.creationActivity = creationActivity;
    this.creationScope    = creationScope;
  }

  protected resolveCreationActivity(data: ExecutionData) {
    if (this.creationActivity === null) {
      return null;
    } else {
      return data.getActivity(this.creationActivity);
    }
  }

  protected resolveCreationScope(data: ExecutionData) {
    if (this.creationScope === null) {
      return null;
    } else {
      return data.getScope(this.creationScope);
    }
  }

  protected resolveEntity(data: ExecutionData, entityId: number,
      type: EntityRefType): Activity | PassiveEntity | DynamicScope | number {
    switch (type) {
      case EntityRefType.None:          return entityId;
      case EntityRefType.Activity:      return data.getActivity(entityId);
      case EntityRefType.DynamicScope:  return data.getScope(entityId);
      case EntityRefType.PassiveEntity: return data.getPassiveEntity(entityId);
    }
  }
}

export class RawActivity extends RawEntity {
  private type: ActivityType;
  private activityId: number;
  private symbolId:  number;
  private sourceSection: RawSourceCoordinate;

  constructor(type: ActivityType, activityId: number, symbolId: number,
      sourceSection: RawSourceCoordinate, creationActivity: number,
      creationScope: number) {
    super(creationActivity, creationScope);
    this.type = type;
    this.activityId = activityId;
    this.symbolId   = symbolId;
    this.sourceSection = sourceSection;
  }

  public resolve(data: ExecutionData): Activity | false {
    const name = data.getSymbol(this.symbolId);
    if (name === undefined) { return false; }

    const creationScope = this.resolveCreationScope(data);
    if (creationScope === undefined) {
      return false;
    }

    const source = this.sourceSection.resolve(data);
    if (source === false) {
      return false;
    }

    let creationActivity;
    if (this.activityId === 0) {
      creationActivity = null;
    } else {
      creationActivity = this.resolveCreationActivity(data);
      if (creationActivity === undefined) {
        return false;
      }
    }

    return {
      id:  this.activityId,
      name: name,
      running: true,
      type: this.type,
      creationScope: creationScope,
      creationActivity: creationActivity,
      origin: source,
      completed: false
    };
  }
}

export class RawScope extends RawEntity {
  private type: DynamicScopeType;
  private scopeId: number;
  private sourceSection: RawSourceCoordinate;

  constructor(type: DynamicScopeType, scopeId: number,
      sourceSection: RawSourceCoordinate, creationActivity: number,
      creationScope: number) {
    super(creationActivity, creationScope);
    this.type = type;
    this.scopeId = scopeId;
    this.sourceSection = sourceSection;
  }

  public resolve(data: ExecutionData): DynamicScope | false {
    const source = this.sourceSection.resolve(data);
    if (source === false) {
      return false;
    }

    const creationActivity = this.resolveCreationActivity(data);
    if (creationActivity === undefined) {
      return false;
    }

    const creationScope = this.resolveCreationScope(data);
    if (creationScope === undefined) {
      return false;
    }

    return {
      type: this.type,
      id: this.scopeId,
      active: true,
      creationActivity: creationActivity,
      creationScope: creationScope,
      origin: source
    };
  }
}

export class RawPassiveEntity extends RawEntity {
  private type: PassiveEntityType;
  private entityId: number;
  private sourceSection: RawSourceCoordinate;

  constructor(type: PassiveEntityType, entityId: number,
      sourceSection: RawSourceCoordinate, creationActivity: number,
      creationScope: number) {
    super(creationActivity, creationScope);
    this.type = type;
    this.entityId = entityId;
    this.sourceSection = sourceSection;
  }

  public resolve(data: ExecutionData): PassiveEntity | false {
    const source = this.sourceSection.resolve(data);
    if (source === false) {
      return false;
    }

    const creationActivity = this.resolveCreationActivity(data);
    if (creationActivity === undefined) {
      return false;
    }

    const creationScope = this.resolveCreationScope(data);
    if (creationScope === undefined) {
      return false;
    }

    return {
      type: this.type,
      id: this.entityId,
      creationActivity: creationActivity,
      creationScope: creationScope,
      origin: source
    };
  }
}

export class RawSendOp extends RawEntity {
  private readonly type: SendOpType;
  private readonly entityId: number;
  private readonly targetId: number;

  constructor(type: SendOpType, entityId: number, targetId: number,
      creationActivity: number, creationScope: number) {
    super(creationActivity, creationScope);
    this.type = type;
    this.entityId = entityId;
    this.targetId = targetId;
  }

  public resolve(data: ExecutionData): SendOp | false {
    const creationActivity = this.resolveCreationActivity(data);
    if (creationActivity === undefined) {
      return false;
    }

    const creationScope = this.resolveCreationScope(data);
    if (creationScope === undefined) {
      return false;
    }
    const entity = this.resolveEntity(data, this.entityId, data.getSendOpModel(this.type).entity);
    if (entity === undefined) {
      return false;
    }

    const target = this.resolveEntity(data, this.targetId, data.getSendOpModel(this.type).target);
    if (target === undefined) {
      return false;
    }
    return {
      id: null,
      type: this.type,
      entity: entity,
      target: target,
      creationActivity: creationActivity,
      creationScope: creationScope
    };
  }
}

export class RawReceiveOp extends RawEntity {
  private readonly type: ReceiveOpType;
  private readonly sourceId: number;

  constructor(type: ReceiveOpType, sourceId: number, creationActivity: number,
      creationScope: number) {
    super(creationActivity, creationScope);
    this.type = type;
    this.sourceId = sourceId;
  }

  public resolve(data: ExecutionData): ReceiveOp | false {
    const creationActivity = this.resolveCreationActivity(data);
    if (creationActivity === undefined) {
      return false;
    }

    const creationScope = this.resolveCreationScope(data);
    if (creationScope === undefined) {
      return false;
    }

    const entity = this.resolveEntity(data, this.sourceId, data.getReceiveOpModel(this.type).source);
    if (entity === undefined) {
      return false;
    }
    return {
      id: null,
      type: this.type,
      source: entity,
      creationActivity: creationActivity,
      creationScope: creationScope
    };
  }
}

export class TraceDataUpdate {
  public readonly activities:      Activity[];
  public readonly scopes:          DynamicScope[];
  public readonly passiveEntities: PassiveEntity[];

  public readonly sendOps:    SendOp[];
  public readonly receiveOps: ReceiveOp[];

  constructor() {
    this.activities = [];
    this.scopes     = [];
    this.passiveEntities = [];

    this.sendOps    = [];
    this.receiveOps = [];
  }
}

/** Maintains all data about the programs execution.
    It is also the place where partial data gets resolved once missing pieces
    are found. */
export class ExecutionData {
  private metaModel: KomposMetaModel;
  private traceParser?: TraceParser;
  private readonly symbols: string[];

  private rawActivities:      RawActivity[];
  private rawScopes:          RawScope[];
  private rawPassiveEntities: RawPassiveEntity[];
  private rawSends:           RawSendOp[];
  private rawReceives:        RawReceiveOp[];

  private newData: TraceDataUpdate;

  private activities:      Activity[];
  private scopes:          DynamicScope[];
  private passiveEntities: PassiveEntity[];
  private sendOps:         SendOp[];
  private receiveOps:      ReceiveOp[];

  private endedScopes: number[];
  private completedActivities: number[];

  constructor() {
    this.symbols = [];

    this.activities = [];
    this.scopes     = [];
    this.passiveEntities = [];
    this.sendOps = [];
    this.receiveOps = [];

    this.rawScopes = [];
    this.rawActivities = [];
    this.rawPassiveEntities = [];
    this.rawSends = [];
    this.rawReceives = [];

    this.endedScopes = [];
    this.completedActivities = [];

    this.newData = new TraceDataUpdate();
  }

  public getSendOpModel(marker: number) {
    return this.metaModel.sendOps[marker];
  }

  public getReceiveOpModel(marker: number) {
    return this.metaModel.receiveOps[marker];
  }

  public getSymbol(id: number) {
    return this.symbols[id];
  }

  public getScope(id: number) {
    return this.scopes[id];
  }

  /** @param id is a global unique id, unique for all types of activities. */
  public getActivity(id: number): Activity {
    return this.activities[id];
  }

  public getPassiveEntity(id: number): PassiveEntity {
    return this.passiveEntities[id];
  }

  public setCapabilities(metaModel: KomposMetaModel) {
    this.metaModel = metaModel;
    this.traceParser = new TraceParser(metaModel, this);
  }

  public updateTraceData(data: DataView) {
    this.traceParser.parseTrace(data);
    this.resolveData();
  }

  public addSymbols(msg: SymbolMessage) {
    for (let i = 0; i < msg.ids.length; i++) {
      this.symbols[msg.ids[i]] = msg.symbols[i];
    }
    this.resolveData();
  }

  public addRawActivity(activity: RawActivity) {
    this.rawActivities.push(activity);
  }

  public completeActivity(activityId: number) {
    this.completedActivities.push(activityId);
  }

  public addRawScope(scope: RawScope) {
    this.rawScopes.push(scope);
  }

  public endScope(scopeId: number) {
    this.endedScopes.push(scopeId);
  }

  public addRawPassiveEntity(entity: RawPassiveEntity) {
    this.rawPassiveEntities.push(entity);
  }

  public addRawSendOp(send: RawSendOp) {
    this.rawSends.push(send);
  }

  public addRawReceiveOp(receive: RawReceiveOp) {
    this.rawReceives.push(receive);
  }

  public getNewestDataSinceLastUpdate(): TraceDataUpdate {
    const result = this.newData;
    this.newData = new TraceDataUpdate();
    return result;
  }

  private resolveData() {
    for (const i in this.rawActivities) {
      const a = this.rawActivities[i].resolve(this);
      if (a !== false) {
        delete this.rawActivities[i];
        console.assert(this.activities[a.id] === undefined);
        this.activities[a.id] = a;
        this.newData.activities.push(a);
      }
    }

    for (const i in this.rawScopes) {
      const s = this.rawScopes[i].resolve(this);
      if (s !== false) {
        delete this.rawScopes[i];
        this.scopes[s.id] = s;
        this.newData.scopes.push(s);
      }
    }

    for (const i in this.rawPassiveEntities) {
      const e = this.rawPassiveEntities[i].resolve(this);
      if (e !== false) {
        delete this.rawPassiveEntities[i];
        this.passiveEntities[e.id] = e;
        this.newData.passiveEntities.push(e);
      }
    }

    for (const i in this.rawSends) {
      const s = this.rawSends[i].resolve(this);
      if (s !== false) {
        delete this.rawSends[i];
        this.sendOps.push(s);
        this.newData.sendOps.push(s);
      }
    }

    for (const i in this.rawReceives) {
      const r = this.rawReceives[i].resolve(this);
      if (r !== false) {
        delete this.rawReceives[i];
        this.receiveOps.push(r);
        this.newData.receiveOps.push(r);
      }
    }

    for (const i in this.endedScopes) {
      const sId = this.endedScopes[i];
      if (this.scopes[sId]) {
        delete this.endedScopes[i];
        this.scopes[sId].active = false;
      }
    }

    for (const i in this.completedActivities) {
      const aId = this.completedActivities[i];
      if (this.activities[aId]) {
        delete this.completedActivities[i];
        this.activities[aId].completed = true;
      }
    }
  }
}
