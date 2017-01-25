/* jshint -W097 */
"use strict";

import * as WebSocket from "ws";

import {Controller} from "./controller";
import {Message, Respond} from "./messages";
import {Breakpoint} from "./breakpoints";

/**
 * Encapsulates the connection to the VM via a web socket and encodes
 * the communication protocol, currently using JSON.
 */
export class VmConnection {
  private socket: WebSocket;
  private binarySocket: WebSocket;
  private controller: Controller;

  constructor() {
    this.socket = null;
    this.binarySocket = null;
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

    console.assert(this.binarySocket === null || this.binarySocket.readyState === WebSocket.CLOSED);
    this.binarySocket = new WebSocket("ws://localhost:7978");
    (<any> this.binarySocket).binaryType = "arraybuffer"; // workaround, typescript dosn't recognize this property

    const controller = this.controller;
    this.socket.onopen = function () {
      controller.onConnect();
    };

    this.socket.onclose = function () {
      controller.onClose();
    };

    this.socket.onerror = function () {
      controller.onError();
    };

    this.socket.onmessage = function (e) {
      const data: Message = JSON.parse(e.data);

      switch (data.type) {
        case "source":
          controller.onReceivedSource(data);
          break;
        case "suspendEvent":
          controller.onExecutionSuspension(data);
          break;
        case "symbolMessage":
          controller.onSymbolMessage(data);
          break;
        default:
          controller.onUnknownMessage(data);
          break;
      }
    };

    this.binarySocket.onmessage = function (e) {
      const data: DataView = new DataView(e.data);
      controller.onTracingData(data);
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
