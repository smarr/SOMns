/* jshint -W097 */
"use strict";

import * as WebSocket from 'ws';

import {Controller} from './controller';
import {Message, Respond} from './messages';
import {Breakpoint} from './breakpoints';

/**
 * Encapsulates the connection to the VM via a web socket and encodes
 * the communication protocol, currently using JSON.
 */
export class VmConnection {
  private socket: WebSocket;
  private controller: Controller;

  constructor() {
    this.socket = null;
    this.controller = null;
  }

  setController(controller: Controller) {
    this.controller = controller;
  }

  isConnected() {
    return this.socket !== null && this.socket.readyState === WebSocket.OPEN;
  }

  connect() {
    console.assert(this.socket === null || this.socket.readyState === WebSocket.CLOSED);
    this.socket = new WebSocket("ws://localhost:7977");

    var controller = this.controller;
    this.socket.onopen = function (e) {
      controller.onConnect();
    };

    this.socket.onclose = function (e) {
      controller.onClose();
    };

    this.socket.onerror = function (e) {
      controller.onError();
    };

    this.socket.onmessage = function (e) {
      var data: Message = JSON.parse(e.data);

      switch (data.type) {
        case "source":
          controller.onReceivedSource(data);
          break;
        case "suspendEvent":
          controller.onExecutionSuspension(data);
          break;
        case "messageHistory":
          controller.onMessageHistory(data);
          break;
        default:
          controller.onUnknownMessage(data);
          break;
      }
    };
  }

  disconnect() {
    console.assert(this.isConnected());
  }

  sendInitialBreakpoints(breakpoints: Breakpoint[]) {
    this.send({
      action: "initialBreakpoints",
      breakpoints: breakpoints.map(b => b.data),
      debuggerProtocol: false
    });
  }

  updateBreakpoint(breakpoint: Breakpoint) {
    this.send({
      action: "updateBreakpoint",
      breakpoint: breakpoint.data
    });
  };

  sendDebuggerAction(action, lastSuspendEventId) {
    this.send({
      action: action,
      suspendEvent: lastSuspendEventId});
  }

  private send(respond: Respond) {
    this.socket.send(JSON.stringify(respond));
  }
}
