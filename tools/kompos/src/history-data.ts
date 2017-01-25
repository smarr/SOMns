/* jshint -W097 */
"use strict";

import {IdMap} from "./messages";
import {dbgLog} from "./source";

var horizontalDistance = 100,
  verticalDistance = 100;

export class HistoryData {
  private actors = {};
  private actorsPerType: IdMap<number> = {};
  private messages: IdMap<IdMap<number>> = {};
  private maxMessageCount = 0;
  private strings = {};
  private currentReceiver = "";

  constructor(){
    this.addActor("0:0", "Platform"); // add main actor
  }

  addActor(id: string, type: string) {
    hashAtInc(this.actorsPerType, type, 1);
      var node = {
        id: id,
        name: type,
        reflexive: false, // selfsends TODO what is this used for, maybe set to true when checking mailbox.
        x: horizontalDistance + horizontalDistance * this.actorsPerType[type],
        y: verticalDistance * Object.keys(this.actorsPerType).length,
        type: type
      };
      this.actors[id.toString()] = node;
  }

  addStrings(ids: number[], strings: string[]){
    var i;
    for (i = 0; i < ids.length; i++) {
      this.strings[ids[i].toString()] = strings[i];
    }
  }

  addMessage(sender: string, target: string) {
      if (target === "0:0") { return; };

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

  updateDataBin(data: DataView){
    var i = 0;
    while(i < data.byteLength){
      var typ = data.getInt8(i);
      i++;
      switch(typ){
        case 1:
          var aid = (data.getInt32(i + 4) + ":" + data.getInt32(i));
          // 8 byte causal message id
          var type: number = data.getInt16(i + 16); // type
          this.addActor(aid, this.strings[type]);
          i += 18;
          break;
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
          this.currentReceiver = (data.getInt32(i + 12) + ":" + data.getInt32(i + 8)); // receiver id
          i += 16;
          break;
        case 6:
          data.getInt8(i); // Thread
          // 8 byte timestamp
          i += 9;
          break;
        case 7:
          // 8 byte message base id
          this.currentReceiver = (data.getInt32(i + 12) + ":" + data.getInt32(i + 8)); // receiver id
          data.getInt16(i + 16); // id offset
          i += 18;
          break;
        case 8:
          var sender = (data.getInt32(i + 4) + ":" + data.getInt32(i)); // sender id
          // 8 byte causal message id
          data.getInt16(i + 16); // selector
          // 8byte execution start
          // 8byte send time
          var numParam = data.getInt8(i + 34); // parameter count
          i += 35;
          for (var k = 0; k < numParam; k++) {
            i += readParameter(data, i);
          }
          this.addMessage(sender, this.currentReceiver);
          break;
        case 9:
          // 8 byte promise id
          sender = (data.getInt32(i + 12) + ":" + data.getInt32(i + 8)); // sender id
          // 8 byte causal message id
          data.getInt16(i + 24); // selector
          // 8byte execution start
          // 8byte send time
          numParam = data.getInt8(i + 42); // parameter count
          i += 43;
          for (var k = 0; k < numParam; k++) {
            i += readParameter(data, i);
          }
          this.addMessage(sender, this.currentReceiver);
          break;
      }
    }
  }
}

function readParameter(dv: DataView, offset: number): number {
  var paramType = dv.getInt8(offset);
  switch(paramType){
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
  var arr = [];
  for (var i in map) {
    arr.push(map[i]);
  }
  return arr;
}
