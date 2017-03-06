/* jshint -W097 */
"use strict";

import {Controller}   from "./controller";
import {Debugger}     from "./debugger";
import {SourceMessage, SymbolMessage, StoppedMessage, StackTraceResponse,
  SectionBreakpointType, ScopesResponse, VariablesResponse, ProgramInfoResponse,
  Activity, Source } from "./messages";
import {LineBreakpoint, MessageBreakpoint, getBreakpointId,
  createLineBreakpoint, createMsgBreakpoint} from "./breakpoints";
import {dbgLog}       from "./source";
import {displayMessageHistory, resetLinks, updateStrings, updateData} from "./visualizations";
import {View, getActivityIdFromView, getSourceIdFrom, getSourceIdFromSection} from "./view";
import {VmConnection} from "./vm-connection";

/**
 * The controller binds the domain model and the views, and mediates their
 * interaction.
 */
export class UiController extends Controller {
  private dbg: Debugger;
  private view: View;

  private actProm = {};
  private actPromResolve = {};

  constructor(dbg, view, vmConnection: VmConnection) {
    super(vmConnection);
    this.dbg = dbg;
    this.view = view;
  }

  private reset() {
    this.actProm = {};
    this.actPromResolve = {};
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
    this.reset();
    this.view.resetActivities();
    this.view.onConnect();
    const bps = this.dbg.getEnabledBreakpoints();
    dbgLog("Send breakpoints: " + bps.length);
    this.vmConnection.sendInitialBreakpoints(bps.map(b => b.data));
    this.vmConnection.requestProgramInfo();
  }

  onClose() {
    dbgLog("[WS] close");
    this.view.onClose();
  }

  onError() {
    dbgLog("[WS] error");
  }

  onReceivedSource(msg: SourceMessage) {
    this.dbg.addSource(msg);
  }

