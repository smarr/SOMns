/* jshint -W097 */
"use strict";

import {Controller}   from "./controller";
import {Debugger}     from "./debugger";
import {ActivityNode} from "./history-data";
import {SourceMessage, SymbolMessage, StoppedMessage, StackTraceResponse,
  ScopesResponse, VariablesResponse, ProgramInfoResponse, InitializationResponse,
  Activity, Source } from "./messages";
import {LineBreakpoint, SectionBreakpoint, getBreakpointId,
  createLineBreakpoint, createSectionBreakpoint} from "./breakpoints";
import {dbgLog}       from "./source";
import {View, getActivityIdFromView, getSourceIdFrom, getSourceIdFromSection} from "./view";
import {VmConnection} from "./vm-connection";

// TODO: remove
import {SteppingType} from "../tests/somns-support";

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
    this.view.reset();
    this.actProm = {};
    this.actPromResolve = {};
  }

  public toggleConnection() {
    if (this.vmConnection.isConnected()) {
      this.vmConnection.disconnect();
    } else {
      this.vmConnection.connect();
    }
  }

  public onConnect() {
    dbgLog("[WS] open");
    this.reset();
    this.view.onConnect();
    const bps = this.dbg.getEnabledBreakpoints();
    dbgLog("Send breakpoints: " + bps.length);
    this.vmConnection.sendInitializeConnection(bps.map(b => b.data));
    this.vmConnection.requestProgramInfo();
  }

  public onClose() {
    dbgLog("[WS] close");
    this.view.onClose();
  }

  public onError() {
    dbgLog("[WS] error");
  }

  public onReceivedSource(msg: SourceMessage) {
    this.dbg.addSource(msg);
  }

  public overActivity(act: ActivityNode, rect: SVGRectElement) {
    this.view.overActivity(act, rect);
  }

  public outActivity(act: ActivityNode, rect: SVGRectElement) {
    this.view.outActivity(act, rect);
  }

  public toggleCodePane(actId: string) {
    const expanded = this.view.isCodePaneExpanded(actId);

    if (expanded) {
      this.view.markCodePaneClosed(actId);
    } else {
      const aId      = getActivityIdFromView(actId);
      const activity = this.dbg.getActivity(aId);
      const sId      = this.dbg.getSourceId(activity.origin.uri);
      const source   = this.dbg.getSource(sId);

      console.assert(aId === activity.id);
      this.view.displaySource(activity, source, sId);
    }
  }

  private ensureBreakpointsAreIndicated(sourceUri) {
    const bps = this.dbg.getEnabledBreakpointsForSource(sourceUri);
    for (const bp of bps) {
      if (bp.data.type === "LineBreakpoint") {
        this.view.updateLineBreakpoint(<LineBreakpoint> bp);
      } else {
        this.view.updateSectionBreakpoint(<SectionBreakpoint> bp);
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

  public onStoppedMessage(msg: StoppedMessage) {
    this.vmConnection.requestTraceData();
    this.ensureActivityPromise(msg.activityId);
    this.vmConnection.requestStackTrace(msg.activityId);
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
    const topFrameId = msg.stackFrames[0].id;
    this.ensureActivityPromise(msg.activityId);

    this.actProm[msg.activityId].then((act: Activity) => {
      this.dbg.getActivity(msg.activityId).running = false;

      console.assert(act.id === msg.activityId);
      this.vmConnection.requestScope(topFrameId);

      this.view.switchActivityDebuggerToSuspendedState(act);

      const sourceId = this.dbg.getSourceId(msg.stackFrames[0].sourceUri);
      const source = this.dbg.getSource(sourceId);

      const newSource = this.view.displaySource(act, source, sourceId);
      this.view.displayStackTrace(sourceId, msg, topFrameId, act);
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

  public onInitializationResponse(msg: InitializationResponse) {
    this.dbg.setCapabilities(msg.capabilities);
    this.view.setCapabilities(msg.capabilities);
  }

  public onVariables(msg: VariablesResponse) {
    this.view.displayVariables(msg.variablesReference, msg.variables);
  }

  public onSymbolMessage(msg: SymbolMessage) {
    this.view.updateStringData(msg);
  }

  public onTracingData(data: DataView) {
    this.newActivities(this.view.updateTraceData(data));
    this.view.displaySystemView();
  }

  public onUnknownMessage(msg: any) {
    dbgLog("[WS] unknown message of type:" + msg.type);
  }

  private toggleBreakpoint(key: any, source: Source, newBp) {
    const breakpoint = this.dbg.getBreakpoint(source, key, newBp);
    breakpoint.toggle();

    this.vmConnection.updateBreakpoint(breakpoint.data);
    return breakpoint;
  }

  public onToggleLineBreakpoint(line: number, clickedSpan: Element) {
    dbgLog("updateBreakpoint");
    const parent     = <Element> clickedSpan.parentNode.parentNode;
    const sourceId   = getSourceIdFrom(parent.id);
    const source     = this.dbg.getSource(sourceId);
    const breakpoint = this.toggleBreakpoint(line, source,
        function () { return createLineBreakpoint(source, sourceId, line); });

    this.view.updateLineBreakpoint(<LineBreakpoint> breakpoint);
  }

  public onToggleSectionBreakpoint(sectionId: string, type: string) {
    dbgLog("section breakpoint: " + type);

    const id = getBreakpointId(sectionId, type),
      sourceSection = this.dbg.getSection(sectionId),
      sourceId      = getSourceIdFromSection(sectionId),
      source        = this.dbg.getSource(sourceId),
      breakpoint    = this.toggleBreakpoint(id, source, function () {
        return createSectionBreakpoint(source, sourceSection, sectionId, type); });

    this.view.updateSectionBreakpoint(<SectionBreakpoint> breakpoint);
  }

  public resumeExecution(actId: string) {
    const activityId = getActivityIdFromView(actId);
    const act = this.dbg.getActivity(activityId);

    if (act.running) { return; }
    act.running = true;
    this.vmConnection.sendDebuggerAction(SteppingType.RESUME, act);
    this.view.onContinueExecution(act);
  }

  public pauseExecution(actId: string) {
    const activityId = getActivityIdFromView(actId);
    const act = this.dbg.getActivity(activityId);
    if (!act.running) { return; }
    console.assert(false, "TODO");
  }

  /** End program, typically terminating it completely. */
  public stopExecution() {
    // TODO
  }

  public stepInto(actId: string) {
    const activityId = getActivityIdFromView(actId);
    const act = this.dbg.getActivity(activityId);
    if (act.running) { return; }
    act.running = true;
    this.view.onContinueExecution(act);
    this.vmConnection.sendDebuggerAction(SteppingType.STEP_INTO, act);
  }

  public stepOver(actId: string) {
    const activityId = getActivityIdFromView(actId);
    const act = this.dbg.getActivity(activityId);
    if (act.running) { return; }
    act.running = true;
    this.view.onContinueExecution(act);
    this.vmConnection.sendDebuggerAction(SteppingType.STEP_OVER, act);
  }

  public returnFromExecution(actId: string) {
    const activityId = getActivityIdFromView(actId);
    const act = this.dbg.getActivity(activityId);
    if (act.running) { return; }
    act.running = true;
    this.view.onContinueExecution(act);
    this.vmConnection.sendDebuggerAction(SteppingType.RETURN, act);
  }
}
