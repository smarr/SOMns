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
    this.dbg.addSources(msg);
  }

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
    this.vmConnection.requestActivityList();
    this.vmConnection.requestStackTrace(msg.activityId);
    this.dbg.setSuspended(msg.activityId);
  }

  public onThreads(msg: ThreadsResponse) {
    const newActivities = this.dbg.addActivities(msg.threads);
    this.view.addActivities(newActivities);
  }

  public onStackTrace(msg: StackTraceResponse) {
    const requestedId = msg.stackFrames[0].id;
    this.vmConnection.requestScope(msg.stackFrames[0].id);
    this.view.switchActivityDebuggerToSuspendedState(msg.activityId);
    this.view.displayStackTrace(msg, requestedId);

    const sourceId = this.dbg.getSourceId(msg.stackFrames[0].sourceUri);
    const source = this.dbg.getSource(sourceId);
    this.view.displaySource(msg.activityId, source, sourceId);
  }

  public onScopes(msg: ScopesResponse) {
    for (let s of msg.scopes) {
      this.vmConnection.requestVariables(s.variablesReference);
      this.view.displayScope(msg.variablesReference, s);
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

  resumeExecution(activityId: number) {
    if (!this.dbg.isSuspended(activityId)) { return; }
    this.dbg.setResumed(activityId);
    this.vmConnection.sendDebuggerAction("resume", activityId);
    this.view.onContinueExecution(activityId);
  }

  pauseExecution(activityId: number) {
    if (this.dbg.isSuspended(activityId)) { return; }
    // TODO
  }

  /** End program, typically terminating it completely. */
  stopExecution() {
    // TODO
  }

  stepInto(activityId: number) {
    if (!this.dbg.isSuspended(activityId)) { return; }
    this.dbg.setResumed(activityId);
    this.view.onContinueExecution(activityId);
    this.vmConnection.sendDebuggerAction("stepInto", activityId);
  }

  stepOver(activityId: number) {
    if (!this.dbg.isSuspended(activityId)) { return; }
    this.dbg.setResumed(activityId);
    this.view.onContinueExecution(activityId);
    this.vmConnection.sendDebuggerAction("stepOver", activityId);
  }

  returnFromExecution(activityId: number) {
    if (!this.dbg.isSuspended(activityId)) { return; }
    this.dbg.setResumed(activityId);
    this.view.onContinueExecution(activityId);
    this.vmConnection.sendDebuggerAction("return", activityId);
  }
}
