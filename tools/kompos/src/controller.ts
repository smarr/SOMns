/* jshint -W097 */
"use strict";

import {dbgLog} from './source';
import {displayMessageHistory} from './visualizations';
import {VmConnection} from './vm-connection';

import {SourceMessage, SuspendEventMessage, MessageHistoryMessage,
  SendBreakpointType, createLineBreakpoint, createSendBreakpoint,
  createAsyncMethodRcvBreakpoint} from './messages';

/**
 * The controller binds the domain model and the views, and mediates their
 * interaction.
 */
export class Controller {
  private dbg;
  private view;
  private vmConnection: VmConnection;

  constructor(dbg, view, vmConnection: VmConnection) {
    this.dbg = dbg;
    this.view = view;
    this.vmConnection = vmConnection;

    vmConnection.setController(this);
  }

  toggleConnection() {
    if (this.vmConnection.isConnected()) {
      this.vmConnection.disconnect();
    } else {
      this.vmConnection.connect();
    }
  }

  onConnect() {
    dbgLog("[WS] open");
    this.dbg.suspended = false;
    this.view.onConnect();
    var bps = this.dbg.getEnabledBreakpoints();
    dbgLog("Send breakpoints: " + bps.length);
    this.vmConnection.sendInitialBreakpoints(bps);
  }

  onClose() {
    dbgLog("[WS] close");
    this.view.onClose();
  }

  onError() {
    dbgLog("[WS] error");
  }

  onReceivedSource(msg: SourceMessage) {
    this.dbg.addSources(msg);
    this.dbg.addSections(msg);
    this.dbg.addMethods(msg);

    this.view.displaySources(msg);

    for (var sId in msg.sources) {
      var source = msg.sources[sId];
      var bps = this.dbg.getEnabledBreakpointsForSource(source.name);
      for (var bp of bps) {
        this.view.updateBreakpoint(bp);
      }
    }
  }

  onExecutionSuspension(msg: SuspendEventMessage) {
    this.dbg.setSuspended(msg.id);
    this.view.switchDebuggerToSuspendedState();

    var dbg = this.dbg;
    this.view.displaySuspendEvent(msg, function (id) {
      return [dbg.getSource(id), dbg.getMethods(id)];
    });
  }

  onMessageHistory(msg: MessageHistoryMessage) {
    displayMessageHistory(msg.messageHistory);
  }

  onUnknownMessage(msg: any) {
    dbgLog("[WS] unknown message of type:" + msg.type);
  }

  toggleBreakpoint(key, newBp) {
    var sourceId = this.view.getActiveSourceId();
    var source   = this.dbg.getSource(sourceId);

    let breakpoint = this.dbg.getBreakpoint(source, key, newBp);
    breakpoint.toggle();

    this.vmConnection.updateBreakpoint(breakpoint);
    return breakpoint;
  }

  onToggleLineBreakpoint(line: number, clickedSpan) {
    dbgLog("updateBreakpoint");

    var breakpoint = this.toggleBreakpoint(line,
      function (source) { return createLineBreakpoint(source, line, clickedSpan); });

    this.view.updateLineBreakpoint(breakpoint);
  }

  onToggleSendBreakpoint(sectionId: string, role: SendBreakpointType) {
    dbgLog("--send-op breakpoint: " + role);

    var id = sectionId + ":" + role,
      sourceSection = this.dbg.getSection(sectionId),
      breakpoint    = this.toggleBreakpoint(id, function (source) {
        return createSendBreakpoint(source, sourceSection, role); });

    this.view.updateSendBreakpoint(breakpoint);
  }

  onToggleMethodAsyncRcvBreakpoint(sectionId: string) {
    dbgLog("async method rcv bp: " + sectionId);

    var id = sectionId + ":async-rcv",
      sourceSection = this.dbg.getSection(sectionId),
      breakpoint    = this.toggleBreakpoint(id, function (source) {
        return createAsyncMethodRcvBreakpoint(source, sourceSection); });

    this.view.updateAsyncMethodRcvBreakpoint(breakpoint);
  }

  resumeExecution() {
    this.vmConnection.sendDebuggerAction('resume', this.dbg.lastSuspendEventId);
    this.view.onContinueExecution();
  }

  pauseExecution() {
    // TODO
  }

  stopExecution() {
    // TODO
  }

  stepInto() {
    this.dbg.setResumed();
    this.view.onContinueExecution();
    this.vmConnection.sendDebuggerAction('stepInto', this.dbg.lastSuspendEventId);
  }

  stepOver() {
    this.dbg.setResumed();
    this.view.onContinueExecution();
    this.vmConnection.sendDebuggerAction('stepOver', this.dbg.lastSuspendEventId);
  }

  returnFromExecution() {
    this.dbg.setResumed();
    this.view.onContinueExecution();
    this.vmConnection.sendDebuggerAction('return', this.dbg.lastSuspendEventId);
  }
}
