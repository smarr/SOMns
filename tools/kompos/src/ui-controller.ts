/* jshint -W097 */
"use strict";

import {Controller}   from "./controller";
import {Debugger}     from "./debugger";
import {SourceMessage, SymbolMessage, StoppedMessage, StackTraceResponse,
  SectionBreakpointType, ScopesResponse, VariablesResponse } from "./messages";
import {LineBreakpoint, MessageBreakpoint,
  createLineBreakpoint, createMsgBreakpoint} from "./breakpoints";
import {dbgLog}       from "./source";
import {displayMessageHistory, resetLinks, updateStrings, updateData} from "./visualizations";
import {View}         from "./view";
import {VmConnection} from "./vm-connection";

/**
 * The controller binds the domain model and the views, and mediates their
 * interaction.
 */
export class UiController extends Controller {
  private dbg: Debugger;
  private view: View;

  constructor(dbg, view, vmConnection: VmConnection) {
    super(vmConnection);
    this.dbg = dbg;
    this.view = view;
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
    const bps = this.dbg.getEnabledBreakpoints();
    dbgLog("Send breakpoints: " + bps.length);
    this.vmConnection.sendInitialBreakpoints(bps.map(b => b.data));
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
      const bps = this.dbg.getEnabledBreakpointsForSource(source.name);
      for (const bp of bps) {
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

  public onStoppedEvent(msg: StoppedMessage) {
    this.dbg.setSuspended(msg.activityId);
    this.vmConnection.requestActivityList();
    this.vmConnection.requestStackTrace(msg.activityId);
  }

  public onStackTrace(msg: StackTraceResponse) {
    this.vmConnection.requestScope(msg.stackFrames[0].id);
    this.view.switchDebuggerToSuspendedState();
    this.view.displayStackTrace(
      msg, this.dbg.getSourceId(msg.stackFrames[0].sourceUri));
  }

  public onScopes(msg: ScopesResponse) {
    for (let s of msg.scopes) {
      this.vmConnection.requestVariables(s.variablesReference);
      this.view.displayScope(s);
    }
  }

  public onVariables(msg: VariablesResponse) {
    this.view.displayVariables(msg.variablesReference, msg.variables);
  }

  onSymbolMessage(msg: SymbolMessage) {
    updateStrings(msg);
  }

  onTracingData(data: DataView) {
    updateData(data);
    displayMessageHistory();
  }

  onUnknownMessage(msg: any) {
    dbgLog("[WS] unknown message of type:" + msg.type);
  }

  private toggleBreakpoint(key, newBp) {
    const sourceId = this.view.getActiveSourceId();
    const source   = this.dbg.getSource(sourceId);

    let breakpoint = this.dbg.getBreakpoint(source, key, newBp);
    breakpoint.toggle();

    this.vmConnection.updateBreakpoint(breakpoint.data);
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

    const id = sectionId + ":async-rcv",
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
    this.vmConnection.sendDebuggerAction("resume", this.dbg.activityId);
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
    this.vmConnection.sendDebuggerAction("stepInto", this.dbg.activityId);
  }

  stepOver() {
    if (!this.dbg.isSuspended()) { return; }
    this.dbg.setResumed();
    this.view.onContinueExecution();
    this.vmConnection.sendDebuggerAction("stepOver", this.dbg.activityId);
  }

  returnFromExecution() {
    if (!this.dbg.isSuspended()) { return; }
    this.dbg.setResumed();
    this.view.onContinueExecution();
    this.vmConnection.sendDebuggerAction("return", this.dbg.activityId);
  }
}
