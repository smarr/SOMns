import { ActivityType, IdMap } from "./messages";
import { Activity, PassiveEntity, TraceDataUpdate, SendOp, ReceiveOp, DynamicScope } from "./execution-data";
import { getEntityId, getEntityGroupId, getEntityGroupVizId, getEntityVizId } from "./view";
import { KomposMetaModel, EntityRefType } from "./meta-model";

const NUM_ACTIVITIES_STARTING_GROUP = 4;

const HORIZONTAL_DISTANCE = 100;
const VERTICAL_DISTANCE = 100;

export abstract class NodeImpl implements d3.layout.force.Node {
  public index?: number;
  public px?: number;
  public py?: number;
  public fixed?: boolean;
  public weight?: number;

  private _x: number;
  private _y: number;

  constructor(x: number, y: number) {
    this._x = x;
    this._y = y;
  }

  public get x(): number { return this._x; }
  public set x(val: number) {
    if (val > 5000) {
      val = 5000;
    } else if (val < -5000) {
      val = -5000;
    }
    this._x = val;
  }

  public get y(): number { return this._y; }
  public set y(val: number) {
    if (val > 5000) {
      val = 5000;
    } else if (val < -5000) {
      val = -5000;
    }
    this._y = val;
  }
}

export abstract class EntityNode extends NodeImpl {

  constructor(x: number, y: number) {
    super(x, y);
  }

  public abstract getDataId(): string;
  public abstract getSystemViewId(): string;

  public abstract getEntityType(): EntityRefType;
}

export abstract class ActivityNode extends EntityNode {
  public readonly reflexive: boolean;

  constructor(reflexive: boolean, x: number, y: number) {
    super(x, y);
    this.reflexive = reflexive;
  }

  public abstract getGroupSize(): number;
  public abstract isRunning(): boolean;
  public abstract getName(): string;

  public abstract getQueryForCodePane(): string;
  public abstract getType(): ActivityType;

  public abstract getActivityId(): number;

  public getEntityType() { return EntityRefType.Activity; }
}

class ActivityNodeImpl extends ActivityNode {
  public readonly activity: Activity;
  constructor(activity: Activity, reflexive: boolean, x: number, y: number) {
    super(reflexive, x, y);
    this.activity = activity;
  }

  public getGroupSize() { return 1; }
  public isRunning() { return this.activity.running; }
  public getCreationScope() { return this.activity.creationScope; }
  public getName() { return this.activity.name; }

  public getDataId() { return getEntityId(this.activity.id); }
  public getSystemViewId() { return getEntityVizId(this.activity.id); }

  public getQueryForCodePane() { return "#" + getEntityId(this.activity.id); }

  public getType() { return this.activity.type; }

  public getActivityId() { return this.activity.id; }
}

class GroupNode extends ActivityNode {
  private group: ActivityGroup;

  constructor(group: ActivityGroup, reflexive: boolean, x: number, y: number) {
    super(reflexive, x, y);
    this.group = group;
  }

  public getGroupSize() { return this.group.activities.length; }
  public isRunning() {
    console.warn("GroupNode.isRunning() not yet implemented");
    return true;
  }
  public getName() { return this.group.activities[0].name; }

  public getDataId() { return getEntityGroupId(this.group.id); }
  public getSystemViewId() { return getEntityGroupVizId(this.group.id); }

  public getQueryForCodePane() {
    let result = "";
    for (const act of this.group.activities) {
      if (result !== "") { result += ","; }
      result += "#" + getEntityId(act.id);
    }
    return result;
  }

  public getType() { return this.group.activities[0].type; }

  public getActivityId() { return this.group.activities[0].id; }
}

export class PassiveEntityNode extends EntityNode {
  public readonly entity: PassiveEntity;
  public messages?: number[][];

  constructor(entity: PassiveEntity, x: number, y: number) {
    super(x, y);
    this.entity = entity;
  }

  public getDataId() { return getEntityId(this.entity.id); }
  public getSystemViewId() { return getEntityVizId(this.entity.id); }

  public getEntityType() { return EntityRefType.PassiveEntity; }
}

export interface EntityLink extends d3.layout.force.Link<EntityNode> {
  left: boolean;
  right: boolean;
  messageCount: number;
  creation?: boolean;
}

interface ActivityGroup {
  id: number;
  activities: Activity[];
  groupNode?: GroupNode;
}

type SourceTargetMap = Map<EntityNode, Map<EntityNode, number>>;

function sourceTargetInc(map: SourceTargetMap, source: EntityNode,
  target: EntityNode, inc = 1) {
  let s = map.get(source);
  if (s === undefined) {
    s = new Map();
    map.set(source, s);
  }

  let t = s.get(target);
  if (t === undefined) {
    t = 0;
  }
  t += inc;
  s.set(target, t);
}

export class SystemViewData {
  private metaModel: KomposMetaModel;

  private activities: IdMap<ActivityNodeImpl>;
  private activitiesPerType: IdMap<ActivityGroup>;

  private passiveEntities: IdMap<PassiveEntityNode>;

  private messages: SourceTargetMap;

  private maxMessageCount;

  constructor() {
    this.reset();
  }

  public setMetaModel(metaModel: KomposMetaModel) {
    this.metaModel = metaModel;
  }

  public reset() {
    this.activities = {};
    this.activitiesPerType = {};
    this.passiveEntities = {};

    this.maxMessageCount = 0;

    this.messages = new Map();
  }

  public updateTraceData(data: TraceDataUpdate) {
    console.assert(this.metaModel !== undefined, "Meta Model not yet initialized. Is there a race?");
    for (const act of data.activities) {
      this.addActivity(act);
    }

    for (const e of data.passiveEntities) {
      this.addPassiveEntity(e);
    }

    for (const send of data.sendOps) {
      this.addMessage(send);
    }

    for (const rcv of data.receiveOps) {
      this.addMessageRcv(rcv);
    }
  }

