/* jshint -W097 */
"use strict";

import {Controller} from "./controller";
import {IdMap, Activity} from "./messages";
import {dbgLog} from "./source";

const horizontalDistance = 100,
  verticalDistance = 100;

const SHIFT_HIGH_INT = 4294967296;
const MAX_SAFE_HIGH_BITS = 53 - 32;
const MAX_SAFE_HIGH_VAL  = (1 << MAX_SAFE_HIGH_BITS) - 1;

enum Trace {
  ActorCreation     =  1,
  PromiseCreation   =  2,
  PromiseResolution =  3,
  PromiseChained    =  4,
  Mailbox           =  5,
  Thread            =  6,
  MailboxContd      =  7,

  BasicMessage      =  8,
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

  BasicMessage      =  7,
  PromiseMessage    =  7,

  ProcessCreation   = 19,
  ProcessCompletion =  9,

  TaskSpawn         = 19,
  TaskJoin          = 11
}

export class HistoryData {
  private actors = {};
  private actorsPerType: IdMap<number> = {};
  private messages: IdMap<IdMap<number>> = {};
  private maxMessageCount = 0;
  private strings: IdMap<string> = {};
  private currentReceiver = 0;

  constructor() {
  }

  addActor(act: Activity) {
    hashAtInc(this.actorsPerType, act.name, 1);
      const node = {
        id: act.id,
        name: act.name,
        reflexive: false, // selfsends TODO what is this used for, maybe set to true when checking mailbox.
        x: horizontalDistance + horizontalDistance * this.actorsPerType[act.name],
        y: verticalDistance * Object.keys(this.actorsPerType).length,
        type: act.name
      };
      this.actors[act.id.toString()] = node;
  }

  addStrings(ids: number[], strings: string[]) {
    for (let i = 0; i < ids.length; i++) {
      this.strings[ids[i].toString()] = strings[i];
    }
  }

  addMessage(sender: number, target: number) {
      if (!this.messages.hasOwnProperty(sender.toString())) {
        this.messages[sender.toString()] = {};
      }
      if (sender !== target) {
        hashAtInc(this.messages[sender.toString()], target.toString(), 1);
      }
  }

  getLinks() {
    let links = [];
    for (let sendId in this.messages) {
      for (let rcvrId in this.messages[sendId]) {
        this.maxMessageCount = Math.max(this.maxMessageCount, this.messages[sendId][rcvrId]);
        if (this.actors[sendId] === undefined) {
          dbgLog("WAT? racy? unknown sendId: " + sendId);
          continue;
        }
        if (this.actors[rcvrId] === undefined) {
          dbgLog("WAT? racy? unknown rcvrId: " + rcvrId);
          continue;
        }
        links.push({
          source: this.actors[sendId],
          target: this.actors[rcvrId],
          left: false, right: true,
          messageCount: this.messages[sendId][rcvrId]
        });
      }
    }
    return links;
  }

  getActorNodes() {
    return mapToArray(this.actors);
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

  updateDataBin(data: DataView, controller: Controller) {
    const newActivities: Activity[] = [];
    let i = 0;
    while (i < data.byteLength) {
      const start = i;
      const typ = data.getInt8(i);
      i++;
      switch (typ) {
        case Trace.ActorCreation: {
          const aid = this.readLong(data, i);
          // 8 byte causal message id
          const nameId: number = data.getUint16(i + 16);
          const actor: Activity = {id: aid, name: this.strings[nameId], type: "Actor"};
          this.addActor(actor);
          newActivities.push(actor);
          i += 18;

          console.assert(i === (start + TraceSize.ActorCreation));
          break;
        }
        case Trace.ProcessCreation: {
          const aid = this.readLong(data, i);
          // 8 byte causal message id
          const nameId: number = data.getUint16(i + 16);
          const proc: Activity = {id: aid, name: this.strings[nameId], type: "Process"};
          newActivities.push(proc);
          i += 18;
          console.assert(i === (start + TraceSize.ProcessCreation));
          break;
        }
        case Trace.TaskSpawn: {
          const aid = this.readLong(data, i);
          // 8 byte causal message id
          const nameId: number = data.getUint16(i + 16);
          const proc: Activity = {id: aid, name: this.strings[nameId], type: "Task"};
          newActivities.push(proc);
          i += 18;
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
          this.currentReceiver = this.readLong(data, i + 12);
          i += 20;
          console.assert(i === (start + TraceSize.Mailbox));
          break;
        case Trace.Thread:
          i += 16;
          console.assert(i === (start + TraceSize.Thread));
          break;
        case Trace.MailboxContd:
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

          this.addMessage(sender, this.currentReceiver);
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

function mapToArray(map) {
  const arr = [];
  for (const i in map) {
    arr.push(map[i]);
  }
  return arr;
}
