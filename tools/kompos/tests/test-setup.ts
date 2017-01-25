import { expect } from 'chai';
import { ChildProcess, spawn, spawnSync, SpawnSyncReturns } from 'child_process';
import { resolve } from 'path';
import * as WebSocket from 'ws';

import {SuspendEventMessage, BreakpointData, Respond, SourceCoordinate,
  FullSourceCoordinate, Frame} from '../src/messages';

const DEBUGGER_PORT = 7977;
const SOM_BASEPATH = '../../';
export const SOM = SOM_BASEPATH + 'som';
export const PING_PONG_URI = 'file:' + resolve('tests/pingpong.som');

const PRINT_SOM_OUTPUT = false;
const PRINT_CMD_LINE   = false;

export function expectStack(stack: Frame[], length: number, methodName: string,
    startLine: number) {
  expect(stack).lengthOf(length);
  expect(stack[0].methodName).to.equal(methodName);
  expect(stack[0].sourceSection.startLine).to.equal(startLine);
}

export function expectSourceCoordinate(section: SourceCoordinate) {
  expect(section).to.have.property('charLength');
  expect(section).to.have.property('startColumn');
  expect(section).to.have.property('startLine');
}

export function expectFullSourceCoordinate(section: FullSourceCoordinate) {
  expectSourceCoordinate(section);
  expect(section).to.have.property('uri');
}

export interface OnMessageEvent {
  data:   any;
  type:   string;
  target: WebSocket;
}

export interface OnMessageHandler {
  (event: OnMessageEvent): void;
}

export interface SomConnection {
  somProc: ChildProcess;
  socket:  WebSocket;
  closed:  boolean;
}

export function closeConnection(connection: SomConnection, done: MochaDone) {
  if (connection.closed) {
    done();
    return;
  }
  connection.somProc.kill();
  connection.somProc.on('exit', _code => {
    connection.closed = true;
    // wait until process is shut down, to make sure all ports are closed
    done();
  });
}

export function execSom(extraArgs: string[]): SpawnSyncReturns<string> {
  const args = ['-G', '-t1', '-dnu', 'tests/pingpong.som'].concat(extraArgs);
  return spawnSync(SOM, args);
}

export class HandleFirstSuspendEvent {
  private firstSuspendCaptured: boolean;
  public getSuspendEvent: (event: OnMessageEvent) => void;

  public readonly suspendP: Promise<SuspendEventMessage>;

  constructor() {
    this.firstSuspendCaptured = false;
    this.suspendP = new Promise<SuspendEventMessage>((resolve, _reject) => {
      this.getSuspendEvent = (event: OnMessageEvent) => {
        if (this.firstSuspendCaptured) { return; }
        const data = JSON.parse(event.data);
        if (data.type === "suspendEvent") {
          this.firstSuspendCaptured = true;
          resolve(data);
        }
      };
    });
  }
}

export function send(socket: WebSocket, respond: Respond) {
  socket.send(JSON.stringify(respond));
}

export function startSomAndConnect(onMessageHandler?: OnMessageHandler,
    initialBreakpoints?: BreakpointData[], extraArgs?: string[],
    triggerDebugger?: boolean, testFile?: string): Promise<SomConnection> {
  if (!testFile) { testFile = 'tests/pingpong.som'; }
  let args = ['-G', '-t1', '-wd', testFile];
  if (triggerDebugger) { args = ['-d'].concat(args); };
  if (extraArgs) { args = args.concat(extraArgs); }
  if (PRINT_CMD_LINE) {
    console.log("[CMD]" + SOM + args.join(' '));
  }
  const somProc = spawn(SOM, args);
  const promise = new Promise((resolve, reject) => {
    let connecting = false;

    somProc.stderr.on('data', (data) => {
      if (PRINT_SOM_OUTPUT) {
        console.error(data.toString());
      }
    });

    somProc.stdout.on('data', (data) => {
      const dataStr = data.toString();
      if (PRINT_SOM_OUTPUT) {
        console.log(dataStr);
      }
      if (dataStr.includes("Started HTTP Server") && !connecting) {
        connecting = true;
        const socket = new WebSocket('ws://localhost:' + DEBUGGER_PORT);
        socket.on('open', () => {
          if (initialBreakpoints) {
            send(socket, {action: "initialBreakpoints",
              breakpoints: initialBreakpoints, debuggerProtocol: false});
          }
          resolve({somProc: somProc, socket: socket, closed: false});
        });
        if (onMessageHandler) {
          socket.onmessage = onMessageHandler;
        }
      }
      if (dataStr.includes("Failed starting WebSocket and/or HTTP Server")) {
        reject(new Error('SOMns failed to starting WebSocket and/or HTTP Server'));
      }
    });
  });
  return promise;
}
