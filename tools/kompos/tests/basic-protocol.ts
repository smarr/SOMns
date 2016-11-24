'use strict';

const debuggerPort = 7977;
const somBasepath = '../../';
const som = somBasepath + 'som';

import * as WebSocket from 'ws';
import { expect } from 'chai';
import { spawn, spawnSync, ChildProcess, SpawnSyncReturns } from 'child_process';
import { resolve } from 'path';
import * as fs from 'fs';
import {X_OK} from 'constants';

import {SourceCoordinate, FullSourceCoordinate, SourceMessage, SuspendEventMessage,
  BreakpointData, LineBreakpointData, SectionBreakpointData, Respond,
  StepMessage} from '../src/messages';

const PRINT_SOM_OUTPUT = false;

interface SomConnection {
  somProc: ChildProcess;
  socket:  WebSocket;
  closed:  boolean;
}

interface OnMessageEvent {
  data:   any;
  type:   string;
  target: WebSocket;
}

interface OnMessageHandler {
  (event: OnMessageEvent): void;
}

const pingPongUri = 'file:' + resolve('tests/pingpong.som');

function expectSourceCoordinate(section: SourceCoordinate) {
  expect(section).to.have.property('charLength');
  expect(section).to.have.property('startColumn');
  expect(section).to.have.property('startLine');
}

function expectFullSourceCoordinate(section: FullSourceCoordinate) {
  expectSourceCoordinate(section);
  expect(section).to.have.property('uri');
}

function send(socket: WebSocket, respond: Respond) {
  socket.send(JSON.stringify(respond));
}

function closeConnection(connection: SomConnection, done: MochaDone) {
  if (connection.closed) {
    done();
    return;
  }
  connection.somProc.kill();
  connection.somProc.on('exit', code => {
    connection.closed = true;
    // wait until process is shut down, to make sure all ports are closed
    done();
  });
}

