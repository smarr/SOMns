"use strict";

import { expect } from "chai";
import * as fs from "fs";
import {X_OK} from "constants";

import { SOM, PING_PONG_URI, ControllerWithInitialBreakpoints,
  TestConnection, HandleStoppedAndGetStackTrace,
  expectStack, expectSourceCoordinate } from "./test-setup";

import { SourceMessage, createLineBreakpointData,
  createSectionBreakpointData } from "../src/messages";

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

describe("Basic Project Setup", () => {
  describe("SOMns is testable", () => {
    it("SOMns executable should be in SOM_BASEPATH", (done) => {
      fs.access(SOM, X_OK, (err) => {
        expect(err).to.be.null;
        done();
      });
    });

    it("should be possible to connect", done => {
      const conn = new TestConnection();
      conn.fullyConnected.then(_ => {
        conn.close(done);
        connectionPossible = true;
      });
      conn.fullyConnected.catch(reason => {
        done(reason);
      });
    });
  });
});

describe("Basic Protocol", function() {
  let conn: TestConnection;

  const closeConnectionAfterSuite = (done) => {
    conn.fullyConnected.then(_ => { conn.close(done); });
    conn.fullyConnected.catch(reason => done(reason));
  };

  describe("source message", () => {
    let sourceP: Promise<SourceMessage>;

    before("Start SOMns and Connect", () => {
      conn = new TestConnection();
      const ctrl = new ControllerWithInitialBreakpoints([], conn);
      let firstSourceCaptured = false;
      sourceP = new Promise<SourceMessage>((resolve, _reject) => {
        ctrl.onReceivedSource = (msg: SourceMessage) => {
          if (firstSourceCaptured) { return; };
          firstSourceCaptured = true;
          resolve(msg);
        };
      });
    });

    after(closeConnectionAfterSuite);

    it("should have sources", onlyWithConnection(() => {
      return sourceP.then(sourceMsg => {
        for (let source of sourceMsg.sources) {
          expect(source.mimeType).to.equal("application/x-newspeak-som-ns");
          expect(source.name).to.equal("Platform.som");
          expect(source).to.have.property("sourceText");
          expect(source).to.have.property("uri");
          return;
        }
      });
    }));

    it("should have source sections", onlyWithConnection(() => {
      return sourceP.then(sourceMsg => {
        for (let s of sourceMsg.sources) {
          for (let ss of s.sections) {
            expectSourceCoordinate(ss);
            return;
          }
        }
      });
    }));

    it("should have methods", onlyWithConnection(() => {
      return sourceP.then(sourceMsg => {
        for (let s of sourceMsg.sources) {
          for (let method of s.methods) {
            expect(method).to.have.property("name");
            expect(method).to.have.property("definition");

            const def = method.definition[0];
            expectSourceCoordinate(def);
            expectSourceCoordinate(method.sourceSection);
            return;
          }
        }
      });
    }));
  });

  const breakpointTests = [
    {suite:       "setting a line breakpoint",
     breakpoint:  createLineBreakpointData(PING_PONG_URI, 70, true),
     test:        "should accept line breakpoint, and halt on expected line",
     stackLength: 7,
     topMethod:   "PingPong>>#benchmark",
     line:        70},

    {suite:       "setting a source section sender breakpoint",
     breakpoint:  createSectionBreakpointData(PING_PONG_URI, 16, 14, 3, "MessageSenderBreakpoint", true),
     test:        "should accept send breakpoint, and halt on expected source section",
     stackLength: 2,
     topMethod:   "Ping>>#start",
     line:        16},

    {suite:       "setting a source section asynchronous method receiver breakpoint",
     breakpoint:  createSectionBreakpointData(PING_PONG_URI, 57, 9, 88, "AsyncMessageReceiverBreakpoint", true),
     test:        "should accept async method receiver breakpoint, and halt on expected source section",
     stackLength: 2,
     topMethod:   "Pong>>#ping:",
     line:        57},

    {suite:       "setting a source section receiver breakpoint",
     breakpoint:  createSectionBreakpointData(PING_PONG_URI, 16, 14, 3, "MessageReceiverBreakpoint", true),
     test:        "should accept send breakpoint, and halt on expected source section",
     stackLength: 2,
     topMethod:   "Pong>>#ping:",
     line:        57},

     {suite:      "setting a source section promise resolver breakpoint for normal resolution",
     breakpoint:  createSectionBreakpointData(PING_PONG_URI, 73, 17, 3, "PromiseResolverBreakpoint", true),
     test:        "should accept promise resolver breakpoint, and halt on expected source section",
     stackLength: 2,
     topMethod:   "Ping>>#start",
     line:        15},

     {suite:      "setting a source section promise resolution breakpoint for normal resolution",
     breakpoint:  createSectionBreakpointData(PING_PONG_URI, 73, 17, 3, "PromiseResolutionBreakpoint", true),
     test:        "should accept promise resolution breakpoint, and halt on expected source section",
     stackLength: 2,
     topMethod:   "Thing>>#println",
     line:        71},

     {suite:      "setting a source section promise resolver breakpoint for null resolution",
     breakpoint:  createSectionBreakpointData(PING_PONG_URI, 16, 14, 3, "PromiseResolverBreakpoint", true),
     test:        "should accept promise resolver breakpoint for null resolution, and halt on expected source section",
     stackLength: 2,
     topMethod:   "Pong>>#ping:",
     line:        57},

     {suite:      "setting a source section promise resolver breakpoint for chained resolution",
     breakpoint:  createSectionBreakpointData(PING_PONG_URI, 26, 20, 3, "PromiseResolverBreakpoint", true),
     test:        "should accept promise resolver breakpoint for chained resolution, and halt on expected source section",
     stackLength: 2,
     topMethod:   "Ping>>#validate:",
     line:        31},

     {suite:      "setting a source section promise resolution breakpoint for chained resolution",
     breakpoint:  createSectionBreakpointData(PING_PONG_URI, 26, 20, 3, "PromiseResolutionBreakpoint", true),
     test:        "should accept promise resolution breakpoint for chained resolution, and halt on expected source section",
     stackLength: 2,
     topMethod:   "Ping>>#$blockMethod@27@33:",
     line:        27}
  ];

  breakpointTests.forEach(desc => {
    describe(desc.suite, () => {
      let ctrl: HandleStoppedAndGetStackTrace;

      before("Start SOMns and Connect", () => {
        conn = new TestConnection();
        ctrl = new HandleStoppedAndGetStackTrace([desc.breakpoint], conn);
      });

      after(closeConnectionAfterSuite);

      it(desc.test, onlyWithConnection(() => {
        return ctrl.stackP.then(msg => {
          expectStack(msg.stackFrames, desc.stackLength, desc.topMethod, desc.line);
        });
      }));
    });
  });

  describe("stepping", () => {
    // Capture suspended events
    let ctrl: HandleStoppedAndGetStackTrace;

    before("Start SOMns and Connect", () => {
      const breakpoint = createSectionBreakpointData(PING_PONG_URI, 16, 14, 3,
        "MessageSenderBreakpoint", true);
      conn = new TestConnection();
      ctrl = new HandleStoppedAndGetStackTrace([breakpoint], conn, 4);
    });

    after(closeConnectionAfterSuite);

    it("should stop initially at breakpoint", onlyWithConnection(() => {
      return ctrl.stackP.then(msg => {
        expectStack(msg.stackFrames, 2, "Ping>>#start", 16);
      });
    }));

    it("should single stepping", onlyWithConnection(() => {
      return new Promise((resolve, _reject) => {
        ctrl.stackP.then(_ => {
          conn.fullyConnected.then(_ => {
            conn.sendDebuggerAction("stepInto", ctrl.stoppedActivities[0]);
          });

          const p = ctrl.getStackP(1).then(msgAfterStep => {
            expectStack(msgAfterStep.stackFrames, 2, "Ping>>#start", 17);
          });
          resolve(p);
        });
      });
    }));

    it("should be possible to dynamically activate line breakpoints",
        onlyWithConnection(() => {
      return Promise.all([
        ctrl.getStackP(1).then(_ => {
          conn.fullyConnected.then(_ => {
            // set another breakpoint, after stepping, and with connection
            const lbp = createLineBreakpointData(PING_PONG_URI, 22, true);
            conn.updateBreakpoint(lbp);
            conn.sendDebuggerAction("resume", ctrl.stoppedActivities[1]);
          });
        }),
        ctrl.getStackP(2).then(msgLineBP => {
          expectStack(msgLineBP.stackFrames, 2, "Ping>>#ping", 22);
        })]);
    }));

    it("should be possible to disable a line breakpoint",
        onlyWithConnection(() => {
      return new Promise((resolve, _reject) => {
        ctrl.getStackP(2).then(_ => {
          conn.fullyConnected.then(_ => {
            const lbp22 = createLineBreakpointData(PING_PONG_URI, 23, true);
            conn.updateBreakpoint(lbp22);

            const lbp21 = createLineBreakpointData(PING_PONG_URI, 22, false);
            conn.updateBreakpoint(lbp21);
            conn.sendDebuggerAction("resume", ctrl.stoppedActivities[2]);

            const p = ctrl.getStackP(3).then(msgLineBP => {
              expectStack(msgLineBP.stackFrames, 2, "Ping>>#ping", 23);
            });
            resolve(p);
          });
        });
      });
    }));
  });
});
