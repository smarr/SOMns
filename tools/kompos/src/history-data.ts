/* jshint -W097 */
"use strict";

import {Controller} from "./controller";
import {IdMap, Activity, ActivityType, Channel,
  FullSourceCoordinate} from "./messages";
import {getActivityId, getActivityRectId,
  getActivityGroupId, getActivityGroupRectId} from "./view";

const horizontalDistance = 100,
  verticalDistance = 100;

const SHIFT_HIGH_INT = 4294967296;
const MAX_SAFE_HIGH_BITS = 53 - 32;
const MAX_SAFE_HIGH_VAL  = (1 << MAX_SAFE_HIGH_BITS) - 1;

const NUM_ACTIVITIES_STARTING_GROUP = 4;

const MESSAGE_BIT   = 0x80;
const PROMISE_BIT   = 0x40;
const TIMESTAMP_BIT = 0x20;
const PARAMETER_BIT = 0x10;

enum Trace {
  ActorCreation     =  1,
  PromiseCreation   =  2,
  PromiseResolution =  3,
  PromiseChained    =  4,
  Mailbox           =  5,
  Thread            =  6,
  MailboxContd      =  7,
  ActivityOrigin    =  8,
  PromiseMessage    =  9,

  ProcessCreation   = 10,
  ProcessCompletion = 11,

  TaskSpawn         = 12,
  TaskJoin          = 13,

  PromiseError      = 14,

  ChannelCreation   = 15
}

enum TraceSize {
  ActorCreation     = 19,
  PromiseCreation   = 17,
  PromiseResolution = 28,
  PromiseChained    = 17,
  Mailbox           = 21,
  Thread            = 17,
  MailboxContd      = 25,

  ActivityOrigin    =  9,
  PromiseMessage    =  7,

  ProcessCreation   = 19,
  ProcessCompletion =  9,

  TaskSpawn         = 19,
  TaskJoin          = 11,

  PromiseError      = 28,

  ChannelCreation   = 25
}

enum ParamTypes {
  False    = 0,
  True     = 1,
  Long     = 2,
  Double   = 3,
  Promise  = 4,
  Resolver = 5,
  Object   = 6,
  String   = 7
}

export abstract class ActivityNode {
  public reflexive:  boolean;
  public x:          number;
  public y:          number;

  constructor(reflexive: boolean, x: number, y: number) {
    this.reflexive = reflexive;
    this.x = x;
    this.y = y;
  }

  public abstract getGroupSize(): number;
  public abstract isRunning(): boolean;
  public abstract getName(): string;

  public abstract getDataId(): string;
  public abstract getSystemViewId(): string;

  public abstract getQueryForCodePane(): string;
  public abstract getType(): ActivityType;
}

class ActivityNodeImpl extends ActivityNode {
  private activity: Activity;
  constructor(activity: Activity, reflexive: boolean, x: number, y: number) {
    super(reflexive, x, y);
    this.activity = activity;
  }

  public getGroupSize() { return 1; }
  public isRunning() { return this.activity.running; }
  public getCausalMessage() { return this.activity.causalMsg; }
  public getName() { return this.activity.name; }

  public getDataId()       { return getActivityId(this.activity.id); }
  public getSystemViewId() { return getActivityRectId(this.activity.id); }

  public getQueryForCodePane() { return "#" + getActivityId(this.activity.id); }

  public getType() { return this.activity.type; }
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

  public getDataId()       { return getActivityGroupId(this.group.id); }
  public getSystemViewId() { return getActivityGroupRectId(this.group.id); }

  public getQueryForCodePane() {
    let result = "";
    for (const act of this.group.activities) {
      if (result !== "") { result += ","; }
      result += "#" + getActivityId(act.id);
    }
    return result;
  }

  public getType() { return this.group.activities[0].type; }
}

export interface ActivityLink {
  source: ActivityNode;
  target: ActivityNode;
  left:   boolean;
  right:  boolean;
  messageCount: number;
  creation?: boolean;
}

interface ActivityGroup {
  id:         number;
  activities: Activity[];
  groupNode?: GroupNode;
}

export class HistoryData {
  private activity: IdMap<ActivityNodeImpl> = {};
  private channels: IdMap<Channel> = {};
  private activitiesPerType: IdMap<ActivityGroup> = {};
  private messages: IdMap<IdMap<number>> = {};
  private msgs = {};
  private maxMessageCount = 0;
  private strings: IdMap<string> = {};

  private currentReceiver = -1;
  private currentMsgId = undefined;

  constructor() {
  }

  private addActivity(act: Activity) {
    const numGroups = Object.keys(this.activitiesPerType).length;
    if (!this.activitiesPerType[act.name]) {
      this.activitiesPerType[act.name] = {id: numGroups, activities: []};
    }
    this.activitiesPerType[act.name].activities.push(act);

    const node = new ActivityNodeImpl(act,
      false, // selfsends TODO what is this used for, maybe set to true when checking mailbox.
      horizontalDistance + horizontalDistance * this.activitiesPerType[act.name].activities.length,
      verticalDistance * numGroups);
    this.activity[act.id.toString()] = node;
  }

