/* jshint -W097 */
"use strict";

/* globals dbgLog */

/**
 * The controller binds the domain model and the views, and mediates their
 * interaction.
 * 
 * @param {Debugger} dbg
 * @param {View} view
 * @param {VmConnection} vmConnection
 * @constructor
 */
function Controller(dbg, view, vmConnection) {
  this.dbg = dbg;
  this.view = view;
  this.vmConnection = vmConnection;

  vmConnection.setController(this);
}

Controller.prototype.toggleConnection = function() {
  if (this.vmConnection.isConnected()) {
    this.vmConnection.disconnect();
  } else {
    this.vmConnection.connect();
  }
};

Controller.prototype.onConnect = function () {
  dbgLog("[WS] open");
  this.view.onConnect();
};

Controller.prototype.onClose = function () {
  dbgLog("[WS] close");
  this.view.onClose();
};

Controller.prototype.onError = function () {
  dbgLog("[WS] error");
};

Controller.prototype.onReceivedSource = function (msg) {
  this.dbg.addSources(msg);
  this.view.displaySources(msg);
};

Controller.prototype.onExecutionSuspension = function (msg) {
  this.dbg.setSuspended(msg.id);

  var dbg = this.dbg;
  this.view.displaySuspendEvent(msg, function (id) {return dbg.getSource(id);});
};

Controller.prototype.onMessageHistory = function (msg) {
  displayMessageHistory(msg.messageHistory);
};

Controller.prototype.onUnknownMessage = function (msg) {
  dbgLog("[WS] unknown message of type:" + msg.type);
};

Controller.prototype.onToggleBreakpoint = function (line, clickedSpan) {
  dbgLog("updateBreakpoint");

  var sourceId = this.view.getActiveSourceId();
  var source   = this.dbg.getSource(sourceId);

  var breakpoint = this.dbg.getBreakpoint(source, line, clickedSpan);
  breakpoint.toggle();

  this.vmConnection.updateBreakpoint(breakpoint);
  this.view.toggleBreakpoint(breakpoint);
};