  private ensureBreakpointsAreIndicated(sourceUri) {
    const bps = this.dbg.getEnabledBreakpointsForSource(sourceUri);
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

  private ensureActivityPromise(actId: number) {
    if (!this.actProm[actId]) {
      this.actProm[actId] = new Promise((resolve, _reject) => {
        this.actPromResolve[actId] = resolve;
      });
    }
  }

  public onStoppedEvent(msg: StoppedMessage) {
    this.vmConnection.requestTraceData();
    this.ensureActivityPromise(msg.activityId);
    this.vmConnection.requestStackTrace(msg.activityId);
    this.dbg.setSuspended(msg.activityId);
  }

  public newActivities(newActivities: Activity[]) {
    this.dbg.addActivities(newActivities);
    this.view.addActivities(newActivities);

    for (const act of newActivities) {
      this.ensureActivityPromise(act.id);
      this.actPromResolve[act.id](act);
    }
  }

  public onStackTrace(msg: StackTraceResponse) {
    const requestedId = msg.stackFrames[0].id;
    this.ensureActivityPromise(msg.activityId);

    this.actProm[msg.activityId].then(act => {
      console.assert(act.id === msg.activityId);
      this.vmConnection.requestScope(requestedId);

      this.view.switchActivityDebuggerToSuspendedState(msg.activityId);

      const sourceId = this.dbg.getSourceId(msg.stackFrames[0].sourceUri);
      const source = this.dbg.getSource(sourceId);

      const newSource = this.view.displaySource(msg.activityId, source, sourceId);
      this.view.displayStackTrace(sourceId, msg, requestedId);
      if (newSource) {
        this.ensureBreakpointsAreIndicated(sourceId);
      }
    });
  }

  public onScopes(msg: ScopesResponse) {
    for (let s of msg.scopes) {
      this.vmConnection.requestVariables(s.variablesReference);
      this.view.displayScope(msg.variablesReference, s);
    }
  }

  public onProgramInfo(msg: ProgramInfoResponse) {
    this.view.displayProgramArguments(msg.args);
  }

  public onVariables(msg: VariablesResponse) {
    this.view.displayVariables(msg.variablesReference, msg.variables);
  }

  onSymbolMessage(msg: SymbolMessage) {
    updateStrings(msg);
  }

  onTracingData(data: DataView) {
    updateData(data, this);
    displayMessageHistory();
  }

  onUnknownMessage(msg: any) {
    dbgLog("[WS] unknown message of type:" + msg.type);
  }

  private toggleBreakpoint(key: any, source: Source, newBp) {
    const breakpoint = this.dbg.getBreakpoint(source, key, newBp);
    breakpoint.toggle();

    this.vmConnection.updateBreakpoint(breakpoint.data);
    return breakpoint;
  }

  onToggleLineBreakpoint(line: number, clickedSpan: Element) {
    dbgLog("updateBreakpoint");
    const parent     = <Element> clickedSpan.parentNode.parentNode;
    const sourceId   = getSourceIdFrom(parent.id);
    const source     = this.dbg.getSource(sourceId);
    const breakpoint = this.toggleBreakpoint(line, source,
        function () { return createLineBreakpoint(source, sourceId, line); });

    this.view.updateLineBreakpoint(<LineBreakpoint> breakpoint);
  }

  onToggleSendBreakpoint(sectionId: string, type: SectionBreakpointType) {
    dbgLog("send-op breakpoint: " + type);

    const id = getBreakpointId(sectionId, type),
      sourceSection = this.dbg.getSection(sectionId),
      sourceId      = getSourceIdFromSection(sectionId),
      source        = this.dbg.getSource(sourceId),
      breakpoint    = this.toggleBreakpoint(id, source, function () {
        return createMsgBreakpoint(source, sourceSection, sectionId, type); });

    this.view.updateSendBreakpoint(<MessageBreakpoint> breakpoint);
  }

  public onToggleMethodAsyncRcvBreakpoint(sectionId: string) {
    dbgLog("async method rcv bp: " + sectionId);

    const id = getBreakpointId(sectionId, "AsyncMessageReceiverBreakpoint"),
      sourceSection = this.dbg.getSection(sectionId),
      sourceId      = getSourceIdFromSection(sectionId),
      source        = this.dbg.getSource(sourceId),
      breakpoint    = this.toggleBreakpoint(id, source, function () {
        return createMsgBreakpoint(source, sourceSection, sectionId, "AsyncMessageReceiverBreakpoint"); });

    this.view.updateAsyncMethodRcvBreakpoint(<MessageBreakpoint> breakpoint);
  }

  public onTogglePromiseBreakpoint(sectionId: string, type: SectionBreakpointType) {
    dbgLog("promise breakpoint: " + type);

     let id = getBreakpointId(sectionId, type),
      sourceSection = this.dbg.getSection(sectionId),
      sourceId      = getSourceIdFromSection(sectionId),
      source        = this.dbg.getSource(sourceId),
      breakpoint    = this.toggleBreakpoint(id, source, function () {
        return createMsgBreakpoint(source, sourceSection, sectionId, type); });

    this.view.updatePromiseBreakpoint(<MessageBreakpoint> breakpoint);
  }

  public resumeExecution(actId: string) {
    const activityId = getActivityIdFromView(actId);
    if (!this.dbg.isSuspended(activityId)) { return; }
    this.dbg.setResumed(activityId);
    this.vmConnection.sendDebuggerAction("resume", activityId);
    this.view.onContinueExecution(activityId);
  }

  public pauseExecution(actId: string) {
    const activityId = getActivityIdFromView(actId);
    if (this.dbg.isSuspended(activityId)) { return; }
    // TODO
  }

  /** End program, typically terminating it completely. */
  stopExecution() {
    // TODO
  }

  stepInto(actId: string) {
    const activityId = getActivityIdFromView(actId);
    if (!this.dbg.isSuspended(activityId)) { return; }
    this.dbg.setResumed(activityId);
    this.view.onContinueExecution(activityId);
    this.vmConnection.sendDebuggerAction("stepInto", activityId);
  }

  stepOver(actId: string) {
    const activityId = getActivityIdFromView(actId);
    if (!this.dbg.isSuspended(activityId)) { return; }
    this.dbg.setResumed(activityId);
    this.view.onContinueExecution(activityId);
    this.vmConnection.sendDebuggerAction("stepOver", activityId);
  }

  returnFromExecution(actId: string) {
    const activityId = getActivityIdFromView(actId);
    if (!this.dbg.isSuspended(activityId)) { return; }
    this.dbg.setResumed(activityId);
    this.view.onContinueExecution(activityId);
    this.vmConnection.sendDebuggerAction("return", activityId);
  }
}
