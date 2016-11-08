/* jshint -W097 */
'use strict';

import {IdMap, MessageHistoryMessage, MessageData, FarRefData} from './messages';
import {dbgLog} from './source';

var horizontalDistance = 100,
  verticalDistance = 100;

export class HistoryData {
  private actors = {};
  private actorsPerType: IdMap<number> = {};
  private messages: IdMap<IdMap<number>> = {};
  private maxMessageCount = 0;

  private updateActors(msgHist: MessageHistoryMessage) {
    for (var aId in msgHist.actors) {
      var actor = msgHist.actors[aId];
      hashAtInc(this.actorsPerType, actor.typeName, 1);

      var selfSends = hasSelfSends(actor.id, msgHist.messages);
      var node = {
        id: actor.id,
        name: actor.typeName,
        reflexive: selfSends,
        x: horizontalDistance + horizontalDistance * this.actorsPerType[actor.typeName],
        y: verticalDistance * Object.keys(this.actorsPerType).length,
        type: actor.typeName
      };

      this.actors[actor.id] = node;
    }
  }

  private countMessagesSenderToReceiver(msgHist: MessageHistoryMessage) {
    for (let msg of msgHist.messages) {
      if (!this.messages.hasOwnProperty(msg.senderId)) {
        this.messages[msg.senderId] = {};
      }
      if (msg.senderId !== msg.targetId) {
        hashAtInc(this.messages[msg.senderId], msg.targetId, 1);
      }
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

  updateData(msgHist: MessageHistoryMessage) {
    dbgLog("[Update] #msg: " + msgHist.messages.length + " #act: " + msgHist.actors.length);
    this.updateActors(msgHist);
    this.countMessagesSenderToReceiver(msgHist);
  }
}

function hasSelfSends(actorId: string, messages: MessageData[]) {
  for (var i in messages) {
    if (messages[i].senderId === actorId) {
      return true;
    }
  }
  return false;
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
