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
        const source = sourceMsg.source;
        expect(source.mimeType).to.equal("application/x-newspeak-som-ns");
        expect(source.name).to.equal("Platform.som");
        expect(source).to.have.property("sourceText");
        expect(source).to.have.property("uri");
      });
    }));

    it("should have source sections", onlyWithConnection(() => {
      return sourceP.then(sourceMsg => {
        const s = sourceMsg.source;
        for (let ss of s.sections) {
          expectSourceCoordinate(ss);
          return;
        }
      });
    }));

    it("should have methods", onlyWithConnection(() => {
      return sourceP.then(sourceMsg => {
        const s = sourceMsg.source;
        for (let method of s.methods) {
          expect(method).to.have.property("name");
          expect(method).to.have.property("definition");

          const def = method.definition[0];
          expectSourceCoordinate(def);
          expectSourceCoordinate(method.sourceSection);
          return;
        }
      });
    }));
  });

  const breakpointTests = [
    {suite:       "setting a line breakpoint",
     breakpoint:  createLineBreakpointData(PING_PONG_URI, 92, true),
     test:        "should accept line breakpoint, and halt on expected line",
     stackLength: 6,
     topMethod:   "PingPong>>#benchmark",
     line:        92},

    {suite:       "setting a source section sender breakpoint",
     breakpoint:  createSectionBreakpointData(PING_PONG_URI, 23, 14, 3, "MessageSenderBreakpoint", true),
     test:        "should accept send breakpoint, and halt on expected source section",
     stackLength: 2,
     topMethod:   "Ping>>#ping",
     line:        23},

    {suite:       "setting a source section asynchronous method before execution breakpoint",
     breakpoint:  createSectionBreakpointData(PING_PONG_URI, 22, 9, 206, "AsyncMessageBeforeExecutionBreakpoint", true),
     test:        "should accept async method before execution breakpoint, and halt on expected source section",
     stackLength: 1,
     topMethod:   "Ping>>#ping",
     line:        22},

    {suite:       "setting a source section asynchronous message after execution breakpoint",
     breakpoint:  createSectionBreakpointData(PING_PONG_URI, 69, 9, 88, "AsyncMessageAfterExecutionBreakpoint", true),
     test:        "should accept async message after execution breakpoint, and halt on expected source section",
     stackLength: 1,
     topMethod:   "Pong>>#ping:",
     line:        69},

    {suite:       "setting a source section receiver breakpoint",
     breakpoint:  createSectionBreakpointData(PING_PONG_URI, 23, 14, 3, "MessageReceiverBreakpoint", true),
     test:        "should accept send breakpoint, and halt on expected source section",
     stackLength: 1,
     topMethod:   "Pong>>#ping:",
     line:        69},

     {suite:      "setting a source section promise resolver breakpoint for normal resolution",
     breakpoint:  createSectionBreakpointData(PING_PONG_URI, 95, 17, 3, "PromiseResolverBreakpoint", true),
     test:        "should accept promise resolver breakpoint, and halt on expected source section",
     stackLength: 1,
     topMethod:   "Ping>>#start",
     line:        16},

     {suite:      "setting a source section promise resolution breakpoint for normal resolution",
     breakpoint:  createSectionBreakpointData(PING_PONG_URI, 95, 17, 3, "PromiseResolutionBreakpoint", true),
     test:        "should accept promise resolution breakpoint, and halt on expected source section",
     stackLength: 1,
     topMethod:   "Thing>>#println",
     line:        71},

     {suite:      "setting a source section promise resolver breakpoint for null resolution",
     breakpoint:  createSectionBreakpointData(PING_PONG_URI, 23, 14, 3, "PromiseResolverBreakpoint", true),
     test:        "should accept promise resolver breakpoint for null resolution, and halt on expected source section",
     stackLength: 1,
     topMethod:   "Pong>>#ping:",
     line:        69},

     {suite:      "setting a source section promise resolver breakpoint for chained resolution",
     breakpoint:  createSectionBreakpointData(PING_PONG_URI, 26, 20, 3, "PromiseResolverBreakpoint", true),
     test:        "should accept promise resolver breakpoint for chained resolution, and halt on expected source section",
     stackLength: 1,
     topMethod:   "Ping>>#validate:",
     line:        33},

     {suite:      "setting a source section promise resolution breakpoint for chained resolution",
     breakpoint:  createSectionBreakpointData(PING_PONG_URI, 26, 20, 3, "PromiseResolutionBreakpoint", true),
     test:        "should accept promise resolution breakpoint for chained resolution, and halt on expected source section",
     stackLength: 1,
     topMethod:   "Ping>>#λping@27@39:",
     line:        27},

     {suite:      "setting a source section promise resolution breakpoint on unresolved explicit promise",
     breakpoint:  createSectionBreakpointData(PING_PONG_URI, 92, 30, 17, "PromiseResolutionBreakpoint", true),
     test:        "should accept promise resolution breakpoint on unresolved explicit promise, and halt on expected source section",
     stackLength: 1,
     topMethod:   "PingPong>>#λbenchmark@98@11:",
     line:        98},

     {suite:      "setting a source section promise resolver breakpoint on unresolved explicit promise",
     breakpoint:  createSectionBreakpointData(PING_PONG_URI, 92, 30, 17, "PromiseResolverBreakpoint", true),
     test:        "should accept promise resolver breakpoint on unresolved explicit promise, and halt on expected source section",
     stackLength: 1,
     topMethod:   "Pong>>#stop",
     line:        80},

     {suite:      "setting a source section promise resolution breakpoint on resolved explicit promise",
     breakpoint:  createSectionBreakpointData(PING_PONG_URI, 41, 22, 17, "PromiseResolutionBreakpoint", true),
     test:        "should accept promise resolution breakpoint on resolved explicit promise, and halt on expected source section",
     stackLength: 1,
     topMethod:   "Thing>>#println",
     line:        71},

     {suite:      "setting a source section promise resolver breakpoint on resolved explicit promise",
     breakpoint:  createSectionBreakpointData(PING_PONG_URI, 41, 22, 17, "PromiseResolverBreakpoint", true),
     test:        "should accept promise resolver breakpoint on resolved explicit promise, and halt on expected source section",
     stackLength: 1,
     topMethod:   "Ping>>#validNumber:",
     line:        49},

     {suite:      "setting a source section promise resolver breakpoint on whenResolved",
     breakpoint:  createSectionBreakpointData(PING_PONG_URI, 27, 18, 32, "PromiseResolverBreakpoint", true),
     test:        "should accept promise resolver breakpoint on whenResolved, and halt on expected source section",
     stackLength: 1,
     topMethod:   "Ping>>#λping@27@39:",
     line:        27},

     {suite:      "setting a source section promise resolution breakpoint on whenResolved",
     breakpoint:  createSectionBreakpointData(PING_PONG_URI, 27, 18, 32, "PromiseResolutionBreakpoint", true),
     test:        "should accept promise resolution breakpoint on whenResolved, and halt on expected source section",
     stackLength: 1,
     topMethod:   "Thing>>#println",
     line:        71},

     {suite:      "setting a source section promise resolver breakpoint onError",
     breakpoint:  createSectionBreakpointData(PING_PONG_URI, 78, 19, 50, "PromiseResolverBreakpoint", true),
     test:        "should accept promise resolver breakpoint onError, and halt on expected source section",
     stackLength: 1,
     topMethod:   "Pong>>#λstop@78@34:",
     line:        78},

     {suite:      "setting a source section promise resolution breakpoint onError",
     breakpoint:  createSectionBreakpointData(PING_PONG_URI, 78, 19, 50, "PromiseResolutionBreakpoint", true),
     test:        "should accept promise resolution breakpoint onError, and halt on expected source section",
     stackLength: 1,
     topMethod:   "Thing>>#println",
     line:        71},

     {suite:      "setting a source section promise resolver breakpoint on whenResolvedOnError",
     breakpoint:  createSectionBreakpointData(PING_PONG_URI, 34, 18, 91, "PromiseResolverBreakpoint", true),
     test:        "should accept promise resolver breakpoint whenResolvedOnError, and halt on expected source section",
     stackLength: 1,
     topMethod:   "Ping>>#λvalidate@34@84:",
     line:        34},

     {suite:      "setting a source section promise resolution breakpoint whenResolvedOnError",
     breakpoint:  createSectionBreakpointData(PING_PONG_URI, 34, 18, 91, "PromiseResolutionBreakpoint", true),
     test:        "should accept promise resolution breakpoint on whenResolvedOnError, and halt on expected source section",
     stackLength: 1,
     topMethod:   "Thing>>#println",
     line:        71}
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
      const breakpoint = createSectionBreakpointData(PING_PONG_URI, 23, 14, 3,
        "MessageSenderBreakpoint", true);
      conn = new TestConnection();
      ctrl = new HandleStoppedAndGetStackTrace([breakpoint], conn, 4);
    });

    after(closeConnectionAfterSuite);

    it("should stop initially at breakpoint", onlyWithConnection(() => {
      return ctrl.stackP.then(msg => {
        expectStack(msg.stackFrames, 2, "Ping>>#ping", 23);
      });
    }));

    it("should single stepping", onlyWithConnection(() => {
      return new Promise((resolve, _reject) => {
        ctrl.stackP.then(_ => {
          conn.fullyConnected.then(_ => {
            conn.sendDebuggerAction("stepInto", ctrl.stoppedActivities[0]);
          });

          const p = ctrl.getStackP(1).then(msgAfterStep => {
            expectStack(msgAfterStep.stackFrames, 2, "Ping>>#ping", 24);
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
          expectStack(msgLineBP.stackFrames, 1, "Ping>>#ping", 22);
        })]);
    }));

    it("should be possible to disable a line breakpoint",
        onlyWithConnection(() => {
      return new Promise((resolve, _reject) => {
        ctrl.getStackP(2).then(_ => {
          conn.fullyConnected.then(_ => {
            const lbp23 = createLineBreakpointData(PING_PONG_URI, 23, true);
            conn.updateBreakpoint(lbp23);

            const lbp22 = createLineBreakpointData(PING_PONG_URI, 22, false);
            conn.updateBreakpoint(lbp22);
            conn.sendDebuggerAction("resume", ctrl.stoppedActivities[2]);

            const p = ctrl.getStackP(3).then(msgLineBP => {
              expectStack(msgLineBP.stackFrames, 1, "Ping>>#ping", 23);
            });
            resolve(p);
          });
        });
      });
    }));
  });
});
