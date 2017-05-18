import { ServerCapabilities, SymbolMessage, Activity, FullSourceCoordinate, ActivityType } from "./messages";
import { TraceParser } from "./trace-parser";

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

export class RawActivity extends RawData {
  private type: ActivityType;
  private activityId: number;
  private symbolId:  number;
  private sourceSection: RawSourceCoordinate;
  private creationActivity?: number;
  private creationScope?: number;

  constructor(type: ActivityType, activityId: number, symbolId: number,
      sourceSection: RawSourceCoordinate, creationActivity: number,
      creationScope: number) {
    super();
    this.type = type;
    this.activityId = activityId;
    this.symbolId   = symbolId;
    this.sourceSection = sourceSection;
    this.creationActivity = creationActivity;
    this.creationScope = creationScope;
  }

  public resolve(data: ExecutionData): Activity | false {
    const name = data.getSymbol(this.symbolId);
    if (name === undefined) { return false; }

    let creationScope;
    if (this.creationScope === null) {
      creationScope = null;
    } else {
      creationScope = data.getScope(this.creationScope);
      if (creationScope === null) {
        return false;
      }
    }

    const source = this.sourceSection.resolve(data);
    if (source === false) {
      return false;
    }

    let creationActivity;
    let isMainActivity = false;
    if (this.creationActivity === null) {
      creationActivity = null;
    } else {
      // handle main activity
      if (this.creationActivity === 0 && this.activityId === 0) {
        creationActivity = null;
        isMainActivity = true;
      } else {
        creationActivity = data.getActivity(this.creationActivity);
        if (creationActivity === undefined) {
          return false;
        }
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

/** Maintains all data about the programs execution.
    It is also the place where partial data gets resolved once missing pieces
    are found. */
export class ExecutionData {
  private serverCapabilities: ServerCapabilities;
  private traceParser?: TraceParser;
  private readonly symbols: string[];

  private rawActivities: RawActivity[];
  private rawScopes;
  private rawPassiveEntities;

  private newActivities: Activity[];

  private activities: Activity[];
  private scopes: DynamicScope[];

  constructor() {
    this.symbols = [];
    this.activities = [];
    this.rawScopes = [];
    this.rawActivities = [];
    this.rawPassiveEntities = [];
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

  public setCapabilities(capabilities: ServerCapabilities) {
    this.serverCapabilities = capabilities;
    this.traceParser = new TraceParser(capabilities, this);
  }

  public updateTraceData(data: DataView) {
    this.traceParser.parseTrace(data);
    this.resolveData();
  }

  public addSymbols(msg: SymbolMessage) {
    for (let i = 0; i < msg.ids.length; i++) {
      this.symbols[msg.ids[i]] = msg.symbols[i];
    }
  }

  public addRawActivity(activity: RawActivity) {
    this.rawActivities.push(activity);
  }

  public getNewActivitiesSinceLastUpdate(): Activity[] {
    return this.newActivities;
  }

  private resolveData() {
    this.newActivities = [];
    for (const act of this.rawActivities) {
      const a = act.resolve(this);
      if (a !== false) {
        console.assert(this.activities[a.id] === undefined);
        this.activities[a.id] = a;
        this.newActivities[a.id] = a;
      }
    }

    for (const scope of this.rawScopes) {
      const s = scope.resolve(this);
      if (s !== false) {
        this.scopes[s.id] = s;
      }
    }

    for (const ent of this.rawPassiveEntities) {
      const e = ent.resolve(this);
      if (e !== false) {
        this.passiveEntities[e.id] = e;
      }
    }
  }
}
