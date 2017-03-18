import { expect } from "chai";
import { ChildProcess, spawn, spawnSync, SpawnSyncReturns } from "child_process";
import { resolve } from "path";
import * as WebSocket from "ws";

import {Controller} from "../src/controller";
import {BreakpointData, SourceCoordinate, StoppedMessage, StackTraceResponse,
  FullSourceCoordinate, StackFrame, Activity} from "../src/messages";
import {VmConnection} from "../src/vm-connection";

const SOM_BASEPATH = "../../";
export const SOM = SOM_BASEPATH + "som";
export const PING_PONG_URI = "file:" + resolve("tests/pingpong.som");

const PRINT_SOM_OUTPUT = false;
const PRINT_CMD_LINE   = false;

export function expectStack(stack: StackFrame[], length: number, methodName: string,
    startLine: number) {
  expect(stack).lengthOf(length);
  expect(stack[0]).to.be.not.null;
  expect(stack[0].name).to.equal(methodName);
  expect(stack[0].line).to.equal(startLine);
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
  data:   any;
  type:   string;
  target: WebSocket;
}

export interface OnMessageHandler {
  (event: OnMessageEvent): void;
}

export class TestConnection extends VmConnection {
  private somProc: ChildProcess;
  private closed:  boolean;
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
    const promise = new Promise((resolve, reject) => {
      this.connectionResolver = resolve;
      let connecting = false;

      if (PRINT_SOM_OUTPUT) {
        this.somProc.stderr.on("data", (data) => { console.error(data.toString()); });
      }

      this.somProc.stdout.on("data", (data) => {
        const dataStr = data.toString();
        if (PRINT_SOM_OUTPUT) {
          console.log(dataStr);
        }
        if (dataStr.includes("Started HTTP Server") && !connecting) {
          connecting = true;
          this.connect();
        }
        if (dataStr.includes("Failed starting WebSocket and/or HTTP Server")) {
          reject(new Error("SOMns failed to starting WebSocket and/or HTTP Server"));
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
    this.vmConnection.sendInitialBreakpoints(this.initialBreakpoints);
  }
}

export function execSom(extraArgs: string[]): SpawnSyncReturns<string> {
  const args = ["-G", "-t1", "-dnu", "tests/pingpong.som"].concat(extraArgs);
  return spawnSync(SOM, args);
}

export class HandleStoppedAndGetStackTrace extends ControllerWithInitialBreakpoints {
  private numStopped: number;
  private readonly numOps: number;
  public readonly stackP: Promise<StackTraceResponse>;
  public readonly stackPs: Promise<StackTraceResponse>[];
  private resolveStackP;
  private readonly resolveStackPs;
  public readonly stoppedActivities: Activity[];

  constructor(initialBreakpoints: BreakpointData[], vmConnection: VmConnection,
      numOps: number = 1) {
    super(initialBreakpoints, vmConnection);

    this.numOps = numOps;
    this.numStopped = 0;
    this.stoppedActivities = [];

    this.stackP = new Promise<StackTraceResponse>((resolve, _reject) => {
      this.resolveStackP = resolve;
    });

    if (numOps > 1) {
      this.resolveStackPs = [];
      this.stackPs = [];
      for (let i = 1; i < numOps; i += 1) {
        this.stackPs.push(new Promise<StackTraceResponse>((resolve, _reject) => {
          this.resolveStackPs.push(resolve);
        }));
      }
    }
  }

  public getStackP(idx: number) {
    if (idx === 0) {
      return this.stackP;
    }
    return this.stackPs[idx - 1];
  }

  public onStoppedEvent(msg: StoppedMessage) {
    if (this.numStopped >= this.numOps) { return; }
    // don't need more than a dummy activity at the moment, just id is enough
    const activity: Activity = {id: msg.activityId,
      name: "dummy", type: "Actor", causalMsg: 0, running: false};
    this.stoppedActivities[this.numStopped] = activity;
    this.numStopped += 1;
    this.vmConnection.requestStackTrace(msg.activityId);
  }

  public onStackTrace(msg: StackTraceResponse) {
    if (this.numStopped === 1) {
      this.resolveStackP(msg);
      return;
    }
    this.resolveStackPs[this.numStopped - 2](msg);
  }
}
