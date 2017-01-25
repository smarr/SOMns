'use strict';

import { expect } from 'chai';
import * as fs from 'fs';
import {X_OK} from 'constants';

import { SomConnection, closeConnection, SOM, OnMessageEvent, PING_PONG_URI,
  expectStack, expectSourceCoordinate, expectFullSourceCoordinate,
  HandleFirstSuspendEvent, startSomAndConnect, send } from './test-setup';

import {SourceMessage, SuspendEventMessage,
  StepMessage, createLineBreakpointData, createSectionBreakpointData} from '../src/messages';

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
    it('SOMns executable should be in SOM_BASEPATH', (done) => {
      fs.access(SOM, X_OK, (err) => {
        expect(err).to.be.null;
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
    let sourceP = new Promise<SourceMessage>((resolve, _reject) => {
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
    const event = new HandleFirstSuspendEvent();

    before('Start SOMns and Connect', () => {
      const breakpoint = createLineBreakpointData(PING_PONG_URI, 70, true);
      connectionP = startSomAndConnect(event.getSuspendEvent, [breakpoint]);
    });

    after(closeConnectionAfterSuite);

    it('should accept line breakpoint, and halt on expected line', onlyWithConnection(() => {
      return event.suspendP.then(msg => {
        expectStack(msg.stack, 7, "PingPong>>#benchmark", 70);
      });
    }));

    it('should have a well structured suspended event', onlyWithConnection(() => {
      return event.suspendP.then(msg => {
        expectFullSourceCoordinate(msg.stack[0].sourceSection);
        expect(msg.id).to.equal("se-0");
        expect(msg.sourceUri).to.equal(PING_PONG_URI);
        expect(msg.topFrame.arguments[0]).to.equal("a PingPong");
        expect(msg.topFrame.slots['Local(ping)']).to.equal('a Nil');
      });
    }));
  });

  describe('setting a source section sender breakpoint', () => {
    const event = new HandleFirstSuspendEvent();

    before('Start SOMns and Connect', () => {
      const breakpoint = createSectionBreakpointData(PING_PONG_URI, 16, 14, 3,
        "MessageSenderBreakpoint", true);
      connectionP = startSomAndConnect(event.getSuspendEvent, [breakpoint]);
    });

    after(closeConnectionAfterSuite);

    it('should accept send breakpoint, and halt on expected source section', onlyWithConnection(() => {
      return event.suspendP.then(msg => {
        expectStack(msg.stack, 2, "Ping>>#start", 16);
      });
    }));
  });

  describe('setting a source section asynchronous method receiver breakpoint', () => {
    const event = new HandleFirstSuspendEvent();

    before('Start SOMns and Connect', () => {
      const breakpoint = createSectionBreakpointData(PING_PONG_URI, 57, 9, 88,
        "AsyncMessageReceiverBreakpoint", true);
      connectionP = startSomAndConnect(event.getSuspendEvent, [breakpoint]);
    });

    after(closeConnectionAfterSuite);

    it('should accept async method receiver breakpoint, and halt on expected source section', onlyWithConnection(() => {
      return event.suspendP.then(msg => {
        expectStack(msg.stack, 2, "Pong>>#ping:", 57);
      });
    }));
  });

  describe('setting a source section receiver breakpoint', () => {
    const event = new HandleFirstSuspendEvent();

    before('Start SOMns and Connect', () => {
      const breakpoint = createSectionBreakpointData(PING_PONG_URI, 16, 14, 3,
        "MessageReceiverBreakpoint", true);
      connectionP = startSomAndConnect(event.getSuspendEvent, [breakpoint]);
    });

    after(closeConnectionAfterSuite);

    it('should accept send breakpoint, and halt on expected source section', onlyWithConnection(() => {
      return event.suspendP.then(msg => {
        expectStack(msg.stack, 2, "Pong>>#ping:", 57);
      });
    }));
  });

 describe('setting a source section promise resolver breakpoint for normal resolution', () => {
    const event = new HandleFirstSuspendEvent();

    before('Start SOMns and Connect', () => {
      const breakpoint = createSectionBreakpointData(PING_PONG_URI, 73, 17, 3,
        "PromiseResolverBreakpoint", true);
      connectionP = startSomAndConnect(event.getSuspendEvent, [breakpoint]);
    });

    after(closeConnectionAfterSuite);

    it('should accept promise resolver breakpoint, and halt on expected source section', onlyWithConnection(() => {
      return event.suspendP.then(msg => {
        expectStack(msg.stack, 2, "Ping>>#start", 15);
      });
    }));
  });

  describe('setting a source section promise resolution breakpoint for normal resolution', () => {
    const event = new HandleFirstSuspendEvent();

    before('Start SOMns and Connect', () => {
      const breakpoint = createSectionBreakpointData(PING_PONG_URI, 73, 17, 3,
        "PromiseResolutionBreakpoint", true);
      connectionP = startSomAndConnect(event.getSuspendEvent, [breakpoint]);
    });

    after(closeConnectionAfterSuite);

    it('should accept promise resolution breakpoint, and halt on expected source section', onlyWithConnection(() => {
      return event.suspendP.then(msg => {
        expectStack(msg.stack, 2, "Thing>>#println", 71);
      });
    }));
  });

  describe('setting a source section promise resolver breakpoint for null resolution', () => {
    const event = new HandleFirstSuspendEvent();

    before('Start SOMns and Connect', () => {
      const breakpoint = createSectionBreakpointData(PING_PONG_URI, 16, 14, 3,
        "PromiseResolverBreakpoint", true);
      connectionP = startSomAndConnect(event.getSuspendEvent, [breakpoint]);
    });

    after(closeConnectionAfterSuite);

    it('should accept promise resolver breakpoint for null resolution, and halt on expected source section', onlyWithConnection(() => {
      return event.suspendP.then(msg => {
        expectStack(msg.stack, 2, "Pong>>#ping:", 57);
      });
    }));
  });

  describe('setting a source section promise resolver breakpoint for chained resolution', () => {
    const event = new HandleFirstSuspendEvent();

    before('Start SOMns and Connect', () => {
      const breakpoint = createSectionBreakpointData(PING_PONG_URI, 26, 20, 3,
        "PromiseResolverBreakpoint", true);
      connectionP = startSomAndConnect(event.getSuspendEvent, [breakpoint]);
    });

    after(closeConnectionAfterSuite);

    it('should accept promise resolver breakpoint for chained resolution, and halt on expected source section', onlyWithConnection(() => {
      return event.suspendP.then(msg => {
        expectStack(msg.stack, 2, "Ping>>#validate:", 31);
      });
    }));
  });

  describe('setting a source section promise resolution breakpoint for chained resolution', () => {
    const event = new HandleFirstSuspendEvent();

    before('Start SOMns and Connect', () => {
      const breakpoint = createSectionBreakpointData(PING_PONG_URI, 26, 20, 3,
        "PromiseResolutionBreakpoint", true);
      connectionP = startSomAndConnect(event.getSuspendEvent, [breakpoint]);
    });

    after(closeConnectionAfterSuite);

    it('should accept promise resolution breakpoint for chained resolution, and halt on expected source section', onlyWithConnection(() => {
      return event.suspendP.then(msg => {
        expectStack(msg.stack, 2, "Ping>>#$blockMethod@27@33:", 27);
      });
    }));
  });

  describe('stepping', () => {
    // Capture suspended events
    const suspendPs: Promise<SuspendEventMessage>[] = [];
    const numSuspends = 4;
    const resolves = [];
    for (let i = 0; i < numSuspends; i++) {
      suspendPs.push(new Promise<SuspendEventMessage>((res, _rej) => resolves.push(res)));
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
      const breakpoint = createSectionBreakpointData(PING_PONG_URI, 16, 14, 3,
        "MessageSenderBreakpoint", true);
      connectionP = startSomAndConnect(getSuspendEvent, [breakpoint]);
    });

    after(closeConnectionAfterSuite);

    it('should stop initially at breakpoint', onlyWithConnection(() => {
      return suspendPs[0].then(msg => {
        expectStack(msg.stack, 2, "Ping>>#start", 16);
      });
    }));

    it('should single stepping', onlyWithConnection(() => {
      return new Promise((resolve, _reject) => {
        suspendPs[0].then(msg => {
          const step : StepMessage = {action: "stepInto", suspendEvent: msg.id};
          connectionP.then(con => {
            send(con.socket, step); });

          const p = suspendPs[1].then(msgAfterStep => {
            expectStack(msgAfterStep.stack, 2, "Ping>>#start", 17);
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
            const lbp = createLineBreakpointData(PING_PONG_URI, 22, true);
            send(con.socket, {action: "updateBreakpoint", breakpoint: lbp});
            send(con.socket, {action: "resume", suspendEvent: msgAfterStep.id});
          });
        }),
        suspendPs[2].then(msgLineBP => {
          expectStack(msgLineBP.stack, 2, "Ping>>#ping", 22);
        })]);
    }));

    it('should be possible to disable a line breakpoint',
        onlyWithConnection(() => {
      return new Promise((resolve, _reject) => {
        suspendPs[2].then(msgAfterStep => {
          connectionP.then(con => {
            const lbp22 = createLineBreakpointData(PING_PONG_URI, 23, true);
            send(con.socket, {action: "updateBreakpoint", breakpoint: lbp22});

            const lbp21 = createLineBreakpointData(PING_PONG_URI, 22, false);
            send(con.socket, {action: "updateBreakpoint", breakpoint: lbp21});
            send(con.socket, {action: "resume", suspendEvent: msgAfterStep.id});

            const p = suspendPs[3].then(msgLineBP => {
              expectStack(msgLineBP.stack, 2, "Ping>>#ping", 23);
            });
            resolve(p);
          });
        });
      });
    }));
  });
});
