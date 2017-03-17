/* jshint -W097 */
"use strict";

import {Controller} from "./controller";
import {IdMap, Activity, ActivityType} from "./messages";
import {dbgLog} from "./source";

const horizontalDistance = 100,
  verticalDistance = 100;

const SHIFT_HIGH_INT = 4294967296;
const MAX_SAFE_HIGH_BITS = 53 - 32;
const MAX_SAFE_HIGH_VAL  = (1 << MAX_SAFE_HIGH_BITS) - 1;

const NUM_ACTIVITIES_STARTING_GROUP = 4;

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
  TaskJoin          = 13
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
  TaskJoin          = 11
}

export interface ActivityNode {
  activity:   Activity;
  reflexive:  boolean;
  x:          number;
  y:          number;
  groupSize?: number;
}

export interface ActivityLink {
  source: ActivityNode;
  target: ActivityNode;
  left:   boolean;
  right:  boolean;
  messageCount: number;
  creation?: boolean;
}

export class HistoryData {
  private activity: IdMap<ActivityNode> = {};
  private activityPerType: IdMap<number> = {};
  private messages: IdMap<IdMap<number>> = {};
  private msgs = {};
  private maxMessageCount = 0;
  private strings: IdMap<string> = {};

  private currentReceiver = -1;
  private currentMsgId = undefined;

  constructor() {
  }

  addActivity(act: Activity) {
    hashAtInc(this.activityPerType, act.name, 1);
    const node = {
      activity:  act,
      reflexive: false, // selfsends TODO what is this used for, maybe set to true when checking mailbox.
      x: horizontalDistance + horizontalDistance * this.activityPerType[act.name],
      y: verticalDistance * Object.keys(this.activityPerType).length
    };
    this.activity[act.id.toString()] = node;
  }

  addStrings(ids: number[], strings: string[]) {
    for (let i = 0; i < ids.length; i++) {
      this.strings[ids[i].toString()] = strings[i];
    }
  }

  addMessage(sender: number, target: number, msgId: number) {
    const senderId = sender.toString();
    if (!this.messages.hasOwnProperty(senderId)) {
      this.messages[senderId] = {};
    }
    if (sender !== target) {
      hashAtInc(this.messages[senderId], target.toString(), 1);
    }
    this.msgs[msgId] = {sender: sender, target: target};
  }

  getLinks(): ActivityLink[] {
    const links: ActivityLink[] = [];
    for (const sendId in this.messages) {
      for (const rcvrId in this.messages[sendId]) {
        this.maxMessageCount = Math.max(this.maxMessageCount, this.messages[sendId][rcvrId]);
        if (this.activity[sendId] === undefined) {
          dbgLog("WAT? racy? unknown sendId: " + sendId);
          continue;
        }
        if (this.activity[rcvrId] === undefined) {
          dbgLog("WAT? racy? unknown rcvrId: " + rcvrId);
          continue;
        }
        links.push({
          source: this.activity[sendId],
          target: this.activity[rcvrId],
          left: false, right: true,
          messageCount: this.messages[sendId][rcvrId]
        });
      }
    }

    for (const i in this.activity) {
      const a = this.activity[i];
      const msg = this.msgs[a.activity.causalMsg];
      if (msg === undefined) { continue; }
      links.push({
        source: this.activity[msg.target],
        target: a,
        left: false, right: true,
        creation:     true,
        messageCount: 1
      });
    }
    return links;
  }

  getActivityNodes(): ActivityNode[] {
    const groupStarted = {};

    const arr: ActivityNode[] = [];
    for (const i in this.activity) {
      const a = this.activity[i];
      const groupSize = this.activityPerType[a.activity.name];
      if (groupSize > NUM_ACTIVITIES_STARTING_GROUP) {
        if (!groupStarted[a.activity.name]) {
          groupStarted[a.activity.name] = true;
          arr.push(a);
          a.groupSize = groupSize;
        }
      } else {
        arr.push(a);
      }
    }
    return arr;
  }

  getMaxMessageSends() {
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

  private readActivityOrigin(data: DataView, i: number) {
    console.assert(data.getInt8(i) === Trace.ActivityOrigin);
    const fileId:    number = data.getUint16(i + 1);
    const startLine: number = data.getUint16(i + 3);
    const startCol:  number = data.getUint16(i + 5);
    const charLen:   number = data.getUint16(i + 7);
    return {
      uri: this.strings[fileId],
      charLength:  charLen,
      startLine:   startLine,
      startColumn: startCol};
  }

  updateDataBin(data: DataView, controller: Controller) {
    const newActivities: Activity[] = [];
    let i = 0;
    while (i < data.byteLength) {
      const start = i;
      const typ = data.getInt8(i);
      i++;
      switch (typ) {
        case Trace.ActorCreation: {
          i += this.readActivity(data, i, "Actor", newActivities);
          console.assert(i === (start + TraceSize.ActorCreation + TraceSize.ActivityOrigin));
          break;
        }
        case Trace.ProcessCreation: {
          i += this.readActivity(data, i, "Process", newActivities);
          console.assert(i === (start + TraceSize.ProcessCreation));
          break;
        }
        case Trace.TaskSpawn: {
          i += this.readActivity(data, i, "Task", newActivities);
          console.assert(i === (start + TraceSize.TaskSpawn));
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
          if (! (typ & 0x80)) {
            break;
          }

          if (typ & 0x40) {
            // promise message
            // var prom = (data.getInt32(i+4) + ':' + data.getInt32(i));
            i += 8;
          }

          let sender = this.readLong(data, i); // sender id
          // 8 byte causal message id
          // var sym = data.getInt16(i+8); //selector
          i += 18;

          if (typ & 0x20) {
            // timestamp
            // 8byte execution start
            // 8byte send time

            i += 16;
          }

          if (typ & 0x10) {
            // message parameters
            let numParam = data.getInt8(i); // parameter count
            i++;
            let k;
            for (k = 0; k < numParam; k++) {
              i += readParameter(data, i);
            }
          }

          this.addMessage(sender, this.currentReceiver, this.currentMsgId);
          this.currentMsgId += 1;
      }
    }
    controller.newActivities(newActivities);
  }
}

function readParameter(dv: DataView, offset: number): number {
  const paramType = dv.getInt8(offset);
  switch (paramType) {
    case 0: // false
      return 1;
    case 1: // true
      return 1;
    case 2: // long
      return 9;
    case 3: // double
      return 9;
    case 4: // promise (promise id)
      return 9;
    case 5: // resolver (promise id)
      return 9;
    case 6: // Object Type
      return 3;
    case 7: // String
      return 1;
    default:
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


