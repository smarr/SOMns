import { expect } from "chai";
import { ChildProcess, spawn, spawnSync, SpawnSyncReturns } from "child_process";
import { resolve } from "path";
import * as WebSocket from "ws";

import { Controller } from "../src/controller";
import {
  BreakpointData, SourceCoordinate, StoppedMessage, StackTraceResponse,
  FullSourceCoordinate, StackFrame
} from "../src/messages";
import { VmConnection } from "../src/vm-connection";
import { ActivityId } from "./somns-support";
import { Activity } from "../src/execution-data";
import { determinePorts } from "../src/launch-connector";
import { Stop, Test } from "./stepping";

const SOM_BASEPATH = "../../";
export const SOM = SOM_BASEPATH + "som";
export const PING_PONG_FILE = resolve("tests/pingpong.ns");
export const PING_PONG_URI = "file:" + PING_PONG_FILE;
export const ACTOR_FILE = resolve("tests/actor.ns")
export const ACTOR2_FILE = resolve("tests/actor2.ns")
export const ACTOR_URI = "file:" + ACTOR_FILE;
export const ACTOR2_URI = "file:" + ACTOR2_FILE;

const PRINT_SOM_OUTPUT = false;
const PRINT_CMD_LINE = false;

export function expectStop(msg: StackTraceResponse, stop: Stop, activityMap) {
  expectStack(msg.stackFrames, stop.stackHeight, stop.methodName, stop.line);

  if (activityMap[msg.activityId]) {
    expect(activityMap[msg.activityId]).to.equal(stop.activity);
  } else {
    activityMap[msg.activityId] = stop.activity;
  }
}

export function expectStack(stack: StackFrame[], length: number, methodName: string,
  startLine: number) {
  expect(stack[0]).to.be.not.null;
  expect(stack[0].name).to.equal(methodName);
  expect(stack[0].line).to.equal(startLine);
  return expect(stack).lengthOf(length);
}

export function expectSourceCoordinate(section: SourceCoordinate) {
  expect(section).to.have.property("charLength");
  expect(section).to.have.property("startColumn");
  expect(section).to.have.property("startLine");
}

export function expectFullSourceCoordinate(section: FullSourceCoordinate) {
  expectSourceCoordinate(section);
  expect(section).to.have.property("uri");
}

export interface OnMessageEvent {
  data: any;
  type: string;
  target: WebSocket;
}

export interface OnMessageHandler {
  (event: OnMessageEvent): void;
}

export class TestConnection extends VmConnection {
  private somProc: ChildProcess;
  private closed: boolean;
  private connectionResolver;
  public readonly fullyConnected: Promise<boolean>;

  constructor(extraArgs?: string[], triggerDebugger?: boolean, testFile?: string) {
    super(true);
    this.closed = false;
    this.startSom(extraArgs, triggerDebugger, testFile);
    this.fullyConnected = this.initConnection();
  }

  private startSom(extraArgs?: string[], triggerDebugger?: boolean, testFile?: string) {
    if (!testFile) { testFile = "tests/pingpong.ns"; }
    let args = ["-G", "-wd", testFile];
    if (triggerDebugger) { args = ["-d"].concat(args); };
    if (extraArgs) { args = args.concat(extraArgs); }

    if (PRINT_CMD_LINE) {
      console.log("[CMD]" + SOM + " " + args.join(" "));
    }

    this.somProc = spawn(SOM, args);
  }

  protected onOpen() {
    super.onOpen();
    this.connectionResolver(true);
  }

  private initConnection(): Promise<boolean> {
    const promise = new Promise<boolean>((resolve, reject) => {
      this.connectionResolver = resolve;
      let connecting = false;
      let errOut = "";

      this.somProc.on("exit", (code, signal) => {
        if (code !== 0) {
          this.somProc.stderr.on("close", () => {
            this.somProc.on("exit", (_code, _signal) => {
              reject(new Error("Process exited with code: " + code + " Signal: " + signal + " StdErr: " + errOut));
            });
          });
        }
      });

      this.somProc.stderr.on("data", data => {
        const dataStr = data.toString();
        if (PRINT_SOM_OUTPUT) {
          console.error(dataStr);
        }
        errOut += dataStr;
      });

      const ports = { msg: 0, trace: 0 };
      this.somProc.stdout.on("data", (data) => {
        const dataStr = data.toString();
        if (PRINT_SOM_OUTPUT) {
          console.log(dataStr);
        }
        determinePorts(dataStr, ports);

        if (dataStr.includes("Started HTTP Server") && !connecting) {
          connecting = true;
          console.assert(ports.msg > 0 && ports.trace > 0);
          this.connectWebSockets(ports.msg, ports.trace);
        }
        if (dataStr.includes("Failed starting WebSocket and/or HTTP Server")) {
          this.somProc.stderr.on("close", () => {
            this.somProc.on("exit", (_code, _signal) => {
              reject(new Error("SOMns failed to starting WebSocket and/or HTTP Server. StdOut: " + dataStr + " StdErr: " + errOut));
            });
          });
          this.somProc.kill();
        }
      });
    });
    return promise;
  }

  public close(done: MochaDone) {
    if (this.closed) {
      done();
      return;
    }

    this.somProc.on("exit", _code => {
      this.closed = true;
      // wait until process is shut down, to make sure all ports are closed
      done();
    });
    this.somProc.kill();
  }
}

export class ControllerWithInitialBreakpoints extends Controller {
  private initialBreakpoints: BreakpointData[];

