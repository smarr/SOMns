/* jshint -W097 */
"use strict";

import {Debugger}     from './debugger';
import {SourceMessage, SuspendEventMessage, SymbolMessage,
  SectionBreakpointType} from './messages';
import {LineBreakpoint, MessageBreakpoint,
  createLineBreakpoint, createMsgBreakpoint} from './breakpoints';
import {dbgLog}       from './source';
import {displayMessageHistory, resetLinks, updateStrings, updateData} from './visualizations';
import {View}         from './view';
import {VmConnection} from './vm-connection';

/**
 * The controller binds the domain model and the views, and mediates their
 * interaction.
 */
export class Controller {
  private dbg: Debugger;
  private view: View;
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
    resetLinks();
    this.dbg.setResumed();
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
    let newSources = this.dbg.addSources(msg);
    this.view.displaySources(newSources);

    for (let source of msg.sources) {
      var bps = this.dbg.getEnabledBreakpointsForSource(source.name);
      for (var bp of bps) {
        switch (bp.data.type) {
          case "LineBreakpoint":
            this.view.updateLineBreakpoint(<LineBreakpoint> bp);
            break;
          case "MessageSenderBreakpoint":
          case "MessageReceiverBreakpoint":
            this.view.updateSendBreakpoint(<MessageBreakpoint> bp);
            break;
          case "AsyncMessageReceiverBreakpoint":
            this.view.updateAsyncMethodRcvBreakpoint(<MessageBreakpoint> bp);
            break;
          case "PromiseResolverBreakpoint" || "PromiseResolutionBreakpoint":
            this.view.updatePromiseBreakpoint(<MessageBreakpoint> bp);
            break;
          default:
            console.error("Unsupported breakpoint type: " + JSON.stringify(bp.data));
            break;
        }
      }
    }
  }

  onExecutionSuspension(msg: SuspendEventMessage) {
    this.dbg.setSuspended(msg.id);
    this.view.switchDebuggerToSuspendedState();
    this.view.displaySuspendEvent(
      msg, this.dbg.getSourceId(msg.stack[0].sourceSection.uri));
  }

  onSymbolMessage(msg: SymbolMessage){
    updateStrings(msg);
  }

  onTracingData(data: DataView){
    updateData(data);
    displayMessageHistory();
  }

  onUnknownMessage(msg: any) {
    dbgLog("[WS] unknown message of type:" + msg.type);
  }

  private toggleBreakpoint(key, newBp) {
    var sourceId = this.view.getActiveSourceId();
    var source   = this.dbg.getSource(sourceId);

    let breakpoint = this.dbg.getBreakpoint(source, key, newBp);
    breakpoint.toggle();

    this.vmConnection.updateBreakpoint(breakpoint);
    return breakpoint;
  }

  onToggleLineBreakpoint(line: number, clickedSpan) {
    dbgLog("updateBreakpoint");

    let dbg = this.dbg,
      breakpoint = this.toggleBreakpoint(line,
        function (source) { return createLineBreakpoint(source,
          dbg.getSourceId(source.uri), line, clickedSpan); });

    this.view.updateLineBreakpoint(<LineBreakpoint> breakpoint);
  }

  onToggleSendBreakpoint(sectionId: string, type: SectionBreakpointType) {
    dbgLog("send-op breakpoint: " + type);

    let id = sectionId + ":" + type,
      sourceSection = this.dbg.getSection(sectionId),
      breakpoint    = this.toggleBreakpoint(id, function (source) {
        return createMsgBreakpoint(source, sourceSection, sectionId, type); });

    this.view.updateSendBreakpoint(<MessageBreakpoint> breakpoint);
  }

  onToggleMethodAsyncRcvBreakpoint(sectionId: string) {
    dbgLog("async method rcv bp: " + sectionId);

    var id = sectionId + ":async-rcv",
      sourceSection = this.dbg.getSection(sectionId),
      breakpoint    = this.toggleBreakpoint(id, function (source) {
        return createMsgBreakpoint(source, sourceSection, sectionId, "AsyncMessageReceiverBreakpoint"); });

    this.view.updateAsyncMethodRcvBreakpoint(<MessageBreakpoint> breakpoint);
  }

  onTogglePromiseBreakpoint(sectionId: string, type: SectionBreakpointType) {
    dbgLog("promise breakpoint: " + type);

     let id = sectionId + ":" + type,
      sourceSection = this.dbg.getSection(sectionId),
      breakpoint    = this.toggleBreakpoint(id, function (source) {
        return createMsgBreakpoint(source, sourceSection, sectionId, type); });

    this.view.updatePromiseBreakpoint(<MessageBreakpoint> breakpoint);
  }

  resumeExecution() {
    if (!this.dbg.isSuspended()) { return; }
    this.dbg.setResumed();
    this.vmConnection.sendDebuggerAction('resume', this.dbg.lastSuspendEventId);
    this.view.onContinueExecution();
  }

  pauseExecution() {
    if (this.dbg.isSuspended()) { return; }
    // TODO
  }

  stopExecution() {
    // TODO
  }

  stepInto() {
    if (!this.dbg.isSuspended()) { return; }
    this.dbg.setResumed();
    this.view.onContinueExecution();
    this.vmConnection.sendDebuggerAction('stepInto', this.dbg.lastSuspendEventId);
  }

  stepOver() {
    if (!this.dbg.isSuspended()) { return; }
    this.dbg.setResumed();
    this.view.onContinueExecution();
    this.vmConnection.sendDebuggerAction('stepOver', this.dbg.lastSuspendEventId);
  }

  returnFromExecution() {
    if (!this.dbg.isSuspended()) { return; }
    this.dbg.setResumed();
    this.view.onContinueExecution();
    this.vmConnection.sendDebuggerAction('return', this.dbg.lastSuspendEventId);
  }
}
