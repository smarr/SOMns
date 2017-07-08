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

const SOM_BASEPATH = "../../";
export const SOM = SOM_BASEPATH + "som";
export const PING_PONG_URI = "file:" + resolve("tests/pingpong.som");

const PRINT_SOM_OUTPUT = false;
const PRINT_CMD_LINE = false;

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
    if (!testFile) { testFile = "tests/pingpong.som"; }
    let args = ["-G", "-t1", "-wd", testFile];
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
  const args = ["-G", "-t1", "-dnu", "tests/pingpong.som"].concat(extraArgs);
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
