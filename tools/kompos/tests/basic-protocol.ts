"use strict";

import { expect } from "chai";
import * as fs from "fs";
import {X_OK} from "constants";

import { SOM, PING_PONG_URI, ControllerWithInitialBreakpoints,
  TestConnection, HandleStoppedAndGetStackTrace,
  expectStack, expectSourceCoordinate } from "./test-setup";

import { SourceMessage, createLineBreakpointData,
  createSectionBreakpointData } from "../src/messages";
import { BreakpointType as BT, SteppingType as ST } from "./somns-support";

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
      sourceP = new Promise<SourceMessage>((resolve, reject) => {
        conn.fullyConnected.catch(reject);
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

  const breakpointTests = {
    "setting a line breakpoint":
    [{test:        "accept line breakpoint, and halt on expected line",
      breakpoint:  createLineBreakpointData(PING_PONG_URI, 92, true),
      stackLength: 6,
      topMethod:   "PingPong>>#benchmark",
      line:        92}],

    "setting a source section sender breakpoint":
    [{test:        "accept send breakpoint, and halt on expected source section",
      breakpoint:  createSectionBreakpointData(PING_PONG_URI, 23, 14, 3, BT.MSG_SENDER, true),
      stackLength: 2,
      topMethod:   "Ping>>#ping",
      line:        23}],

    "setting a source section asynchronous method before execution breakpoint":
    [{test:        "accept async method before execution breakpoint, and halt on expected source section",
      breakpoint:  createSectionBreakpointData(PING_PONG_URI, 22, 9, 206, BT.ASYNC_MSG_BEFORE_EXEC, true),
      stackLength: 1,
      topMethod:   "Ping>>#ping",
      line:        22}],

    "setting a source section asynchronous message after execution breakpoint":
    [{test:        "accept async message after execution breakpoint, and halt on expected source section",
      breakpoint:  createSectionBreakpointData(PING_PONG_URI, 69, 9, 88, BT.ASYNC_MSG_AFTER_EXEC, true),
      stackLength: 1,
      topMethod:   "Pong>>#ping:",
      line:        69}],

    "setting a source section receiver breakpoint":
    [{test:        "accept send breakpoint, and halt on expected source section",
      breakpoint:  createSectionBreakpointData(PING_PONG_URI, 23, 14, 3, BT.MSG_RECEIVER, true),
      stackLength: 1,
      topMethod:   "Pong>>#ping:",
      line:        69}],

    "setting a source section promise resolver breakpoint":
    [{test:        "for normal resolution, accept promise resolver breakpoint, and halt on expected source section",
      breakpoint:  createSectionBreakpointData(PING_PONG_URI, 95, 17, 3, BT.PROMISE_RESOLVER, true),
      stackLength: 1,
      topMethod:   "Ping>>#start",
      line:        16},
     {test:        "for null resolution, accept promise resolver breakpoint for null resolution, and halt on expected source section",
      breakpoint:  createSectionBreakpointData(PING_PONG_URI, 23, 14, 3, BT.PROMISE_RESOLVER, true),
      stackLength: 1,
      topMethod:   "Pong>>#ping:",
      line:        69},
     {test:        "for chained resolution, accept promise resolver breakpoint for chained resolution, and halt on expected source section",
      breakpoint:  createSectionBreakpointData(PING_PONG_URI, 26, 20, 3, BT.PROMISE_RESOLVER, true),
      stackLength: 1,
      topMethod:   "Ping>>#validate:",
      line:        33},
     {test:        "on unresolved explicit promise, accept promise resolver breakpoint on unresolved explicit promise, and halt on expected source section",
      breakpoint:  createSectionBreakpointData(PING_PONG_URI, 92, 30, 17, BT.PROMISE_RESOLVER, true),
      stackLength: 1,
      topMethod:   "Pong>>#stop",
      line:        80},
     {test:        "on resolved explicit promise, accept promise resolution breakpoint on resolved explicit promise, and halt on expected source section",
      breakpoint:  createSectionBreakpointData(PING_PONG_URI, 41, 22, 17, BT.PROMISE_RESOLUTION, true),
      stackLength: 1,
      topMethod:   "Thing>>#println",
      line:        71},
     {test:        "on resolved explicit promise, accept promise resolver breakpoint on resolved explicit promise, and halt on expected source section",
      breakpoint:  createSectionBreakpointData(PING_PONG_URI, 41, 22, 17, BT.PROMISE_RESOLVER, true),
      stackLength: 1,
      topMethod:   "Ping>>#validNumber:",
      line:        49},
     {test:        "on whenResolved, accept promise resolver breakpoint on whenResolved, and halt on expected source section",
      breakpoint:  createSectionBreakpointData(PING_PONG_URI, 27, 18, 32, BT.PROMISE_RESOLVER, true),
      stackLength: 1,
      topMethod:   "Ping>>#λping@27@32:",
      line:        27},
     {test:        "onError, accept promise resolver breakpoint onError, and halt on expected source section",
      breakpoint:  createSectionBreakpointData(PING_PONG_URI, 78, 19, 50, BT.PROMISE_RESOLVER, true),
      stackLength: 1,
      topMethod:   "Pong>>#λstop@78@28:",
      line:        78},
     {test:        "whenResolvedOnError, accept promise resolver breakpoint whenResolvedOnError, and halt on expected source section",
      breakpoint:  createSectionBreakpointData(PING_PONG_URI, 34, 18, 91, BT.PROMISE_RESOLVER, true),
      stackLength: 1,
      topMethod:   "Ping>>#λvalidate@34@78:",
      line:        34}],

    "setting a source section promise resolution breakpoint":
    [{test:        "for normal resolution, accept promise resolution breakpoint, and halt on expected source section",
      breakpoint:  createSectionBreakpointData(PING_PONG_URI, 95, 17, 3, BT.PROMISE_RESOLUTION, true),
      stackLength: 1,
      topMethod:   "Thing>>#println",
      line:        71},
     {test:        "for chained resolution, accept promise resolution breakpoint for chained resolution, and halt on expected source section",
      breakpoint:  createSectionBreakpointData(PING_PONG_URI, 26, 20, 3, BT.PROMISE_RESOLUTION, true),
      stackLength: 1,
      topMethod:   "Ping>>#λping@27@32:",
      line:        27},
     {test:        "on unresolved explicit promise, accept promise resolution breakpoint on unresolved explicit promise, and halt on expected source section",
      breakpoint:  createSectionBreakpointData(PING_PONG_URI, 92, 30, 17, BT.PROMISE_RESOLUTION, true),
      stackLength: 1,
      topMethod:   "PingPong>>#λbenchmark@97@44:",
      line:        98},
     {test:        "on whenResolved, accept promise resolution breakpoint on whenResolved, and halt on expected source section",
      breakpoint:  createSectionBreakpointData(PING_PONG_URI, 27, 18, 32, BT.PROMISE_RESOLUTION, true),
      stackLength: 1,
      topMethod:   "Thing>>#println",
      line:        71},
     {test:        "onError, accept promise resolution breakpoint onError, and halt on expected source section",
      breakpoint:  createSectionBreakpointData(PING_PONG_URI, 78, 19, 50, BT.PROMISE_RESOLUTION, true),
      stackLength: 1,
      topMethod:   "Thing>>#println",
      line:        71},
     {test:        "whenResolvedOnError, accept promise resolution breakpoint on whenResolvedOnError, and halt on expected source section",
      breakpoint:  createSectionBreakpointData(PING_PONG_URI, 34, 18, 91, BT.PROMISE_RESOLUTION, true),
      stackLength: 1,
      topMethod:   "Thing>>#println",
      line:        71}]
  };

  for (const suiteName in breakpointTests) {
    const suite = breakpointTests[suiteName];

    describe(suiteName, () => {
      suite.forEach(testDesc => {
        describe("should", () => {
          let ctrl: HandleStoppedAndGetStackTrace;

          before("Start SOMns and Connect", () => {
            conn = new TestConnection();
            ctrl = new HandleStoppedAndGetStackTrace([testDesc.breakpoint], conn, conn.fullyConnected);
          });

          after(closeConnectionAfterSuite);

          it(testDesc.test, onlyWithConnection(() => {
            return ctrl.stackPs[0].then(msg => {
              expectStack(msg.stackFrames, testDesc.stackLength, testDesc.topMethod, testDesc.line);
            });
          }));
        });
      });
    });
  }

  describe("single stepping", () => {
    // Capture suspended events
    let ctrl: HandleStoppedAndGetStackTrace;

    before("Start SOMns and Connect", () => {
      const breakpoint = createSectionBreakpointData(PING_PONG_URI, 23, 14, 3,
        BT.MSG_SENDER, true);
      conn = new TestConnection();
      ctrl = new HandleStoppedAndGetStackTrace([breakpoint], conn, conn.fullyConnected, 4);
    });

    after(closeConnectionAfterSuite);

    it("should stop initially at breakpoint", onlyWithConnection(() => {
      return ctrl.stackPs[0].then(msg => {
        expectStack(msg.stackFrames, 2, "Ping>>#ping", 23);
      });
    }));

    it("should single stepping", onlyWithConnection(() => {
      return new Promise((resolve, _reject) => {
        ctrl.stackPs[0].then(_ => {
          conn.fullyConnected.then(_ => {
            conn.sendDebuggerAction(ST.STEP_INTO, ctrl.stoppedActivities[0]);
          });

          const p = ctrl.stackPs[1].then(msgAfterStep => {
            expectStack(msgAfterStep.stackFrames, 2, "Ping>>#ping", 24);
          });
          resolve(p);
        });
      });
    }));

    it("should be possible to dynamically activate line breakpoints",
        onlyWithConnection(() => {
      return Promise.all([
        ctrl.stackPs[1].then(_ => {
          conn.fullyConnected.then(_ => {
            // set another breakpoint, after stepping, and with connection
            const lbp = createLineBreakpointData(PING_PONG_URI, 22, true);
            conn.updateBreakpoint(lbp);
            conn.sendDebuggerAction(ST.RESUME, ctrl.stoppedActivities[1]);
          });
        }),
        ctrl.stackPs[2].then(msgLineBP => {
          expectStack(msgLineBP.stackFrames, 1, "Ping>>#ping", 22);
        })]);
    }));

    it("should be possible to disable a line breakpoint",
        onlyWithConnection(() => {
      return new Promise((resolve, _reject) => {
        ctrl.stackPs[2].then(_ => {
          conn.fullyConnected.then(_ => {
            const lbp23 = createLineBreakpointData(PING_PONG_URI, 23, true);
            conn.updateBreakpoint(lbp23);

            const lbp22 = createLineBreakpointData(PING_PONG_URI, 22, false);
            conn.updateBreakpoint(lbp22);
            conn.sendDebuggerAction(ST.RESUME, ctrl.stoppedActivities[2]);

            const p = ctrl.stackPs[3].then(msgLineBP => {
              expectStack(msgLineBP.stackFrames, 1, "Ping>>#ping", 23);
            });
            resolve(p);
          });
        });
      });
    }));
  });

  const steppingTests = {
      "stepping to message receiver":
      [{breakpoint: createSectionBreakpointData(PING_PONG_URI, 26, 20, 3, BT.MSG_SENDER, true),
        stopLine:   26,
        stopMethod: "Ping>>#ping",
        numOp:      4,
        length:     2},
       {test: "step to message receiver",
        type:       ST.STEP_TO_MESSAGE_RECEIVER,
        length:     2,
        methodName: "Ping>>#ping",
        line:       27,
        stackIndex: 1},
       {test: "resume after step to message receiver",
        type:       ST.RESUME,
        length:     1,
        methodName: "Ping>>#validate:",
        line:        33,
        stackIndex:  2}],

      "stepping to promise resolution":
      [{breakpoint: createSectionBreakpointData(PING_PONG_URI, 33, 20, 3, BT.MSG_SENDER, true),
        stopLine:   33,
        stopMethod: "Ping>>#validate:",
        numOp:      4,
        length:     1},
       {test: "step to promise resolution",
        type:       ST.STEP_TO_PROMISE_RESOLUTION,
        length:     1,
        methodName: "Ping>>#validate:",
        line:       34,
        stackIndex: 1},
       {test: "resume after step to promise resolution",
        type:       ST.RESUME,
        length:     1,
        methodName: "Ping>>#λvalidate@34@78:",
        line:       34,
        stackIndex: 2}],

      "returning from turn to promise resolution":
      [{breakpoint: createSectionBreakpointData(PING_PONG_URI, 33, 20, 3, BT.MSG_SENDER, true),
        stopLine:   33,
        stopMethod: "Ping>>#validate:",
        numOp:      4,
        length:     1},
       {test: "return from turn to promise resolution",
        type:       ST.RETURN_FROM_TURN_TO_PROMISE_RESOLUTION,
        length:     1,
        methodName: "Ping>>#validate:",
        line:       34,
        stackIndex: 1},
       {test: "resume after returning from turn",
        type:       ST.RESUME,
        length:     1,
        methodName: "Ping>>#λping@27@32:",
        line:       27,
        stackIndex: 2}],

      "stepping to next turn":
      [{breakpoint: createSectionBreakpointData(PING_PONG_URI, 23, 14, 3, BT.MSG_SENDER, true),
        stopLine:   23,
        stopMethod: "Ping>>#ping",
        numOp:      4,
        length:     2},
       {test: "step to next turn",
        type:       ST.STEP_TO_NEXT_TURN,
        length:     2,
        methodName: "Ping>>#ping",
        line:       24,
        stackIndex: 1},
       {test: "resume after stepping to next turn",
        type:       ST.RESUME,
        length:     1,
        methodName: "Ping>>#validate:",
        line:       33,
        stackIndex: 2}],

      "stepping to promise resolver":
      [{breakpoint: createSectionBreakpointData(PING_PONG_URI, 26, 20, 3, BT.MSG_SENDER, true),
        stopLine:   26,
        stopMethod: "Ping>>#ping",
        numOp:      4,
        length:     2},
       {test: "step to promise resolver",
        type:       ST.STEP_TO_PROMISE_RESOLVER,
        length:     2,
        methodName: "Ping>>#ping",
        line:       27,
        stackIndex: 1},
       {test: "resume after step to promise resolver",
        type:       ST.RESUME,
        length:     1,
        methodName: "Ping>>#validate:",
        line:       33,
        stackIndex: 2}],

      "stepping to promise resolution for explicit promise":
      [{breakpoint: createLineBreakpointData(PING_PONG_URI, 92, true),
        stopLine:   92,
        stopMethod: "PingPong>>#benchmark",
        numOp:      4,
        length:     6},
       {test: "step over",
        type:       ST.STEP_OVER,
        length:     6,
        methodName: "PingPong>>#benchmark",
        line:       92,
        stackIndex: 1},
       {test: "step to promise resolution",
        type:       ST.STEP_TO_PROMISE_RESOLUTION,
        length:     6,
        methodName: "PingPong>>#benchmark",
        line:       93,
        stackIndex: 2},
       {test: "resume after step to promise resolution",
        type:       ST.RESUME,
        length:     1,
        methodName: "PingPong>>#λbenchmark@97@44:",
        line:       98,
        stackIndex: 3}],

      "stepping to promise resolution for whenResolved":
      [{breakpoint: createLineBreakpointData(PING_PONG_URI, 27, true),
        stopLine:   27,
        stopMethod: "Ping>>#ping",
        numOp:      5,
        length:     2},
       {test: "step over",
        type:       ST.STEP_OVER,
        length:     2,
        methodName: "Ping>>#ping",
        line:       27,
        stackIndex: 1},
       {test: "should step to promise resolution",
        type: ST.STEP_TO_PROMISE_RESOLUTION,
        length:     2,
        methodName: "Ping>>#ping",
        line:       28,
        stackIndex: 2},
       {test: "should resume after step to promise resolution",
        type:       ST.RESUME,
        length:     1,
        methodName: "Ping>>#λping@27@32:",
        line:       27,
        stackIndex: 3},
       {test: "should resume again to get to resolution",
        type:       ST.RESUME,
        length:     1,
        methodName: "Thing>>#println",
        line:       71,
        stackIndex: 4}],

      "stepping to promise resolution for whenResolvedOnError":
      [{breakpoint: createLineBreakpointData(PING_PONG_URI, 34, true),
        stopLine:   34,
        stopMethod: "Ping>>#validate:",
        numOp:      5,
        length:     1},
       {test: "step over",
        type:       ST.STEP_OVER,
        length:     1,
        methodName: "Ping>>#validate:",
        line:       34,
        stackIndex: 1},
       {test: "step to promise resolution",
        type: ST.STEP_TO_PROMISE_RESOLUTION,
        length:     1,
        methodName: "Ping>>#validate:",
        line:       35,
        stackIndex: 2},
       {test: "resume after step to promise resolution",
        type: ST.RESUME,
        length:     1,
        methodName: "Ping>>#λvalidate@34@78:",
        line:       34,
        stackIndex: 3},
       {test: "resume again to get to resolution",
        type:       ST.RESUME,
        length:     1,
        methodName: "Thing>>#println",
        line:       71,
        stackIndex: 4}],

      "stepping to promise resolution for onError":
      [{breakpoint: createLineBreakpointData(PING_PONG_URI, 78, true),
        stopLine:   78,
        stopMethod: "Pong>>#stop",
        numOp:      5,
        length:     1},
       {test: "step over",
        type:       ST.STEP_OVER,
        length:     1,
        methodName: "Pong>>#stop",
        line:       78,
        stackIndex: 1},
       {test: "step to promise resolution",
        type:       ST.STEP_TO_PROMISE_RESOLUTION,
        length:     1,
        methodName: "Pong>>#stop",
        line:       79,
        stackIndex: 2},
       {test:  "resume after step to promise resolution",
        type:       ST.RESUME,
        length:     1,
        methodName: "Pong>>#λstop@78@28:",
        line:       78,
        stackIndex: 3},
       {test: "resume again to get to resolution",
        type:       ST.RESUME,
        length:     1,
        methodName: "Thing>>#println",
        line:       71,
        stackIndex: 4}]
  };

  for (const suiteName in steppingTests) {
    const suite = steppingTests[suiteName];
    const stopData = suite[0];

    describe(suiteName, () => {
      let ctrl: HandleStoppedAndGetStackTrace;

      before("Start SOMns and Connect", () => {
        conn = new TestConnection();
        ctrl = new HandleStoppedAndGetStackTrace(
          [stopData.breakpoint], conn, conn.fullyConnected, stopData.numOp);
      });

      after(closeConnectionAfterSuite);

      it("should stop initially at breakpoint", onlyWithConnection(() => {
        return  ctrl.stackPs[0].then(msg => {
          expectStack(msg.stackFrames, stopData.length, stopData.stopMethod, stopData.stopLine);
        });
      }));

      suite.forEach((testDesc, index) => {
        describe("should", () => {
          if (index > 0) { // evaluate all stepping data except the first one that corresponds to the breakpoint
            it(testDesc.test, onlyWithConnection(() => {
              return new Promise((resolve, _reject) => {
                ctrl.stackPs[0].then(_ => {
                  conn.fullyConnected.then(_ => {
                    conn.sendDebuggerAction(testDesc.type, ctrl.stoppedActivities[0]);
                  });

                  const p =  ctrl.stackPs[testDesc.stackIndex].then(msgAfterStep => {
                    expectStack(msgAfterStep.stackFrames, testDesc.length, testDesc.methodName, testDesc.line);
                  });
                  resolve(p);
                });
              });
            }));
          }
        });
      });
    });
  }
});
