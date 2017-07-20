import { createSectionBreakpointData, createLineBreakpointData } from "../src/messages";
import { PING_PONG_URI, HandleStoppedAndGetStackTrace, TestConnection, expectStack } from "./test-setup";
import { BreakpointType as BT, SteppingType as ST } from "./somns-support";

let conn: TestConnection;

const closeConnectionAfterSuite = (done) => {
  conn.fullyConnected.then(_ => { conn.close(done); });
  conn.fullyConnected.catch(reason => done(reason));
};

describe("Actor Stepping", () => {
  const steppingTests = {
    "stepping to message receiver":
    [{
      breakpoint: createSectionBreakpointData(PING_PONG_URI, 26, 19, 3, BT.MSG_SENDER, true),
      stopLine: 26,
      stopMethod: "Ping>>#ping",
      numOp: 4,
      length: 2
    },
    {
      test: "step to message receiver",
      type: ST.STEP_TO_MESSAGE_RECEIVER,
      length: 2,
      methodName: "Ping>>#ping",
      line: 27,
      stackIndex: 1
    },
    {
      test: "resume after step to message receiver",
      type: ST.RESUME,
      length: 1,
      methodName: "Ping>>#validate:",
      line: 33,
      stackIndex: 2
    }],

    "stepping to promise resolution":
    [{
      breakpoint: createSectionBreakpointData(PING_PONG_URI, 33, 19, 3, BT.MSG_SENDER, true),
      stopLine: 33,
      stopMethod: "Ping>>#validate:",
      numOp: 4,
      length: 1
    },
    {
      test: "step to promise resolution",
      type: ST.STEP_TO_PROMISE_RESOLUTION,
      length: 1,
      methodName: "Ping>>#validate:",
      line: 34,
      stackIndex: 1
    },
    {
      test: "resume after step to promise resolution",
      type: ST.RESUME,
      length: 1,
      methodName: "Ping>>#λvalidate@34@77:",
      line: 34,
      stackIndex: 2
    }],

    "returning from turn to promise resolution":
    [{
      breakpoint: createSectionBreakpointData(PING_PONG_URI, 33, 19, 3, BT.MSG_SENDER, true),
      stopLine: 33,
      stopMethod: "Ping>>#validate:",
      numOp: 4,
      length: 1
    },
    {
      test: "return from turn to promise resolution",
      type: ST.RETURN_FROM_TURN_TO_PROMISE_RESOLUTION,
      length: 1,
      methodName: "Ping>>#λping@27@31:",
      line: 27,
      stackIndex: 2
    },
    {
      test: "resume after returning from turn",
      type: ST.RESUME,
      length: 1,
      methodName: "Ping>>#validate:",
      line: 33,
      stackIndex: 1
    }],

    "stepping to next turn":
    [{
      breakpoint: createSectionBreakpointData(PING_PONG_URI, 23, 14, 3, BT.MSG_SENDER, true),
      stopLine: 23,
      stopMethod: "Ping>>#ping",
      numOp: 4,
      length: 2
    },
    {
      test: "step to next turn",
      type: ST.STEP_TO_NEXT_TURN,
      length: 1,
      methodName: "Ping>>#pong:",
      line: 56,
      stackIndex: 1
    },
    {
      test: "resume after stepping to next turn",
      type: ST.RESUME,
      length: 1,
      methodName: "Ping>>#ping",
      line: 23,
      stackIndex: 2
    }],

    "stepping to promise resolver":
    [{
      breakpoint: createSectionBreakpointData(PING_PONG_URI, 26, 19, 3, BT.MSG_SENDER, true),
      stopLine: 26,
      stopMethod: "Ping>>#ping",
      numOp: 4,
      length: 2
    },
    {
      test: "step to promise resolver",
      type: ST.STEP_TO_PROMISE_RESOLVER,
      length: 2,
      methodName: "Ping>>#ping",
      line: 27,
      stackIndex: 1
    },
    {
      test: "resume after step to promise resolver",
      type: ST.RESUME,
      length: 1,
      methodName: "Ping>>#validate:",
      line: 33,
      stackIndex: 2
    }],

    "stepping to promise resolution for explicit promise":
    [{
      breakpoint: createLineBreakpointData(PING_PONG_URI, 92, true),
      stopLine: 92,
      stopMethod: "PingPong>>#benchmark",
      numOp: 4,
      length: 6
    },
    {
      test: "step over",
      type: ST.STEP_OVER,
      length: 6,
      methodName: "PingPong>>#benchmark",
      line: 92,
      stackIndex: 1
    },
    {
      test: "step to promise resolution",
      type: ST.STEP_TO_PROMISE_RESOLUTION,
      length: 6,
      methodName: "PingPong>>#benchmark",
      line: 93,
      stackIndex: 2
    },
    {
      test: "resume after step to promise resolution",
      type: ST.RESUME,
      length: 1,
      methodName: "PingPong>>#λbenchmark@97@44:",
      line: 98,
      stackIndex: 3
    }],

    "stepping to promise resolution for whenResolved":
    [{
      breakpoint: createLineBreakpointData(PING_PONG_URI, 27, true),
      stopLine: 27,
      stopMethod: "Ping>>#ping",
      numOp: 5,
      length: 2
    },
    {
      test: "step over",
      type: ST.STEP_OVER,
      length: 2,
      methodName: "Ping>>#ping",
      line: 27,
      stackIndex: 1
    },
    {
      test: "should step to promise resolution",
      type: ST.STEP_TO_PROMISE_RESOLUTION,
      length: 2,
      methodName: "Ping>>#ping",
      line: 28,
      stackIndex: 2
    },
    {
      test: "should resume after step to promise resolution",
      type: ST.RESUME,
      length: 1,
      methodName: "Ping>>#ping",
      line: 27,
      stackIndex: 3
    },
    {
      test: "should resume again to get to resolution",
      type: ST.RESUME,
      length: 1,
      methodName: "Ping>>#ping",
      line: 27,
      stackIndex: 4
    }],

    "stepping to promise resolution for whenResolvedOnError":
    [{
      breakpoint: createLineBreakpointData(PING_PONG_URI, 34, true),
      stopLine: 34,
      stopMethod: "Ping>>#validate:",
      numOp: 5,
      length: 1
    },
    {
      test: "step over",
      type: ST.STEP_OVER,
      length: 1,
      methodName: "Ping>>#validate:",
      line: 34,
      stackIndex: 1
    },
    {
      test: "step to promise resolution",
      type: ST.STEP_TO_PROMISE_RESOLUTION,
      length: 1,
      methodName: "Ping>>#validate:",
      line: 35,
      stackIndex: 2
    },
    {
      test: "resume after step to promise resolution",
      type: ST.RESUME,
      length: 1,
      methodName: "Ping>>#validate:",
      line: 34,
      stackIndex: 3
    },
    {
      test: "resume again to get to resolution",
      type: ST.RESUME,
      length: 1,
      methodName: "Ping>>#validate:",
      line: 34,
      stackIndex: 4
    }],

    "stepping to promise resolution for onError":
    [{
      breakpoint: createLineBreakpointData(PING_PONG_URI, 78, true),
      stopLine: 78,
      stopMethod: "Pong>>#stop",
      numOp: 5,
      length: 1
    },
    {
      test: "step over",
      type: ST.STEP_OVER,
      length: 1,
      methodName: "Pong>>#stop",
      line: 78,
      stackIndex: 1
    },
    {
      test: "step to promise resolution",
      type: ST.STEP_TO_PROMISE_RESOLUTION,
      length: 1,
      methodName: "Pong>>#stop",
      line: 79,
      stackIndex: 2
    },
    {
      test: "resume after step to promise resolution",
      type: ST.RESUME,
      length: 1,
      methodName: "Pong>>#λstop@78@27:",
      line: 78,
      stackIndex: 3
    },
    {
      test: "resume again to get to resolution",
      type: ST.RESUME,
      length: 1,
      methodName: "Thing>>#println",
      line: 71,
      stackIndex: 4
    }]
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

      it("should stop initially at breakpoint", () => {
        return ctrl.stackPs[0].then(msg => {
          expectStack(msg.stackFrames, stopData.length, stopData.stopMethod, stopData.stopLine);
        });
      });

      describe("should", () => {
        suite.forEach((testDesc, index) => {
          if (index > 0) { // evaluate all stepping data except the first one that corresponds to the breakpoint
            it(testDesc.test, () => {
              ctrl.stackPs[testDesc.stackIndex - 1].then(_ => {
                conn.sendDebuggerAction(testDesc.type, ctrl.stoppedActivities[0]);
              });

              return new Promise((resolve, _reject) => {
                const p = ctrl.stackPs[testDesc.stackIndex].then(msgAfterStep => {
                  expectStack(msgAfterStep.stackFrames, testDesc.length, testDesc.methodName, testDesc.line);
                });
                resolve(p);
              });
            });
          }
        });
      });
    });
  }
});