  constructor(initialBreakpoints: BreakpointData[], vmConnection: VmConnection) {
    super(vmConnection);
    this.initialBreakpoints = initialBreakpoints;
  }

  public onConnect() {
    super.onConnect();
    this.vmConnection.sendInitializeConnection(this.initialBreakpoints);
  }
}

export function execSom(extraArgs: string[]): SpawnSyncReturns<string> {
  const args = ["-G", "-t1", "-dnu", "tests/pingpong.ns"].concat(extraArgs);
  return spawnSync(SOM, args);
}

export class HandleStoppedAndGetStackTrace extends ControllerWithInitialBreakpoints {
  private numStopped: number;
  private readonly numOps: number;
  public readonly stackPs: Promise<StackTraceResponse>[];
  private readonly resolveStackPs;
  public readonly stoppedActivities: Activity[];

  constructor(initialBreakpoints: BreakpointData[], vmConnection: VmConnection,
    connectionP: Promise<boolean>, numOps: number = 1) {
    super(initialBreakpoints, vmConnection);

    this.numOps = numOps;
    this.numStopped = 0;
    this.stoppedActivities = [];

    this.resolveStackPs = [];
    this.stackPs = [];
    for (let i = 0; i < numOps; i += 1) {
      this.stackPs.push(new Promise<StackTraceResponse>((resolve, reject) => {
        this.resolveStackPs.push(resolve);
        connectionP.catch(reject);
      }));
    }
  }

  public onStoppedMessage(msg: StoppedMessage) {
    if (this.numStopped >= this.numOps) { return; }
    // don't need more than a dummy activity at the moment, just id is enough
    const activity: Activity = {
      id: msg.activityId, completed: false,
      name: "dummy", type: <number> ActivityId.ACTOR, creationScope: null,
      creationActivity: null, running: false
    };
    this.stoppedActivities[this.numStopped] = activity;
    this.vmConnection.requestStackTrace(msg.activityId, this.numStopped);
    this.numStopped += 1;
  }

  public onStackTrace(msg: StackTraceResponse) {
    this.resolveStackPs[msg.requestId](msg);
  }
}

export class TestController extends ControllerWithInitialBreakpoints {
  private readonly activityMap;

  private numStopped: number;

  private resolve: (value?: Stop[] | PromiseLike<Stop[]>) => void;
  public stopsDoneForStep: Promise<Stop[]>;
  private readonly suite: Test;

  private expectedStops: Stop[];
  private receivedStops: Stop[];
  private readonly connectionP: Promise<boolean>;

  constructor(suite: Test, vmConnection: VmConnection,
    connectionP: Promise<boolean>) {
    super(suite.initialBreakpoints, vmConnection);
    this.connectionP = connectionP;
    this.activityMap = {};
    this.suite = suite;

    this.numStopped = 0;
    this.expectedStops = [suite.initialStop];
    this.receivedStops = [];

    this.renewPromise();
  }

  private renewPromise() {
    if (this.expectedStops !== null && this.expectedStops !== undefined &&
      this.expectedStops.length > 0) {
      this.stopsDoneForStep = new Promise<Stop[]>((resolve, reject) => {
        this.resolve = resolve;
        this.connectionP.catch(reject);
      });
    } else {
      this.stopsDoneForStep = Promise.resolve([]);
    }
    return this.stopsDoneForStep;
  }

  private getActivityId(activity: string): number {
    for (const actId in this.activityMap) {
      if (this.activityMap[actId] === activity) {
        return parseInt(actId);
      }
    }

    throw new Error("Activity map did not contain an id for activity: " + activity);
  }

  public doNextStep(type: string, activity: string, expectedStops?: Stop[]) {
    this.expectedStops = expectedStops;
    this.receivedStops = [];

    const actId = this.getActivityId(activity);

    const act: Activity = {
      id: actId, completed: false,
      name: "dummy", type: <number> ActivityId.ACTOR, creationScope: null,
      creationActivity: null, running: false
    };
    this.vmConnection.sendDebuggerAction(type, act);
    return this.renewPromise();
  }

  public onStoppedMessage(msg: StoppedMessage) {
    if (this.numStopped === 0) {
      this.activityMap[msg.activityId] = this.suite.initialStop.activity;
    }

    this.vmConnection.requestStackTrace(msg.activityId, this.numStopped);
    this.numStopped += 1;
  }

  private getActivity(activityId: number, currentStop: Stop): string {
    if (this.activityMap[activityId]) {
      return this.activityMap[activityId];
    }

    for (const stop of this.expectedStops) {
      if (stop.line === currentStop.line &&
        stop.methodName === currentStop.methodName &&
        stop.stackHeight === currentStop.stackHeight) {
        this.activityMap[activityId] = stop.activity;
        return stop.activity;
      }
    }
    throw new Error("Could not identify which activity name corresponds to " +
      `activity id: ${activityId} for stopped msg: ${JSON.stringify(currentStop)}`);
  }

  public onStackTrace(msg: StackTraceResponse) {
    const stop: Stop = {
      line: msg.stackFrames[0].line,
      methodName: msg.stackFrames[0].name,
      stackHeight: msg.stackFrames.length,
      activity: ""
    };
    const activity = this.getActivity(msg.activityId, stop);
    stop.activity = activity;

    this.receivedStops.push(stop);

    if (this.receivedStops.length === this.expectedStops.length) {
      this.resolve(this.receivedStops);
    } else if (this.receivedStops.length > this.expectedStops.length) {
      throw new Error("Received more StoppedMessages than expected.");
    }
  }
}