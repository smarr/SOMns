/* jshint -W097 */
"use strict";

/** WebSocket connection states */
var CONNECTING = 0,
  OPEN = 1,
  CLOSING = 2,
  CLOSED = 3;

/**
 * Encapsulates the connection to the VM via a web socket and encodes
 * the communication protocol, currently using JSON.
 * @constructor
 */
function VmConnection() {
  this.socket = null;
  this.controller = null;
}

VmConnection.prototype.setController = function (controller) {
  this.controller = controller;
};

VmConnection.prototype.isConnected = function () {
  return this.socket !== null && this.socket.readyState === OPEN;
};

VmConnection.prototype.connect = function () {
  console.assert(this.socket === null || this.socket.readyState === CLOSED);
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
    var data = JSON.parse(e.data);

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
};

VmConnection.prototype.disconnect = function () {
  console.assert(this.isConnected());
};

VmConnection.prototype.sendInitialBreakpoints = function (breakpoints) {
  var bps = [];
  for (var bp of breakpoints) {
    bps.push(bp.toJsonObj());
  }
  this.socket.send(JSON.stringify({
    action: "initialBreakpoints",
    breakpoints: bps
  }));
};

VmConnection.prototype.updateBreakpoint = function (breakpoint) {
  this.socket.send(JSON.stringify({
    action: "updateBreakpoint",
    breakpoint: breakpoint.toJsonObj()
  }));
};

VmConnection.prototype.sendDebuggerAction = function (action, lastSuspendEventId) {
  this.socket.send(JSON.stringify({
    action: action,
    suspendEvent: lastSuspendEventId}));
};