  private addActivity(act: Activity) {
    const numGroups = Object.keys(this.activitiesPerType).length;
    if (!this.activitiesPerType[act.name]) {
      this.activitiesPerType[act.name] = { id: numGroups, activities: [] };
    }
    this.activitiesPerType[act.name].activities.push(act);

    const node = new ActivityNodeImpl(act,
      false, // self-sends TODO what is this used for, maybe set to true when checking mailbox.
      HORIZONTAL_DISTANCE + HORIZONTAL_DISTANCE * this.activitiesPerType[act.name].activities.length,
      VERTICAL_DISTANCE * numGroups);
    this.activities[act.id.toString()] = node;
  }

  private getGroupOrActivity(id: number): ActivityNode {
    const node = this.activities[id];
    console.assert(node !== undefined);

    const group = this.activitiesPerType[node.getName()];
    if (group.groupNode) {
      return group.groupNode;
    }
    return node;
  }

  private getNode(type: EntityRefType, entity: number | Activity | DynamicScope | PassiveEntity) {
    switch (type) {
      case EntityRefType.Activity:
        return this.activities[(<Activity> entity).id];
      case EntityRefType.PassiveEntity:
        return this.passiveEntities[(<PassiveEntity> entity).id];
    }
  }

  private addMessage(sendOp: SendOp) {
    const source = this.activities[sendOp.creationActivity.id];
    const target = this.getNode(this.metaModel.sendOps[sendOp.type].target, sendOp.target);

    sourceTargetInc(this.messages, source, target);
  }

  private addMessageRcv(rcvOp: ReceiveOp) {
    const target = this.activities[rcvOp.creationActivity.id];
    const source = this.getNode(this.metaModel.receiveOps[rcvOp.type].source, rcvOp.source);
    sourceTargetInc(this.messages, source, target);
  }

  private addPassiveEntity(passiveEntity: PassiveEntity) {
    this.passiveEntities[passiveEntity.id] = new PassiveEntityNode(
      passiveEntity, HORIZONTAL_DISTANCE * Object.keys(this.passiveEntities).length, 0);
  }

  public getMaxMessageSends() {
    return this.maxMessageCount;
  }

  public getActivityNodes(): ActivityNode[] {
    const groupStarted = {};

    const arr: ActivityNode[] = [];
    for (const i in this.activities) {
      const a = this.activities[i];
      const name = a.getName();
      const group = this.activitiesPerType[name];
      if (group.activities.length > NUM_ACTIVITIES_STARTING_GROUP) {
        if (!groupStarted[name]) {
          groupStarted[name] = true;
          const groupNode = new GroupNode(group,
            false, // todo reflexive
            HORIZONTAL_DISTANCE + HORIZONTAL_DISTANCE * group.activities.length,
            VERTICAL_DISTANCE * Object.keys(this.activitiesPerType).length);
          group.groupNode = groupNode;
          arr.push(groupNode);
        }
      } else {
        arr.push(a);
      }
    }
    return arr;
  }

  public getEntityNodes(): PassiveEntityNode[] {
    const result = [];
    for (const i in this.passiveEntities) {
      result.push(this.passiveEntities[i]);
    }
    return result;
  }

  private collectMessageLinks(links: EntityLink[]) {
    const messages: SourceTargetMap = new Map();

    // first, consider groups for links
    for (const [source, m] of this.messages) {
      for (const [target, cnt] of m) {
        this.maxMessageCount = Math.max(this.maxMessageCount, cnt);

        let sender;
        switch (source.getEntityType()) {
          case EntityRefType.Activity:
            sender = this.getGroupOrActivity((<ActivityNodeImpl> source).activity.id);
            break;
          case EntityRefType.PassiveEntity:
            sender = source;
            break;
        }


        let receiver;
        switch (target.getEntityType()) {
          case EntityRefType.Activity:
            receiver = this.getGroupOrActivity((<ActivityNodeImpl> target).activity.id);
            break;
          case EntityRefType.PassiveEntity:
            receiver = target;
            break;
        }

        sourceTargetInc(messages, sender, receiver, cnt);
      }
    }

    // then, populate the list of links
    for (const [source, m] of messages) {
      for (const [target, cnt] of m) {
        links.push({
          source: source, target: target,
          left: false, right: true,
          creation: false,
          messageCount: cnt
        });
      }
    }
  }

  private collectCreationLinks(links: EntityLink[]) {
    const connections: SourceTargetMap = new Map();

    for (const i in this.activities) {
      const act = this.activities[i];
      if (act.activity.creationActivity === null) {
        // ignore first activity, it is created ex nihilo
        continue;
      }

      const target = this.getGroupOrActivity(act.activity.id);
      const source = this.getGroupOrActivity(act.activity.creationActivity.id);

      sourceTargetInc(connections, source, target);
    }

    for (const i in this.passiveEntities) {
      const e = this.passiveEntities[i];
      const source = this.getGroupOrActivity(e.entity.creationActivity.id);

      sourceTargetInc(connections, source, e);
    }

    for (const [source, m] of connections) {
      for (const [target, cnt] of m) {
        links.push(this.creationLink(source, target, cnt));
      }
    }
  }

  private creationLink(source: EntityNode, target: EntityNode, cnt: number): EntityLink {
    return {
      source: source, target: target,
      left: false, right: true,
      creation: true,
      messageCount: cnt
    };
  }

  public getLinks(): EntityLink[] {
    const links: EntityLink[] = [];
    this.collectMessageLinks(links);
    this.collectCreationLinks(links);

    return links;
  }
}
