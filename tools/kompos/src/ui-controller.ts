/* jshint -W097 */
"use strict";

import { Controller } from "./controller";
import { Debugger } from "./debugger";
import {
  SourceMessage, SymbolMessage, StoppedMessage, StackTraceResponse,
  ScopesResponse, VariablesResponse, ProgramInfoResponse, InitializationResponse,
  Source, Method
} from "./messages";
import {
  LineBreakpoint, SectionBreakpoint, getBreakpointId,
  createLineBreakpoint, createSectionBreakpoint
} from "./breakpoints";
import { dbgLog } from "./source";
import { View, getActivityIdFromView, getSourceIdFrom, getSourceIdFromSection, getFullMethodName } from "./view";
import { VmConnection } from "./vm-connection";
import { Activity, ExecutionData, TraceDataUpdate } from "./execution-data";
import { ActivityNode } from "./system-view-data";
import { KomposMetaModel } from "./meta-model";

/**
 * The controller binds the domain model and the views, and mediates their
 * interaction.
 */
export class UiController extends Controller {
  private dbg: Debugger;
  private view: View;
  private data: ExecutionData;

  private actProm = {};
  private actPromResolve = {};

  constructor(vmConnection: VmConnection) {
    super(vmConnection);
    this.dbg = new Debugger();
    this.data = new ExecutionData();
    this.view = new View();
  }

  private reset() {
    this.data.reset();
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
    this.reset();
    this.view.onConnect();
    const bps = this.dbg.getEnabledBreakpoints();
    dbgLog("Send breakpoints: " + bps.length);
    this.vmConnection.sendInitializeConnection(bps.map(b => b.data));
    this.vmConnection.requestProgramInfo();
  }

  public onClose() {
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
      const aId = getActivityIdFromView(actId);
      const activity = this.data.getActivity(aId);
      const sId = this.dbg.getSourceId(activity.origin.uri);
      const source = this.dbg.getSource(sId);

      console.assert(aId === activity.id);
      this.view.displaySource(activity, source, sId);
    }
  }

  public toggleHighlightMethod(actId: string, shortName: string, highlight: boolean) {
    const aId = getActivityIdFromView(actId);
    const activity = this.data.getActivity(aId);
    const sId = this.dbg.getSourceId(activity.origin.uri);
    const source: Source = this.dbg.getSource(sId);
    console.assert(aId === activity.id);
    const methods: Method[] = source.methods;
    const fullName = getFullMethodName(activity, shortName);
    const method: Method = methods.find(method => method.name === fullName);

    if (!method) {
      console.error("Method could not be found. This needs to be fixed by actually using DynamicScope info, instead of SendOps for the ProcessView");
      return;
    }

    if (highlight) {
      this.view.displaySource(activity, source, sId); // if source is already displayed, will return false
    }
    this.view.toggleHighlightMethod(sId, activity, method.sourceSection, highlight);
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

  public updateTraceData(data: TraceDataUpdate) {
    this.view.updateTraceData(data);

    for (const act of data.activities) {
      this.ensureActivityPromise(act.id);
      this.actPromResolve[act.id](act);
    }
  }

  public onStackTrace(msg: StackTraceResponse) {
    this.ensureActivityPromise(msg.activityId);

    this.actProm[msg.activityId].then((act: Activity) => {
      this.data.getActivity(msg.activityId).running = false;

      console.assert(act.id === msg.activityId);

      this.view.switchActivityDebuggerToSuspendedState(act);

      // Can happen when the application is exiting, or perhaps the activity
      if (msg.stackFrames.length < 1) {
        return;
      }

      const topFrameId = msg.stackFrames[0].id;
      this.vmConnection.requestScope(topFrameId);

      const sourceId = this.dbg.getSourceId(msg.stackFrames[0].sourceUri);
      const source = this.dbg.getSource(sourceId);

      const newSource = this.view.displaySource(act, source, sourceId);

      const ssId = this.dbg.getSectionIdFromFrame(sourceId, msg.stackFrames[0]);
      const section = this.dbg.getSection(ssId);

      this.view.displayStackTrace(sourceId, msg, topFrameId, act, ssId, section);
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
    const metaModel = new KomposMetaModel(msg.capabilities);

    this.data.setCapabilities(metaModel);
    this.view.setCapabilities(metaModel);
  }

  public onVariables(msg: VariablesResponse) {
    this.view.displayVariables(msg.variablesReference, msg.variables);
  }

  public onSymbolMessage(msg: SymbolMessage) {
    this.data.addSymbols(msg);
    this.updateTraceData(this.data.getNewestDataSinceLastUpdate());

    this.view.displaySystemView();
  }

  public onTracingData(data: DataView) {
    this.data.updateTraceData(data);
    this.updateTraceData(this.data.getNewestDataSinceLastUpdate());

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
    const parent = <Element> clickedSpan.parentNode.parentNode;
    const sourceId = getSourceIdFrom(parent.id);
    const source = this.dbg.getSource(sourceId);
    const breakpoint = this.toggleBreakpoint(line, source,
      function() { return createLineBreakpoint(source, sourceId, line); });

    this.view.updateLineBreakpoint(<LineBreakpoint> breakpoint);
  }

  public onToggleSectionBreakpoint(sectionId: string, type: string) {
    dbgLog("section breakpoint: " + type);

    const id = getBreakpointId(sectionId, type),
      sourceSection = this.dbg.getSection(sectionId),
      sourceId = getSourceIdFromSection(sectionId),
      source = this.dbg.getSource(sourceId),
      breakpoint = this.toggleBreakpoint(id, source, function() {
        return createSectionBreakpoint(source, sourceSection, sectionId, type);
      });

    this.view.updateSectionBreakpoint(<SectionBreakpoint> breakpoint);
  }

  public step(actId: string, step: string) {
    const activityId = getActivityIdFromView(actId);
    const act = this.data.getActivity(activityId);

    // Note: in general, we assume that all stepping operations lead to a
    // running activity. However, some operations, might discontinue execution
    // completely. These need extra support/interaction with the back-end/interpreter.
    if (act.running) { return; }
    act.running = true;
    this.vmConnection.sendDebuggerAction(step, act);
    this.view.onContinueExecution(act);
  }
}
