/* jshint -W097 */
'use strict';

import {IdMap} from './messages';
import {dbgLog} from './source';
import {displayMessageHistory} from './visualizations';

var horizontalDistance = 100,
  verticalDistance = 100;

export class HistoryData {
  private actors = {};
  private actorsPerType: IdMap<number> = {};
  private messages: IdMap<IdMap<number>> = {};
  private maxMessageCount = 0;
  private strings = {};

  constructor(){
    this.addActor("0:0", "Platform"); //add main actor 
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
      if(target == "0:0") return;

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
    return mapToArray(this.actors)
  }

  getMaxMessageSends() {
    return this.maxMessageCount
  }

  updateDataBin(data: DataView){
    var i = 0;
    while(i < data.byteLength){
      var typ = data.getInt8(i);
      i++;
      switch(typ){
        case 1: 
          var aid = (data.getInt32(i+4) + ':' + data.getInt32(i));
          //8 byte causal message id
          var type:number = data.getInt16(i+16); //type
          this.addActor(aid, this.strings[type]);
          i += 18;
          break;
        case 2:
          //8 byte promise id
          //8 byte causal message id
          i += 16;
          break;
        case 3:
          //8 byte promise id
          //8 byte resolving message id
          i += 16;
          break;
        case 4:
          //8 byte promise id
          //8 byte chained promise id
          i += 16;
          break;
        case 5:
          var num = data.getInt16(i); //num messages
          //8 byte message base id
          var rec = (data.getInt32(i+14) + ':' + data.getInt32(i+10)); //receiver id
          i += 18;
          var j;
          for(j = 0; j < num; j++){
            var sender = (data.getInt32(i+4) + ':' + data.getInt32(i)); //sender id
            //8 byte causal message id
            var sym = data.getInt16(i+16); //selector
            this.addMessage(sender, rec);
            i += 18;
          }
          break;
        case 6:
          var thread = data.getInt8(i); //Thread
          //8 byte timestamp
          i += 9;
          break;
      }
    }
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
