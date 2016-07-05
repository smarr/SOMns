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
  this.dbg.suspended = false;
  this.view.onConnect();
  var bps = this.dbg.getEnabledBreakpoints();
  dbgLog("Send breakpoints: " + bps.length);
  this.vmConnection.sendInitialBreakpoints(bps);
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
  this.dbg.addSections(msg);
  this.view.displaySources(msg);

  for (var sId in msg.sources) {
    var source = msg.sources[sId];
    var bps = this.dbg.getEnabledBreakpointsForSource(source.name);
    for (var bp of bps) {
      this.view.updateBreakpoint(bp);
    }
  }
};

Controller.prototype.onExecutionSuspension = function (msg) {
  this.dbg.setSuspended(msg.id);
  this.view.switchDebuggerToSuspendedState();

  var dbg = this.dbg;
  this.view.displaySuspendEvent(msg, function (id) {return dbg.getSource(id);});
};

Controller.prototype.onMessageHistory = function (msg) {
  displayMessageHistory(msg.messageHistory);
};

Controller.prototype.onUnknownMessage = function (msg) {
  dbgLog("[WS] unknown message of type:" + msg.type);
};

Controller.prototype.toggleBreakpoint = function (key, newBp) {
  var sourceId = this.view.getActiveSourceId();
  var source   = this.dbg.getSource(sourceId);

  var breakpoint = this.dbg.getBreakpoint(source, key, newBp);
  breakpoint.toggle();

  this.vmConnection.updateBreakpoint(breakpoint);
  return breakpoint;
};

Controller.prototype.onToggleLineBreakpoint = function (line, clickedSpan) {
  dbgLog("updateBreakpoint");

  var breakpoint = this.toggleBreakpoint(line,
    function (source) { return new LineBreakpoint(source, line, clickedSpan); });

  this.view.updateLineBreakpoint(breakpoint);
};

Controller.prototype.onToggleMessageSendBreakpoint = function (e) {
  dbgLog("onToggleMessageSendBreakpoint");

  var sectionId   = e.currentTarget.id,
    sourceSection = this.dbg.getSection(sectionId),
    breakpoint    = this.toggleBreakpoint(sectionId,
      function (source) { return new SendBreakpoint(source, sourceSection); });

  this.view.updateSendBreakpoint(breakpoint);
};

Controller.prototype.resumeExecution = function () {
  this.vmConnection.sendDebuggerAction('resume', this.dbg.lastSuspendEventId);
  this.view.onContinueExecution();
};

Controller.prototype.pauseExecution = function () {

};

Controller.prototype.stopExecution = function () {

};

Controller.prototype.stepInto = function () {
  this.dbg.setResumed();
  this.view.onContinueExecution();
  this.vmConnection.sendDebuggerAction('stepInto', this.dbg.lastSuspendEventId);
};

Controller.prototype.stepOver = function () {
  this.dbg.setResumed();
  this.view.onContinueExecution();
  this.vmConnection.sendDebuggerAction('stepOver', this.dbg.lastSuspendEventId);
};

Controller.prototype.returnFromExecution = function () {
  this.dbg.setResumed();
  this.view.onContinueExecution();
  this.vmConnection.sendDebuggerAction('return', this.dbg.lastSuspendEventId);
};
