'use strict';

const debuggerPort = 7977;
const somBasepath = '../../';
const som = somBasepath + 'som';

import * as WebSocket from 'ws';
import { expect } from 'chai';
import { spawn, ChildProcess } from 'child_process';
import { resolve } from 'path';
import * as fs from 'fs';
import {X_OK} from 'constants';

import {SimpleSourceSection, SourceSection, SourceMessage, SuspendEventMessage,
  BreakpointData, LineBreakpointData, SectionBreakpointData, Respond,
  StepMessage} from '../src/messages';

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

function expectSimpleSourceSection(section: SimpleSourceSection) {
  expect(section).to.have.property('firstIndex');
  expect(section).to.have.property('length');
  expect(section).to.have.property('column');
  expect(section).to.have.property('line');
}

function expectSourceSection(section: SourceSection) {
  expect(section).to.have.property('id');
  expect(section).to.have.property('sourceId');
  expect(section.id.startsWith("ss")).to.be.true;
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
    initialBreakpoints?: BreakpointData[]): Promise<SomConnection> {
  const somProc = spawn(som, ['-G', '-t1', '-wd', 'tests/pingpong.som']);
  const promise = new Promise((resolve, reject) => {
    let connecting = false;
    somProc.stdout.on('data', (data) => {
      if (data.toString().includes("Started HTTP Server") && !connecting) {
        connecting = true;
        const socket = new WebSocket('ws://localhost:' + debuggerPort);
        socket.on('open', () => {
          if (initialBreakpoints) {
            send(socket, {action: "initialBreakpoints", breakpoints: initialBreakpoints});
          }

          resolve({somProc: somProc, socket: socket, closed: false});
        });
        if (onMessageHandler) {
          socket.onmessage = onMessageHandler;
        }
      }
      if (data.toString().includes("Failed starting WebSocket and/or HTTP Server")) {
        reject(new Error('SOMns failed to starting WebSocket and/or HTTP Server'));
      }
    });
  });
  return promise;
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
        for (let sourceId in sourceMsg.sources) {
          expect(sourceId).to.equal("s-0");
          const source = sourceMsg.sources[sourceId];
          expect(source.id).to.equal(sourceId);
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
        for (let ssId in sourceMsg.sections) {
          const section = sourceMsg.sections[ssId];
          expectSourceSection(section);
          return;
        }
      });
    }));

    it('should have methods', onlyWithConnection(() => {
      return sourceP.then(sourceMsg => {
        for (let method of sourceMsg.methods) {
          expect(method).to.have.property('name');
          expect(method).to.have.property('definition');

          const def = method.definition[0];
          expectSimpleSourceSection(def);
          expectSourceSection(method.sourceSection);
          return;
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
        line: 52,
        sourceUri: 'file:' + resolve('tests/pingpong.som'),
        enabled: true
      };
      connectionP = startSomAndConnect(getSuspendEvent, [breakpoint]);
    });

    after(closeConnectionAfterSuite);

    it('should accept line breakpoint, and halt on expected line', onlyWithConnection(() => {
      return suspendP.then(msg => {
        expect(msg.stack).lengthOf(7);
        expect(msg.stack[0].methodName).to.equal("PingPong>>#benchmark");
        expect(msg.stack[0].sourceSection.line).to.equal(52);
      });
    }));

    it('should have a well structured suspended event', onlyWithConnection(() => {
      return suspendP.then(msg => {
        expectSourceSection(msg.stack[0].sourceSection);
        expectSourceSection(msg.sections[msg.stack[0].sourceSection.id]);
        expect(msg.id).to.equal("se-0");
        expect(msg.sourceId).to.equal("s-6");
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
          uri:        'file:' + resolve('tests/pingpong.som'),
          startLine:   15,
          startColumn: 14,
          charLength:   3}};
      connectionP = startSomAndConnect(getSuspendEvent, [breakpoint]);
    });

    after(closeConnectionAfterSuite);

    it('should accept send breakpoint, and halt on expected source section', onlyWithConnection(() => {
      return suspendP.then(msg => {
        expect(msg.stack).lengthOf(2);
        expect(msg.stack[0].methodName).to.equal("Ping>>#start");
        expect(msg.stack[0].sourceSection.line).to.equal(15);
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
        type: "MessageReceiveBreakpoint",
        enabled: true,
        coord: {
          uri:        'file:' + resolve('tests/pingpong.som'),
          startLine:   15,
          startColumn: 14,
          charLength:   3}};
      connectionP = startSomAndConnect(getSuspendEvent, [breakpoint]);
    });

    after(closeConnectionAfterSuite);

    it('should accept send breakpoint, and halt on expected source section', onlyWithConnection(() => {
      return suspendP.then(msg => {
        expect(msg.stack).lengthOf(2);
        expect(msg.stack[0].methodName).to.equal("Pong>>#ping:");
        expect(msg.stack[0].sourceSection.line).to.equal(39);
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
          uri:        'file:' + resolve('tests/pingpong.som'),
          startLine:   15,
          startColumn: 14,
          charLength:   3}};
      connectionP = startSomAndConnect(getSuspendEvent, [breakpoint]);
    });

    after(closeConnectionAfterSuite);

    it('should stop initially at breakpoint', onlyWithConnection(() => {
      return suspendPs[0].then(msg => {
        expect(msg.stack).lengthOf(2);
        expect(msg.stack[0].methodName).to.equal("Ping>>#start");
        expect(msg.stack[0].sourceSection.line).to.equal(15);
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
            expect(msgAfterStep.stack[0].sourceSection.line).to.equal(16);
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
              line: 21,
              sourceUri: 'file:' + resolve('tests/pingpong.som'),
              enabled: true
            };
            send(con.socket, {action: "updateBreakpoint", breakpoint: lbp});
            send(con.socket, {action: "resume", suspendEvent: msgAfterStep.id});  
          });
        }),
        suspendPs[2].then(msgLineBP => {
          expect(msgLineBP.stack).lengthOf(2);
          expect(msgLineBP.stack[0].methodName).to.equal("Ping>>#ping");
          expect(msgLineBP.stack[0].sourceSection.line).to.equal(21);
        })]);
    }));

    it('should be possible to disable a line breakpoint',
        onlyWithConnection(() => {
      return new Promise((resolve, reject) => {
        suspendPs[2].then(msgAfterStep => {
          connectionP.then(con => {
            const lbp22: LineBreakpointData = {
              type: "LineBreakpoint",
              line: 22,
              sourceUri: 'file:' + resolve('tests/pingpong.som'),
              enabled: true
            };
            send(con.socket, {action: "updateBreakpoint", breakpoint: lbp22});
            
            const lbp21: LineBreakpointData = {
              type: "LineBreakpoint",
              line: 21,
              sourceUri: 'file:' + resolve('tests/pingpong.som'),
              enabled: false
            };
            send(con.socket, {action: "updateBreakpoint", breakpoint: lbp21});
            send(con.socket, {action: "resume", suspendEvent: msgAfterStep.id});

            const p = suspendPs[3].then(msgLineBP => {
              expect(msgLineBP.stack).lengthOf(2);
              expect(msgLineBP.stack[0].methodName).to.equal("Ping>>#ping");
              expect(msgLineBP.stack[0].sourceSection.line).to.equal(22);
            });
            resolve(p);
          });
        });
      });
    }));
  });
});
