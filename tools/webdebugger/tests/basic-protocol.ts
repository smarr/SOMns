'use strict';

const debuggerPort = 7977;
const somBasepath = '../../';
const som = somBasepath + 'som';

import * as WebSocket from 'ws';
import { expect } from 'chai';
import { spawn, ChildProcess } from 'child_process';
import { resolve } from 'path';
import * as fs from 'fs';


interface SomConnection {
  somProc: ChildProcess;
  socket:  WebSocket;
  closed:  boolean;
}

interface OnMessageEvent {
  data:   any;
  type:   string;
  target: WebSocket
}

interface OnMessageHandler {
  (event: OnMessageEvent): void
}

type MessageType = "source" | "suspendEvent";

interface Message {
  type: MessageType;
}

interface Source {
  id:         string;
  sourceText: string;
  mimeType:   string;
  name:       string;
  uri:        string;
}

interface IdMap<T> {
  [key: string]: T;
}

interface SimpleSourceSection {
  firstIndex: number;
  length:     number;
  line:       number;
  column:     number;
}

interface SourceSection extends SimpleSourceSection {
  id:          string;
  description: string;
  sourceId:    string;
}

interface Method {
  name:          string;
  definition:    SimpleSourceSection[];
  sourceSection: SourceSection;
}

interface SourceMessage extends Message {
  sources:  IdMap<Source>;
  sections: IdMap<SourceSection>;
  methods:  Method[];
}

interface Frame {
  sourceSection: SourceSection;
  methodName: string;
}

interface TopFrame {
  arguments: string[];
  slots:     IdMap<string>;
}

interface SuspendEventMessage extends Message {
  sourceId: string;
  sections: SourceSection[];
  stack:    Frame[];
  topFrame: TopFrame;
  id: string;
}

type RespondType = "initialBreakpoints" | "updateBreakpoint" | "stepInto" |
                    "stepOver" | "return" | "resume" | "stop";

interface Respond {
  action: RespondType;
}

type BreakpointType = "lineBreakpoint" |
                      "sendBreakpoint" |
                      "asyncMsgRcvBreakpoint";

interface Breakpoint {
  type:      BreakpointType;
  sourceUri: string;
  enabled:   boolean;
}

interface LineBreakpoint extends Breakpoint {
  line: number;
}

type SendBreakpointType = "receiver" | "sender";

interface SendBreakpoint extends Breakpoint {
  sectionId:   string;
  startLine:   number;
  startColumn: number;
  charLength:  number;
  role:        SendBreakpointType;
}

interface AsyncMethodRcvBreakpoint extends Breakpoint {
  sectionId:   string;
  startLine:   number;
  startColumn: number;
  charLength:  number;
} 

interface InitialBreakpointsResponds extends Respond {
  breakpoints: Breakpoint[];
}

interface UpdateBreakpoint extends Respond {
  breakpoint: Breakpoint;
}