function startSomAndConnect(onMessageHandler?: OnMessageHandler,
    initialBreakpoints?: BreakpointData[], extraArgs?: string[],
    triggerDebugger?: boolean): Promise<SomConnection> {
  let args = ['-G', '-t1', '-wd', 'tests/pingpong.som'];
  if (triggerDebugger) { args = ['-d'].concat(args); };
  if (extraArgs) { args = args.concat(extraArgs); }

  const somProc = spawn(som, args);
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
        const socket = new WebSocket('ws://localhost:' + debuggerPort);
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

function execSom(extraArgs: string[]): SpawnSyncReturns<string> {
  const args = ['-G', '-t1', '-dnu', 'tests/pingpong.som'].concat(extraArgs);
  return spawnSync(som, args);
}

let connectionPossible = false;
function onlyWithConnection(fn) {
  return function() {
    if (connectionPossible) {
      return fn();
    } else {
      this.skip();
    }
  };
}

describe('Basic Project Setup', () => {
  describe('SOMns is testable', () => {
    it('SOMns executable should be in somBasepath', (done) => {
      fs.access(som, X_OK, (err) => {
        expect(err).to.be.null;
        done();
      });
    });

    it('should show help', done => {
      let sawOutput = false;
      const somProc = spawn(som, ['-h']);
      somProc.stdout.on('data', (data) => { sawOutput = true; });
      somProc.on('exit', (code) => {
        expect(sawOutput).to.be.true;
        expect(code).to.equal(0);
        done();
      });
    });

    it('should be possible to connect', done => {
      const connectionP = startSomAndConnect();
      connectionP.then(connection => {
        closeConnection(connection, done);
        connectionPossible = true;
      });
      connectionP.catch(reason => {
        done(reason);
      });
    });
  });

  describe('SOMns stack trace', () => {
    it('should be correct for #doesNotUnderstand', () => {
      const result = execSom(['dnu']);
      expect(result.output[1].toString()).to.equal('Stack Trace\n\
\tPlatform>>#start                             Platform.som:27:7\n\
\tBlock>>#on:do:                               Kernel.som:601:18\n\
\tvmMirror>>#exceptionDo:catch:onException:    ExceptionDoOnPrimFactory:1:1\n\
\tPlatform>>#$blockMethod@26@25                Platform.som:26:55\n\
\tPingPongApp>>#main:                          pingpong.som:87:34\n\
\tPingPongApp>>#testDNU                        pingpong.som:71:7\n\
ERROR: MessageNotUnderstood(Integer>>#foobar)\n');
    });

    it('should be correct for `system printStackTrace`', () => {
      const result = execSom(['stack']);
      expect(result.output[1].toString()).to.equal('Stack Trace\n\
\tPlatform>>#start                             Platform.som:27:7\n\
\tBlock>>#on:do:                               Kernel.som:601:18\n\
\tvmMirror>>#exceptionDo:catch:onException:    ExceptionDoOnPrimFactory:1:1\n\
\tPlatform>>#$blockMethod@26@25                Platform.som:26:55\n\
\tPingPongApp>>#main:                          pingpong.som:88:34\n\
\tPingPongApp>>#testPrintStackTrace            pingpong.som:76:12\n');
    });
  });
});

describe('Basic Protocol', function() {
  let connectionP : Promise<SomConnection> = null;
  const closeConnectionAfterSuite = (done) => {
    connectionP.then(c => { closeConnection(c, done);});
    connectionP.catch(reason => done(reason));
  };

  describe('source message', () => {
    // Capture first source message for testing
    let firstSourceCaptured = false;
    let getSourceData: (event: OnMessageEvent) => void;
    let sourceP = new Promise<SourceMessage>((resolve, reject) => {
      getSourceData = (event: OnMessageEvent) => {
        if (firstSourceCaptured) { return; }    
        const data = JSON.parse(event.data);
        if (data.type === "source") {
          firstSourceCaptured = true;
          resolve(data);
        }
      };
    });

    before('Start SOMns and Connect', () => {
      connectionP = startSomAndConnect(getSourceData, []);
    });

    after(closeConnectionAfterSuite);

    it('should have sources', onlyWithConnection(() => {
      return sourceP.then(sourceMsg => {
        for (let source of sourceMsg.sources) {
          expect(source.mimeType).to.equal("application/x-newspeak-som-ns");
          expect(source.name).to.equal("Platform.som");
          expect(source).to.have.property('sourceText');
          expect(source).to.have.property('uri');
          return;
        }
      });
    }));

    it('should have source sections', onlyWithConnection(() => {
      return sourceP.then(sourceMsg => {
        for (let s of sourceMsg.sources) {
          for (let ss of s.sections) {
            expectSourceCoordinate(ss);
            return;
          }
        }
      });
    }));

    it('should have methods', onlyWithConnection(() => {
      return sourceP.then(sourceMsg => {
        for (let s of sourceMsg.sources) {
          for (let method of s.methods) {
            expect(method).to.have.property('name');
            expect(method).to.have.property('definition');

            const def = method.definition[0];
            expectSourceCoordinate(def);
            expectSourceCoordinate(method.sourceSection);
            return;
          }
        }
      });
    }));
  });

  describe('setting a line breakpoint', () => {
    // Capture first suspended event for testing
    let firstSuspendCaptured = false;
    let getSuspendEvent: (event: OnMessageEvent) => void;
    let suspendP = new Promise<SuspendEventMessage>((resolve, reject) => {
      getSuspendEvent = (event: OnMessageEvent) => {
        if (firstSuspendCaptured) { return; }    
        const data = JSON.parse(event.data);
        if (data.type === "suspendEvent") {
          firstSuspendCaptured = true;
          resolve(data);
        }
      };
    });

    before('Start SOMns and Connect', () => {
      const breakpoint: LineBreakpointData = {
        type: "LineBreakpoint",
        line: 53,
        sourceUri: pingPongUri,
        enabled: true
      };
      connectionP = startSomAndConnect(getSuspendEvent, [breakpoint]);
    });

    after(closeConnectionAfterSuite);

    it('should accept line breakpoint, and halt on expected line', onlyWithConnection(() => {
      return suspendP.then(msg => {
        expect(msg.stack).lengthOf(7);
        expect(msg.stack[0].methodName).to.equal("PingPong>>#benchmark");
        expect(msg.stack[0].sourceSection.startLine).to.equal(53);
      });
    }));

    it('should have a well structured suspended event', onlyWithConnection(() => {
      return suspendP.then(msg => {
        expectFullSourceCoordinate(msg.stack[0].sourceSection);
        expect(msg.id).to.equal("se-0");
        expect(msg.sourceUri).to.equal(pingPongUri);
        expect(msg.topFrame.arguments[0]).to.equal("a PingPong");
        expect(msg.topFrame.slots['ping']).to.equal('a Nil');
      });
    }));    
  });

  describe('setting a source section sender breakpoint', () => {
    // Capture first suspended event for testing
    let firstSuspendCaptured = false;
    let getSuspendEvent: (event: OnMessageEvent) => void;
    let suspendP = new Promise<SuspendEventMessage>((resolve, reject) => {
      getSuspendEvent = (event: OnMessageEvent) => {
        if (firstSuspendCaptured) { return; }    
        const data = JSON.parse(event.data);
        if (data.type === "suspendEvent") {
          firstSuspendCaptured = true;
          resolve(data);
        }
      };
    });

    before('Start SOMns and Connect', () => {
      const breakpoint: SectionBreakpointData = {
        type: "MessageSenderBreakpoint",
        enabled: true,
        coord: {
          uri:         pingPongUri,
          startLine:   16,
          startColumn: 14,
          charLength:   3}};
      connectionP = startSomAndConnect(getSuspendEvent, [breakpoint]);
    });

    after(closeConnectionAfterSuite);

    it('should accept send breakpoint, and halt on expected source section', onlyWithConnection(() => {
      return suspendP.then(msg => {
        expect(msg.stack).lengthOf(2);
        expect(msg.stack[0].methodName).to.equal("Ping>>#start");
        expect(msg.stack[0].sourceSection.startLine).to.equal(16);
      });
    }));    
  });

  describe('setting a source section asynchronous method receiver breakpoint', () => {
    // Capture first suspended event for testing
    let firstSuspendCaptured = false;
    let getSuspendEvent: (event: OnMessageEvent) => void;
    let suspendP = new Promise<SuspendEventMessage>((resolve, reject) => {
      getSuspendEvent = (event: OnMessageEvent) => {
        if (firstSuspendCaptured) { return; }
        const data = JSON.parse(event.data);
        if (data.type === "suspendEvent") {
          firstSuspendCaptured = true;
          resolve(data);
        }
      };
    });

    before('Start SOMns and Connect', () => {
      const breakpoint: SectionBreakpointData = {
        type: "AsyncMessageReceiverBreakpoint",
        enabled: true,
        coord: {
          uri:         pingPongUri,
          startLine:   40,
          startColumn:  9,
          charLength:  88}
      };
      connectionP = startSomAndConnect(getSuspendEvent, [breakpoint]);
    });

    after(closeConnectionAfterSuite);

    it('should accept async method receiver breakpoint, and halt on expected source section', onlyWithConnection(() => {
      return suspendP.then(msg => {
        expect(msg.stack).lengthOf(2);
        expect(msg.stack[0].methodName).to.equal("Pong>>#ping:");
        expect(msg.stack[0].sourceSection.startLine).to.equal(40);
      });
    }));
  });

  describe('setting a source section receiver breakpoint', () => {
    // Capture first suspended event for testing
    let firstSuspendCaptured = false;
    let getSuspendEvent: (event: OnMessageEvent) => void;
    let suspendP = new Promise<SuspendEventMessage>((resolve, reject) => {
      getSuspendEvent = (event: OnMessageEvent) => {
        if (firstSuspendCaptured) { return; }    
        const data = JSON.parse(event.data);
        if (data.type === "suspendEvent") {
          firstSuspendCaptured = true;
          resolve(data);
        }
      };
    });

    before('Start SOMns and Connect', () => {
      const breakpoint: SectionBreakpointData = {
        type: "MessageReceiverBreakpoint",
        enabled: true,
        coord: {
          uri:         pingPongUri,
          startLine:   16,
          startColumn: 14,
          charLength:   3}};
      connectionP = startSomAndConnect(getSuspendEvent, [breakpoint]);
    });

    after(closeConnectionAfterSuite);

    it('should accept send breakpoint, and halt on expected source section', onlyWithConnection(() => {
      return suspendP.then(msg => {
        expect(msg.stack).lengthOf(2);
        expect(msg.stack[0].methodName).to.equal("Pong>>#ping:");
        expect(msg.stack[0].sourceSection.startLine).to.equal(40);
      });
    }));
  });

  it('should accept source section breakpoint format (promise), and halt at resolution');

  describe('stepping', () => {
    // Capture suspended events
    const suspendPs: Promise<SuspendEventMessage>[] = [];
    const numSuspends = 4;
    const resolves = [];
    for (let i = 0; i < numSuspends; i++) {
      suspendPs.push(new Promise<SuspendEventMessage>((res, rej) => resolves.push(res)));
    }

    let capturedEvents = 0;
    let getSuspendEvent: (event: OnMessageEvent) => void;
    
    getSuspendEvent = (event: OnMessageEvent) => {
      if (capturedEvents > numSuspends) { return; }    
      const data = JSON.parse(event.data);
      if (data.type === "suspendEvent") {
        resolves[capturedEvents](data);
        capturedEvents += 1;
      }
    };

    before('Start SOMns and Connect', () => {
      const breakpoint: SectionBreakpointData = {
        type: "MessageSenderBreakpoint",
        enabled: true,
        coord: {
          uri:         pingPongUri,
          startLine:   16,
          startColumn: 14,
          charLength:   3}};
      connectionP = startSomAndConnect(getSuspendEvent, [breakpoint]);
    });

    after(closeConnectionAfterSuite);

    it('should stop initially at breakpoint', onlyWithConnection(() => {
      return suspendPs[0].then(msg => {
        expect(msg.stack).lengthOf(2);
        expect(msg.stack[0].methodName).to.equal("Ping>>#start");
        expect(msg.stack[0].sourceSection.startLine).to.equal(16);
      });
    }));

    it('should single stepping', onlyWithConnection(() => {
      return new Promise((resolve, reject) => {
        suspendPs[0].then(msg => {
          const step : StepMessage = {action: "stepInto", suspendEvent: msg.id};
          connectionP.then(con => {
            send(con.socket, step); });

          const p = suspendPs[1].then(msgAfterStep => {
            expect(msgAfterStep.stack).lengthOf(2);
            expect(msgAfterStep.stack[0].methodName).to.equal("Ping>>#start");
            expect(msgAfterStep.stack[0].sourceSection.startLine).to.equal(17);
          });
          resolve(p);
        });
      });
    }));

    it('should be possible to dynamically activate line breakpoints',
        onlyWithConnection(() => {
      return Promise.all([
        suspendPs[1].then(msgAfterStep => {
          connectionP.then(con => {
            // set another breakpoint, after stepping, and with connection
            const lbp: LineBreakpointData = {
              type: "LineBreakpoint",
              line: 22,
              sourceUri: pingPongUri,
              enabled:   true
            };
            send(con.socket, {action: "updateBreakpoint", breakpoint: lbp});
            send(con.socket, {action: "resume", suspendEvent: msgAfterStep.id});  
          });
        }),
        suspendPs[2].then(msgLineBP => {
          expect(msgLineBP.stack).lengthOf(2);
          expect(msgLineBP.stack[0].methodName).to.equal("Ping>>#ping");
          expect(msgLineBP.stack[0].sourceSection.startLine).to.equal(22);
        })]);
    }));

    it('should be possible to disable a line breakpoint',
        onlyWithConnection(() => {
      return new Promise((resolve, reject) => {
        suspendPs[2].then(msgAfterStep => {
          connectionP.then(con => {
            const lbp22: LineBreakpointData = {
              type: "LineBreakpoint",
              line:      23,
              sourceUri: pingPongUri,
              enabled:   true
            };
            send(con.socket, {action: "updateBreakpoint", breakpoint: lbp22});
            
            const lbp21: LineBreakpointData = {
              type: "LineBreakpoint",
              line:      22,
              sourceUri: pingPongUri,
              enabled:   false
            };
            send(con.socket, {action: "updateBreakpoint", breakpoint: lbp21});
            send(con.socket, {action: "resume", suspendEvent: msgAfterStep.id});

            const p = suspendPs[3].then(msgLineBP => {
              expect(msgLineBP.stack).lengthOf(2);
              expect(msgLineBP.stack[0].methodName).to.equal("Ping>>#ping");
              expect(msgLineBP.stack[0].sourceSection.startLine).to.equal(23);
            });
            resolve(p);
          });
        });
      });
    }));
  });

  describe('execute `1 halt` and get suspended event', () => {
    // Capture first suspended event for testing
    let firstSuspendCaptured = false;
    let getSuspendEvent: (event: OnMessageEvent) => void;
    let suspendP = new Promise<SuspendEventMessage>((resolve, reject) => {
      getSuspendEvent = (event: OnMessageEvent) => {
        if (firstSuspendCaptured) { return; }    
        const data = JSON.parse(event.data);
        if (data.type === "suspendEvent") {
          firstSuspendCaptured = true;
          resolve(data);
        }
      };
    });

    before('Start SOMns and Connect', () => {
      connectionP = startSomAndConnect(getSuspendEvent, [], ['halt']);
    });

    after(closeConnectionAfterSuite);

    it('should halt on expected source section', onlyWithConnection(() => {
      return suspendP.then(msg => {
        expect(msg.stack).lengthOf(7);
        expect(msg.stack[0].methodName).to.equal("PingPongApp>>#testHalt");
        expect(msg.stack[0].sourceSection.startLine).to.equal(66);
      });
    }));
  });
});
