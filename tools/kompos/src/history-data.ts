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
          dbgLog("WAT? unknown sendId: " + sendId);
        }
        if (this.actors[rcvrId] === undefined) {
          dbgLog("WAT? unknown rcvrId: " + rcvrId);
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
      const typ = data.getInt8(i);
      i++;
      switch (typ) {
        case 1: {
          const aid = this.readLong(data, i);
          // 8 byte causal message id
          const nameId: number = data.getUint16(i + 16);
          const actor: Activity = {id: aid, name: this.strings[nameId], type: "Actor"};
          this.addActor(actor);
          newActivities.push(actor);
          i += 18;
          break;
        }
        case 10: {
          const aid = this.readLong(data, i);
          // 8 byte causal message id
          const nameId: number = data.getUint16(i + 16);
          const proc: Activity = {id: aid, name: this.strings[nameId], type: "Process"};
          newActivities.push(proc);
          i += 18;
          break;
        }
        case 2:
          // 8 byte promise id
          // 8 byte causal message id
          i += 16;
          break;
        case 3:
          // 8 byte promise id
          // 8 byte resolving message id
          i += 16;
          i += readParameter(data, i);
          break;
        case 4:
          // 8 byte promise id
          // 8 byte chained promise id
          i += 16;
          break;
        case 5:
          // 8 byte message base id
          // 4 byte mailboxno
          this.currentReceiver = this.readLong(data, i + 12); // receiver id
          i += 20;
          break;
        case 6:
          data.getInt8(i); // Thread
          // 8 byte timestamp
          i += 9;
          break;
        case 7:
          // 8 byte message base id
          // 4 byte mailboxno
          this.currentReceiver = this.readLong(data, i + 12); // receiver id
          data.getInt32(i + 20); // id offset
          i += 24;
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
