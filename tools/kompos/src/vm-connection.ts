/* jshint -W097 */
"use strict";

import * as WebSocket from "ws";

import {Controller} from "./controller";
import {Activity, Message, Respond, StepType, BreakpointData} from "./messages";

const DBG_PORT   = 7977;
const TRACE_PORT = 7978;
const LOCAL_WS_URL = "ws://localhost";

/**
 * Encapsulates the connection to the VM via a web socket and encodes
 * the communication protocol, currently using JSON.
 */
export class VmConnection {
  private socket:                WebSocket;
  private traceDataSocket:       WebSocket;
  private readonly useTraceData: boolean;
  private controller:            Controller;

  constructor(useTraceData: boolean) {
    this.socket          = null;
    this.traceDataSocket = null;
    this.controller      = null;
    this.useTraceData    = useTraceData;
  }

  public setController(controller: Controller) {
    this.controller = controller;
  }

  public isConnected() {
    return this.socket !== null && this.socket.readyState === WebSocket.OPEN;
  }

  private connectTraceDataSocket() {
    if (!this.useTraceData) { return; }

    console.assert(this.traceDataSocket === null || this.traceDataSocket.readyState === WebSocket.CLOSED);
    this.traceDataSocket = new WebSocket(LOCAL_WS_URL + ":" + TRACE_PORT);
    (<any> this.traceDataSocket).binaryType = "arraybuffer"; // workaround, typescript dosn't recognize this property

    const controller = this.controller;
    this.traceDataSocket.onmessage = function (e) {
      const data: DataView = new DataView(e.data);
      controller.onTracingData(data);
    };
  }

  protected onOpen() {
    if (!this.controller) { return; }
    this.controller.onConnect();
  }

  public connect() {
    console.assert(this.socket === null || this.socket.readyState === WebSocket.CLOSED);
    this.socket = new WebSocket(LOCAL_WS_URL + ":" + DBG_PORT);
    const ctrl = this.controller;

    this.socket.onopen = () => {
      this.onOpen();
    };

    this.socket.onclose = () => {
      if (!ctrl) { return; }
      ctrl.onClose();
    };

    this.socket.onerror = () => {
      if (!ctrl) { return; }
      ctrl.onError();
    };

    this.socket.onmessage = (e) => {
      if (!ctrl) { return; }

      const data: Message = JSON.parse(e.data);

      switch (data.type) {
        case "source":
          ctrl.onReceivedSource(data);
          break;
        case "StoppedMessage":
          ctrl.onStoppedMessage(data);
          break;
        case "SymbolMessage":
          ctrl.onSymbolMessage(data);
          break;
        case "StackTraceResponse":
          ctrl.onStackTrace(data);
          break;
        case "ScopesResponse":
          ctrl.onScopes(data);
          break;
        case "VariablesResponse":
          ctrl.onVariables(data);
          break;
        case "ProgramInfoResponse":
          ctrl.onProgramInfo(data);
          break;
        default:
          ctrl.onUnknownMessage(data);
          break;
      }
    };

    this.connectTraceDataSocket();
  }

  public disconnect() {
    console.assert(this.isConnected());
  }

  public requestProgramInfo() {
    this.send({action: "ProgramInfoRequest"});
  }

  public requestTraceData() {
    this.send({action: "TraceDataRequest"});
  }

  public sendInitialBreakpoints(breakpoints: BreakpointData[]) {
    this.send({
      action: "initialBreakpoints",
      breakpoints: breakpoints
    });
  }

  public updateBreakpoint(breakpoint: BreakpointData) {
    this.send({
      action: "updateBreakpoint",
      breakpoint: breakpoint
    });
  };

  public sendDebuggerAction(action: StepType, activity: Activity) {
    this.send({
      action: action,
      activityId: activity.id});
  }

  public requestStackTrace(activityId: number) {
    this.send({
      action: "StackTraceRequest",
      activityId: activityId,
      startFrame: 0, // from the top
      levels:     0, // request all
      requestId:  0  // only used in VS code adapter currently
    });
  }

  public requestScope(scopeId: number) {
    this.send({
      action:    "ScopesRequest",
      frameId:   scopeId,
      requestId: 0 // only used in VS code
    });
  }

  public requestVariables(variablesReference: number) {
    this.send({
      action: "VariablesRequest",
      variablesReference: variablesReference,
      requestId: 0 // only used in VS code
    });
  }

  private send(respond: Respond) {
    this.socket.send(JSON.stringify(respond));
  }
}