interface StepMessage extends Respond {
  // TODO: should be renamed to suspendEventId
  /** Id of the corresponding suspend event. */
  suspendEvent: string;
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

function getInitialBreakpointsResponds(breakpoints: Breakpoint[]): string {
  return JSON.stringify({action: "initialBreakpoints", breakpoints});
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
    initialBreakpoints?: Breakpoint[]): Promise<SomConnection> {
  const somProc = spawn(som, ['-G', '-t1', '-wd', 'tests/pingpong.som']);
  const promise = new Promise((resolve, reject) => {
    let connecting = false;
    somProc.stdout.on('data', (data) => {
      if (data.toString().includes("Started HTTP Server") && !connecting) {
        connecting = true;
        const socket = new WebSocket('ws://localhost:' + debuggerPort);
        socket.on('open', () => {
          if (initialBreakpoints) {
            socket.send(getInitialBreakpointsResponds(initialBreakpoints));
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
function onlyWithConection(fn: ActionFunction) {
  return function(done) {
    if (connectionPossible) {
      try {
        fn(done);
      } catch (e) {
        done(e);
      }
    } else {
      this.skip();
    }
  }
}

describe('Basic Project Setup', () => {
  describe('SOMns is testable', () => {
    it('SOMns executable should be in somBasepath', (done) => {
      fs.access(som, fs.X_OK, (err) => {
        expect(err).to.be.null;
        done();
      })
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
      })
    })
  });
});

describe('Basic Protocol', function() {
  let connectionP : Promise<SomConnection> = null;

  describe('source message', () => {
    // Capture first source message for testing
    let firstSourceCaptured = false;
    let getSourceData: (event: OnMessageEvent) => void;
    let sourceP = new Promise<SourceMessage>((resolve, reject) => {
      getSourceData = (event: OnMessageEvent) => {
        if (firstSourceCaptured) { return; }    
        const data = JSON.parse(event.data);
        if (data.type == "source") {
          firstSourceCaptured = true;
          resolve(data);
        }
      }
    });

    before('Start SOMns and Connect', () => {
      connectionP = startSomAndConnect(getSourceData, []);
    });

    after((done) => {
      connectionP.then(c => { closeConnection(c, done);});
      connectionP.catch(reason => done(reason));
    });

    it('should have sources', onlyWithConection(done => {
      sourceP.then(sourceMsg => {
        for (let sourceId in sourceMsg.sources) {
          try {
            expect(sourceId).to.equal("s-0");
            const source = sourceMsg.sources[sourceId];
            expect(source.id).to.equal(sourceId);
            expect(source.mimeType).to.equal("application/x-newspeak-som-ns");
            expect(source.name).to.equal("Platform.som");
            expect(source).to.have.property('sourceText');
            expect(source).to.have.property('uri');
            done();
          } catch (e) {
            done(e);
          }
          return;
        }
      });
    }));

    it('should have source sections', onlyWithConection(done => {
      sourceP.then(sourceMsg => {
        for (let ssId in sourceMsg.sections) {
          try {
            const section = sourceMsg.sections[ssId];
            expectSourceSection(section);
            done();
          } catch (e) {
            done(e);
          }
          return;
        }
      });
    }));

    it('should have methods', onlyWithConection(done => {
      sourceP.then(sourceMsg => {
        for (let method of sourceMsg.methods) {
          try {
            expect(method).to.have.property('name');
            expect(method).to.have.property('definition');

            const def = method.definition[0];
            expectSimpleSourceSection(def);
            expectSourceSection(method.sourceSection);
            done();
          } catch (e) {
            done(e);
          }
          return;
        }
      });
    }));
  });

  describe('setting a line breakpoint', () => {
    let firstSuspendCaptured = false;
    let getSuspendEvent: (event: OnMessageEvent) => void;
    let suspendP = new Promise<SuspendEventMessage>((resolve, reject) => {
      getSuspendEvent = (event: OnMessageEvent) => {
        if (firstSuspendCaptured) { return; }    
        const data = JSON.parse(event.data);
        if (data.type == "suspendEvent") {
          firstSuspendCaptured = true;
          resolve(data);
        }
      }
    });

    before('Start SOMns and Connect', () => {
      const breakpoint: LineBreakpoint = {
        type: "lineBreakpoint",
        line: 52,
        sourceUri: 'file:' + resolve('tests/pingpong.som'),
        enabled: true
      };
      connectionP = startSomAndConnect(getSuspendEvent, [breakpoint]);
    });

    after((done) => {
      connectionP.then(c => { closeConnection(c, done);});
      connectionP.catch(reason => done(reason));
    });

    it('should accept line breakpoint, and halt on expected line', onlyWithConection(done => {
      suspendP.then(msg => {
        try {
          expect(msg.stack).lengthOf(7);
          expect(msg.stack[0].methodName).to.equal("PingPong>>#benchmark");
          expect(msg.stack[0].sourceSection.line).to.equal(52);
          done();
        } catch (e) {
          done(e);
        }
      });
    }));

    it('should have a well structured suspended event', onlyWithConection(done => {
      suspendP.then(msg => {
        try {
          expectSourceSection(msg.stack[0].sourceSection);
          expectSourceSection(msg.sections[msg.stack[0].sourceSection.id]);
          expect(msg.id).to.equal("se-0");
          expect(msg.sourceId).to.equal("s-6");
          expect(msg.topFrame.arguments[0]).to.equal("a PingPong");
          expect(msg.topFrame.slots['ping']).to.equal('null');
          done();          
        } catch (e) {
          done(e);
        }
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
        if (data.type == "suspendEvent") {
          firstSuspendCaptured = true;
          resolve(data);
        }
      }
    });

    before('Start SOMns and Connect', () => {
      const breakpoint: SendBreakpoint = {
        type: "sendBreakpoint",
        sourceUri: 'file:' + resolve('tests/pingpong.som'),
        enabled: true,
        sectionId:   'ss-7291',
        startLine:   15,
        startColumn: 14,
        charLength:  3,
        role:        "sender"
      };
      connectionP = startSomAndConnect(getSuspendEvent, [breakpoint]);
    });

    after((done) => {
      connectionP.then(c => { closeConnection(c, done);});
      connectionP.catch(reason => done(reason));
    });

    it('should accept send breakpoint, and halt on expected source section', onlyWithConection(done => {
      suspendP.then(msg => {
        try {
          expect(msg.stack).lengthOf(2);
          expect(msg.stack[0].methodName).to.equal("Ping>>#start");
          expect(msg.stack[0].sourceSection.line).to.equal(15);
          done();
        } catch (e) {
          done(e);
        }
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
        if (data.type == "suspendEvent") {
          firstSuspendCaptured = true;
          resolve(data);
        }
      }
    });

    before('Start SOMns and Connect', () => {
      const breakpoint: SendBreakpoint = {
        type: "sendBreakpoint",
        sourceUri: 'file:' + resolve('tests/pingpong.som'),
        enabled: true,
        sectionId:   'ss-7291',
        startLine:   15,
        startColumn: 14,
        charLength:  3,
        role:        "receiver"
      };
      connectionP = startSomAndConnect(getSuspendEvent, [breakpoint]);
    });

    after((done) => {
      connectionP.then(c => { closeConnection(c, done);});
      connectionP.catch(reason => done(reason));
    });

    it('should accept send breakpoint, and halt on expected source section', onlyWithConection(done => {
      suspendP.then(msg => {
        try {
          expect(msg.stack).lengthOf(2);
          expect(msg.stack[0].methodName).to.equal("Pong>>#ping:");
          expect(msg.stack[0].sourceSection.line).to.equal(39);
          done();
        } catch (e) {
          done(e);
        }
      });
    }));
  });

  it('should accept source section breakpoint format (promise), and halt at resolution');

  describe('stepping', () => {
    // Capture suspended events
    const suspendPs: Promise<SuspendEventMessage>[] = [];
    const resolves = [];
    suspendPs.push(new Promise<SuspendEventMessage>((res, rej) => resolves.push(res)));
    suspendPs.push(new Promise<SuspendEventMessage>((res, rej) => resolves.push(res)));

    let capturedEvents = 0;
    let getSuspendEvent: (event: OnMessageEvent) => void;
    
    getSuspendEvent = (event: OnMessageEvent) => {
      if (capturedEvents > 2) { return; }    
      const data = JSON.parse(event.data);
      if (data.type == "suspendEvent") {
        resolves[capturedEvents](data);
        capturedEvents += 1;
      }
    }

    before('Start SOMns and Connect', () => {
      const breakpoint: SendBreakpoint = {
        type: "sendBreakpoint",
        sourceUri: 'file:' + resolve('tests/pingpong.som'),
        enabled: true,
        sectionId:   'ss-7291',
        startLine:   15,
        startColumn: 14,
        charLength:  3,
        role:        "sender"
      };
      connectionP = startSomAndConnect(getSuspendEvent, [breakpoint]);
    });
    after((done) => {
      connectionP.then(c => { closeConnection(c, done);});
      connectionP.catch(reason => done(reason));
    });

    it('should stop initially at breakpoint', onlyWithConection(done => {
      suspendPs[0].then(msg => {
        try {
          expect(msg.stack).lengthOf(2);
          expect(msg.stack[0].methodName).to.equal("Ping>>#start");
          expect(msg.stack[0].sourceSection.line).to.equal(15);
          done();
        } catch (e) {
          done(e);
        }
      });
    }));

    it('should single stepping', onlyWithConection(done => {
      suspendPs[0].then(msg => {
        const step : StepMessage = {action: "stepInto", suspendEvent: msg.id};
        connectionP.then(con => {
          con.socket.send(JSON.stringify(step))});

        suspendPs[1].then(msgAfterStep => {
          try {
            expect(msgAfterStep.stack).lengthOf(2);
            expect(msgAfterStep.stack[0].methodName).to.equal("Ping>>#start");
            expect(msgAfterStep.stack[0].sourceSection.line).to.equal(16);
            done();
          } catch (e) {
            done(e);
          }
        });
      });
    }));


    it('should be possible to dynamically activate line breakpoints',
        onlyWithConection(done => {
      suspendPs[1].then(msgAfterStep => {
        connectionP.then(con => {
          // set another breakpoint, after stepping, and with connection
          const lbp: LineBreakpoint = {
            type: "lineBreakpoint",
            line: 21,
            sourceUri: 'file:' + resolve('tests/pingpong.som'),
            enabled: true
          };
          con.socket.send(JSON.stringify(
            {action: "updateBreakpoint", breakpoint: lbp}));
          con.socket.send(JSON.stringify(
            {action: "resume", suspendEvent: msgAfterStep.id}
          ));  
        });
      });

      suspendPs[2].then(msgLineBP => {
        try {
          expect(msgLineBP.stack).lengthOf(2);
          expect(msgLineBP.stack[0].methodName).to.equal("Ping>>#ping");
          expect(msgLineBP.stack[0].sourceSection.line).to.equal(21);
          done();
        } catch (e) {
          done(e);
        }
      });
    }));

    it('should be possible to disable a line breakpoint',
        onlyWithConection(done => {
      suspendPs[2].then(msgAfterStep => {
        connectionP.then(con => {
          const lbp22: LineBreakpoint = {
            type: "lineBreakpoint",
            line: 22,
            sourceUri: 'file:' + resolve('tests/pingpong.som'),
            enabled: true
          };
          con.socket.send(JSON.stringify(
            {action: "updateBreakpoint", breakpoint: lbp22}));
          
          const lbp21: LineBreakpoint = {
            type: "lineBreakpoint",
            line: 21,
            sourceUri: 'file:' + resolve('tests/pingpong.som'),
            enabled: false
          };
          con.socket.send(JSON.stringify(
            {action: "updateBreakpoint", breakpoint: lbp21}));
          con.socket.send(JSON.stringify(
            {action: "resume", suspendEvent: msgAfterStep.id}));

          suspendPs[3].then(msgLineBP => {
            try {
              expect(msgLineBP.stack).lengthOf(2);
              expect(msgLineBP.stack[0].methodName).to.equal("Ping>>#ping");
              expect(msgLineBP.stack[0].sourceSection.line).to.equal(22);
              done();
            } catch (e) {
              done(e);
            }
          });
        });
      })
    }));
  });
});
