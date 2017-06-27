/* jshint -W097 */
"use strict";

import * as WebSocket from "ws";

import { Controller } from "./controller";
import { Message, Respond, BreakpointData } from "./messages";
import { Activity } from "./execution-data";

const LOCAL_WS_URL = "ws://localhost";

/**
 * Encapsulates the connection to the VM via a web socket and encodes
 * the communication protocol, currently using JSON.
 */
export class VmConnection {
  private socket: WebSocket;
  private traceDataSocket: WebSocket;
  private readonly useTraceData: boolean;
  private controller: Controller;

  constructor(useTraceData: boolean) {
    this.socket = null;
    this.traceDataSocket = null;
    this.controller = null;
    this.useTraceData = useTraceData;
  }

  public setController(controller: Controller) {
    this.controller = controller;
  }

  public isConnected() {
    return this.socket !== null && this.socket.readyState === WebSocket.OPEN;
  }

  private connectTraceDataSocket(tracePort: number) {
    if (!this.useTraceData) { return; }

    console.assert(this.traceDataSocket === null || this.traceDataSocket.readyState === WebSocket.CLOSED);
    this.traceDataSocket = new WebSocket(LOCAL_WS_URL + ":" + tracePort);
    (<any> this.traceDataSocket).binaryType = "arraybuffer"; // workaround, typescript doesn't recognize this property

    const controller = this.controller;
    this.traceDataSocket.onmessage = function(e) {
      const data: DataView = new DataView(e.data);
      controller.onTracingData(data);
    };
  }

  protected onOpen() {
    if (!this.controller) { return; }
    this.controller.onConnect();
  }

  public connect() {
    $.getJSON("ports.json", data => {
      console.log(data);
      this.connectWebSockets(data.dbgPort, data.tracePort);
    });
  }

  protected connectWebSockets(dbgPort: number, tracePort: number) {
    console.assert(this.socket === null || this.socket.readyState === WebSocket.CLOSED);
    this.socket = new WebSocket(LOCAL_WS_URL + ":" + dbgPort);
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
        case "InitializationResponse":
          ctrl.onInitializationResponse(data);
          break;
        default:
          ctrl.onUnknownMessage(data);
          break;
      }
    };

    this.connectTraceDataSocket(tracePort);
  }

  public disconnect() {
    console.assert(this.isConnected());
  }

  public requestProgramInfo() {
    this.send({ action: "ProgramInfoRequest" });
  }

  public requestTraceData() {
    this.send({ action: "TraceDataRequest" });
  }

  public sendInitializeConnection(breakpoints: BreakpointData[]) {
    this.send({
      action: "InitializeConnection",
      breakpoints: breakpoints
    });
  }

  public updateBreakpoint(breakpoint: BreakpointData) {
    this.send({
      action: "updateBreakpoint",
      breakpoint: breakpoint
    });
  };

  public sendDebuggerAction(step: string, activity: Activity) {
    this.send({
      action: "StepMessage",
      step: step,
      activityId: activity.id
    });
  }

  public requestStackTrace(activityId: number, requestId: number = 0) {
    this.send({
      action: "StackTraceRequest",
      activityId: activityId,
      startFrame: 0, // from the top
      levels: 0, // request all
      requestId: requestId
    });
  }

  public requestScope(scopeId: number) {
    this.send({
      action: "ScopesRequest",
      frameId: scopeId,
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