  private addChannel(actId: number, channelId: number, section: FullSourceCoordinate) {
    this.channels[channelId] = {id: channelId, creatorActivityId: actId, origin: section};
  }

  public addStrings(ids: number[], strings: string[]) {
    for (let i = 0; i < ids.length; i++) {
      this.strings[ids[i].toString()] = strings[i];
    }
  }

  private addMessage(sender: number, target: number, msgId: number) {
    const senderId = sender.toString();
    if (!this.messages.hasOwnProperty(senderId)) {
      this.messages[senderId] = {};
    }
    if (sender !== target) {
      hashAtInc(this.messages[senderId], target.toString(), 1);
    }
    this.msgs[msgId] = {sender: sender, target: target};
  }

  private getActivityOrGroupIfAvailable(actId: string): ActivityNode {
    const node = this.activity[actId];
    if (node === undefined) {
      return null;
    }
    const group = this.activitiesPerType[node.getName()];
    if (group.groupNode) {
      return group.groupNode;
    }
    return node;
  }

  private accumulateMessageCounts(msgMap, links, source: ActivityNode,
      target: ActivityNode, creation: boolean, msgCount: number) {
    const createMsgId = source.getDataId() + ":" + target.getDataId();
      if (!msgMap[createMsgId]) {
        const msgLink = {
          source: source, target: target,
          left: false, right: true,
          creation: creation,
          messageCount: msgCount
        };
        links.push(msgLink);
        msgMap[createMsgId] = msgLink;
      } else {
        msgMap[createMsgId].messageCount += msgCount;
      }
  }

  public getLinks(): ActivityLink[] {
    const links: ActivityLink[] = [];
    const normalMsgMap = {};
    for (const sendId in this.messages) {
      for (const rcvrId in this.messages[sendId]) {
        this.maxMessageCount = Math.max(this.maxMessageCount, this.messages[sendId][rcvrId]);
        const sender = this.getActivityOrGroupIfAvailable(sendId);
        if (sender === null) {
          // this is a data race, so, might not be available yet
          continue;
        }

        const rcvr = this.getActivityOrGroupIfAvailable(rcvrId);
        if (rcvr === null) {
          // this is a data race, so, might not be available yet
          continue;
        }

        this.accumulateMessageCounts(normalMsgMap, links, sender, rcvr, false,
          this.messages[sendId][rcvrId]);
      }
    }

    // get links for creation of entities
    const createMessages = {};
    for (const i in this.activity) {
      const a = this.activity[i];
      const msg = this.msgs[a.getCausalMessage()];
      if (msg === undefined) { continue; }

      const creator = this.getActivityOrGroupIfAvailable(msg.target);
      if (creator === null) {
        // this is a data race, so, might not be available yet
        continue;
      }

      const target = this.getActivityOrGroupIfAvailable(i);
      this.accumulateMessageCounts(createMessages, links, creator, target, true, 1);
    }
    return links;
  }

  public getActivityNodes(): ActivityNode[] {
    const groupStarted = {};

    const arr: ActivityNode[] = [];
    for (const i in this.activity) {
      const a = this.activity[i];
      const name = a.getName();
      const group = this.activitiesPerType[name];
      if (group.activities.length > NUM_ACTIVITIES_STARTING_GROUP) {
        if (!groupStarted[name]) {
          groupStarted[name] = true;
          const groupNode = new GroupNode(group,
            false, // todo reflexive
            horizontalDistance + horizontalDistance * group.activities.length,
            verticalDistance * Object.keys(this.activitiesPerType).length);
          group.groupNode = groupNode;
          arr.push(groupNode);
        }
      } else {
        arr.push(a);
      }
    }
    return arr;
  }

  public getMaxMessageSends() {
    return this.maxMessageCount;
  }

  /** Read a long within JS int range */
  private readLong(d: DataView, offset: number) {
    const high = d.getUint32(offset);
    console.assert(high <= MAX_SAFE_HIGH_VAL);
    return high * SHIFT_HIGH_INT + d.getUint32(offset + 4);
  }

  private readActivity(data: DataView, i: number, type: ActivityType,
      newActivities: Activity[]) {
    const aid = this.readLong(data, i);
    const causalMsg = this.readLong(data, i + 8);
    const nameId: number = data.getUint16(i + 16);
    const actor: Activity = {
      id: aid,
      type:      type,
      name:      this.strings[nameId],
      causalMsg: causalMsg,
      running:   true,
      origin:    this.readActivityOrigin(data, i + 18)};
    this.addActivity(actor);
    newActivities.push(actor);
    return TraceSize.ActorCreation + TraceSize.ActivityOrigin - 1; // type tag of ActorCreation already covered
  }

  private readChannelCreation(data: DataView, i: number) {
    const aId = this.readLong(data, i);
    const cId = this.readLong(data, i + 8);
    const section = this.readSourceSection(data, i + 16);
    this.addChannel(aId, cId, section);
    return TraceSize.ChannelCreation - 1; // type tag already read
  }

  private readSourceSection(data: DataView, i: number): FullSourceCoordinate {
    const fileId:    number = data.getUint16(i);
    const startLine: number = data.getUint16(i + 2);
    const startCol:  number = data.getUint16(i + 4);
    const charLen:   number = data.getUint16(i + 6);
    return {
      uri: this.strings[fileId],
      charLength:  charLen,
      startLine:   startLine,
      startColumn: startCol};
  }

  private readActivityOrigin(data: DataView, i: number): FullSourceCoordinate {
    console.assert(data.getInt8(i) === Trace.ActivityOrigin);
    return this.readSourceSection(data, i + 1);
  }

  private readMessage(data: DataView, msgType: number, i: number): number {
    if ((msgType & PROMISE_BIT) > 0) {
      // promise message
      // var prom = (data.getInt32(i+4) + ':' + data.getInt32(i));
      i += 8;
    }

    const sender = this.readLong(data, i); // sender id
    // 8 byte causal message id
    // var sym = data.getInt16(i+8); //selector
    i += 18;

    if ((msgType & TIMESTAMP_BIT) > 0) {
      // timestamp
      // 8byte execution start
      // 8byte send time

      i += 16;
    }

    if ((msgType & PARAMETER_BIT) > 0) {
      // message parameters
      const numParam = data.getInt8(i); // parameter count
      i++;
      for (let k = 0; k < numParam; k++) {
        i += readParameter(data, i);
      }
    }

    this.addMessage(sender, this.currentReceiver, this.currentMsgId);
    this.currentMsgId += 1;
    return i;
  }

  public updateDataBin(data: DataView, controller: Controller) {
    const newActivities: Activity[] = [];
    let i = 0;
    while (i < data.byteLength) {
      const start = i;
      const msgType = data.getInt8(i);
      i++;
      switch (msgType) {
        case Trace.ActorCreation: {
          i += this.readActivity(data, i, "Actor", newActivities);
          console.assert(i === (start + TraceSize.ActorCreation + TraceSize.ActivityOrigin));
          break;
        }
        case Trace.ProcessCreation: {
          i += this.readActivity(data, i, "Process", newActivities);
          console.assert(i === (start + TraceSize.ProcessCreation + TraceSize.ActivityOrigin));
          break;
        }
        case Trace.TaskSpawn: {
          i += this.readActivity(data, i, "Task", newActivities);
          console.assert(i === (start + TraceSize.TaskSpawn + TraceSize.ActivityOrigin));
          break;
        }
        case Trace.ChannelCreation: {
          i += this.readChannelCreation(data, i);
          console.assert(i === (start + TraceSize.ChannelCreation));
          break;
        }

        case Trace.PromiseCreation:
          i += 16;
          console.assert(i === (start + TraceSize.PromiseCreation));
          break;
        case Trace.PromiseResolution:
          i += 16;
          i += readParameter(data, i);
          console.assert(i <= (start + TraceSize.PromiseResolution));
          break;
        case Trace.PromiseChained:
          i += 16;
          console.assert(i === (start + TraceSize.PromiseChained));
          break;
        case Trace.Mailbox:
          this.currentMsgId = this.readLong(data, i);
          this.currentReceiver = this.readLong(data, i + 12);
          i += 20;
          console.assert(i === (start + TraceSize.Mailbox));
          break;
        case Trace.Thread:
          i += 16;
          console.assert(i === (start + TraceSize.Thread));
          break;
        case Trace.MailboxContd:
          this.currentMsgId = this.readLong(data, i);
          this.currentReceiver = this.readLong(data, i + 12); // receiver id
          i += 24;
          console.assert(i === (start + TraceSize.MailboxContd));
          break;
        case Trace.PromiseMessage:
          i += 6;
          console.assert(i === (start + TraceSize.PromiseMessage));
          break;
        case Trace.ProcessCompletion:
          i += 8;
          console.assert(i === (start + TraceSize.ProcessCompletion));
          break;
        case Trace.TaskJoin:
          i += 10;
          console.assert(i === (start + TraceSize.TaskJoin));
          break;

        default:
          console.assert((msgType & MESSAGE_BIT) !== 0, "msgType was expected to be > 0x80, but was " + msgType);
          i = this.readMessage(data, msgType, i); // doesn't return an offset, but the absolute index
          break;
      }
    }
    controller.newActivities(newActivities);
  }
}

function readParameter(dv: DataView, offset: number): number {
  const paramType = dv.getInt8(offset);
  switch (paramType) {
    case ParamTypes.False:
      return 1;
    case ParamTypes.True:
      return 1;
    case ParamTypes.Long:
      return 9;
    case ParamTypes.Double:
      return 9;
    case ParamTypes.Promise:
      return 9;
    case ParamTypes.Resolver:
      return 9;
    case ParamTypes.Object:
      return 3;
    case ParamTypes.String:
      return 1;
    default:
      console.warn("readParameter default case. NOT YET IMPLEMENTED???");
      return 1;
  }
}

function hashAtInc(hash, idx: string, inc: number) {
  if (hash.hasOwnProperty(idx)) {
    hash[idx] += inc;
  } else {
    hash[idx] = inc;
  }
}


