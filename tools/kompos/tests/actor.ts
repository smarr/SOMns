import { expect } from "chai";
import { createSectionBreakpointData, createLineBreakpointData } from "../src/messages";
import { PING_PONG_URI, TestConnection, ACTOR_URI, TestController, ACTOR_FILE, PING_PONG_FILE } from "./test-setup";
import { BreakpointType as BT, SteppingType as ST } from "./somns-support";
import { Test, Stop, expectStops } from "./stepping";

let conn: TestConnection;

const closeConnectionAfterSuite = (done) => {
  conn.fullyConnected.then(_ => { conn.close(done); });
  conn.fullyConnected.catch(reason => done(reason));
};

describe("Actor Stepping", () => {
  const MAPWhenResolved: Stop = {
    line: 21,
    methodName: "Actor>>#λmsgAndPromiseCallback@20@31:",
    stackHeight: 1,
    activity: "main"
  };

  const MTFooBody: Stop = {
    line: 9,
    methodName: "MyActor>>#foo",
    stackHeight: 1,
    activity: "MyActor"
  };

  const PingValidate: Stop = {
    line: 33,
    methodName: "Ping>>#validate:",
    stackHeight: 1,
    activity: "main"
  };

  const PingPing: Stop = {
    line: 23,
    methodName: "Ping>>#ping",
    stackHeight: 2,
    activity: "ping"
  };

  const MTFooLine: Stop = {
    line: 11,
    methodName: "MyActor>>#foo",
    stackHeight: 1,
    activity: "MyActor"
  };

  const steppingTests: Test[] = [
    {
      title: "stepping to message receiver",
      test: PING_PONG_FILE,
      initialBreakpoints: [
        createSectionBreakpointData(PING_PONG_URI, 26, 19, 3, BT.MSG_SENDER, true)],
      initialStop: {
        line: 26,
        methodName: "Ping>>#ping",
        stackHeight: 2,
        activity: "ping"
      },
      steps: [
        {
          type: ST.STEP_TO_MESSAGE_RECEIVER,
          activity: "ping",
          stops: [{
            line: 27,
            methodName: "Ping>>#ping",
            stackHeight: 2,
            activity: "ping"
          }]
        },
        {
          type: ST.RESUME,
          activity: "ping",
          stops: [{
            line: 33,
            methodName: "Ping>>#validate:",
            stackHeight: 1,
            activity: "ping"
          }]
        }
      ]
    },
    {
      title: "stepping to promise resolution",
      test: ACTOR_FILE,
      testArg: "msgAndPromiseCallback",
      initialBreakpoints: [
        createSectionBreakpointData(ACTOR_URI, 20, 8, 3, BT.MSG_SENDER, true)
      ],
      initialStop: {
        line: 20,
        methodName: "Actor>>#msgAndPromiseCallback:",
        stackHeight: 6,
        activity: "main"
      },
      steps: [
        {
          type: ST.STEP_TO_MESSAGE_RECEIVER,
          activity: "main",
          stops: [
            {
              line: 29,
              methodName: "Actor>>#msgAndPromiseCallback:",
              stackHeight: 6,
              activity: "main"
            },
            {
              line: 9,
              methodName: "MyActor>>#foo",
              stackHeight: 1,
              activity: "MyActor"
            }]
        },
        {
          type: ST.RESUME,
          activity: "main"
        },
        {
          type: ST.RETURN_FROM_TURN_TO_PROMISE_RESOLUTION,
          activity: "MyActor",
          stops: [MAPWhenResolved]
        },
        {
          type: ST.RESUME,
          activity: "main"
        }
      ]
    },
    {
      title: "returning from turn to promise resolution for self-send",
      test: PING_PONG_FILE,
      initialBreakpoints: [
        createSectionBreakpointData(PING_PONG_URI, 33, 19, 3, BT.MSG_SENDER, true)
      ],
      initialStop: PingValidate,
      steps: [
        {
          type: ST.RETURN_FROM_TURN_TO_PROMISE_RESOLUTION,
          activity: "main",
          stops: [PingValidate]
        },
        {
          type: ST.RESUME,
          activity: "main",
          stops: [{
            line: 27,
            methodName: "Ping>>#λping@27@31:",
            stackHeight: 1,
            activity: "main"
          }]
        },
        {
          type: ST.RESUME,
          activity: "main",
          stops: [PingValidate]
        },
        {
          type: ST.RESUME,
          activity: "main"
        },
      ]
    },
    {
      title: "breakpoint to promise resolution",
      test: ACTOR_FILE,
      testArg: "msgAndPromiseCallback",
      initialBreakpoints: [
        createSectionBreakpointData(ACTOR_URI, 20, 8, 3, BT.PROMISE_RESOLUTION, true)
      ],
      initialStop: MAPWhenResolved
    },
    {
      title: "step to next turn",
      test: ACTOR_FILE,
      testArg: "multipleTurns",
      initialBreakpoints: [
        createLineBreakpointData(ACTOR_URI, 11, true)
      ],
      initialStop: MTFooLine,
      steps: [
        {
          type: ST.STEP_TO_NEXT_TURN,
          activity: "MyActor",
          stops: [MTFooBody]
        },
        {
          type: ST.STEP_TO_NEXT_TURN,
          activity: "MyActor",
          stops: [MTFooLine]
        },
        {
          type: ST.STEP_TO_NEXT_TURN,
          activity: "MyActor",
          stops: [MTFooBody]
        },
        {
          type: ST.STEP_TO_NEXT_TURN,
          activity: "MyActor",
          stops: [MTFooLine]
        },
        {
          type: ST.RESUME,
          activity: "MyActor"
        }
      ]
    },
    {
      title: "stepping to next turn",
      test: PING_PONG_FILE,
      initialBreakpoints: [
        createSectionBreakpointData(PING_PONG_URI, 23, 14, 3, BT.MSG_SENDER, true)
      ],
      initialStop: PingPing,
      steps: [
        {
          type: ST.STEP_TO_NEXT_TURN,
          activity: "ping",
          stops: [{
            line: 56,
            methodName: "Ping>>#pong:",
            stackHeight: 1,
            activity: "ping"
          }]
        },
        {
          type: ST.RESUME,
          activity: "ping",
          stops: [{
            line: 23,
            methodName: "Ping>>#ping",
            stackHeight: 1,
            activity: "ping"
          }]
        }
      ]
    },
    {
      title: "stepping to promise resolution",
      test: PING_PONG_FILE,
      initialBreakpoints: [
        createSectionBreakpointData(PING_PONG_URI, 33, 19, 3, BT.MSG_SENDER, true)],
      initialStop: PingValidate,
      steps: [
        {
          type: ST.STEP_TO_PROMISE_RESOLUTION,
          activity: "main",
          stops: [{
            line: 34,
            methodName: "Ping>>#validate:",
            stackHeight: 1,
            activity: "main"
          }]
        },
        {
          type: ST.RESUME,
          activity: "main",
          stops: [PingValidate]
        }
      ]
    },
    {
      title: "stepping to promise resolver",
      test: PING_PONG_FILE,
      initialBreakpoints: [
        createSectionBreakpointData(PING_PONG_URI, 26, 19, 3, BT.MSG_SENDER, true)],
      initialStop: {
        line: 26,
        methodName: "Ping>>#ping",
        stackHeight: 2,
        activity: "main"
      },
      steps: [
        {
          type: ST.STEP_TO_PROMISE_RESOLVER,
          activity: "main",
          stops: [{
            line: 27,
            methodName: "Ping>>#ping",
            stackHeight: 2,
            activity: "main"
          }]
        },
        {
          type: ST.RESUME,
          activity: "main",
          stops: [PingValidate]
        }
      ]
    },
    {
      title: "stepping to promise resolution for explicit promise",
      test: PING_PONG_FILE,
      initialBreakpoints: [createLineBreakpointData(PING_PONG_URI, 92, true)],
      initialStop: {
        line: 92,
        methodName: "PingPong>>#benchmark",
        stackHeight: 6,
        activity: "main"
      },
      steps: [
        {
          type: ST.STEP_OVER,
          activity: "main",
          stops: [{
            line: 92,
            methodName: "PingPong>>#benchmark",
            stackHeight: 6,
            activity: "main"
          }]
        },
        {
          type: ST.STEP_TO_PROMISE_RESOLUTION,
          activity: "main",
          stops: [{
            line: 93,
            methodName: "PingPong>>#benchmark",
            stackHeight: 6,
            activity: "main"
          }]
        },
        {
          type: ST.RESUME,
          activity: "main",
          stops: [{
            line: 98,
            methodName: "PingPong>>#λbenchmark@97@44:",
            stackHeight: 1,
            activity: "main"
          }]
        }
      ]
    },
    {
      title: "stepping to promise resolution for whenResolved",
      test: PING_PONG_FILE,
      initialBreakpoints: [createLineBreakpointData(PING_PONG_URI, 27, true)],
      initialStop: {
        line: 27,
        methodName: "Ping>>#ping",
        activity: "ping",
        stackHeight: 2
      },
      steps: [
        {
          type: ST.STEP_OVER,
          activity: "ping",
          stops: [{
            line: 27,
            methodName: "Ping>>#ping",
            activity: "ping",
            stackHeight: 2
          }]
        },
        {
          type: ST.STEP_TO_PROMISE_RESOLUTION,
          activity: "ping",
          stops: [{
            line: 28,
            methodName: "Ping>>#ping",
            activity: "ping",
            stackHeight: 2
          }]
        },
        {
          type: ST.RESUME,
          activity: "ping",
          stops: [{
            line: 27,
            methodName: "Ping>>#ping",
            stackHeight: 1,
            activity: "ping"
          }]
        }
      ]
    },
    {
      title: "stepping to promise resolution for whenResolvedOnError",
      test: PING_PONG_FILE,
      initialBreakpoints: [createLineBreakpointData(PING_PONG_URI, 34, true)],
      initialStop: {
        line: 34,
        methodName: "Ping>>#validate:",
        stackHeight: 1,
        activity: "ping"
      },
      steps: [
        {
          type: ST.STEP_OVER,
          activity: "ping",
          stops: [{
            line: 34,
            methodName: "Ping>>#validate:",
            stackHeight: 1,
            activity: "ping"
          }]
        },
        {
          type: ST.STEP_TO_PROMISE_RESOLUTION,
          activity: "ping",
          stops: [{
            line: 35,
            methodName: "Ping>>#validate:",
            stackHeight: 1,
            activity: "ping"
          }]
        },
        {
          type: ST.RESUME,
          activity: "ping",
          stops: [{
            line: 34,
            methodName: "Ping>>#validate:",
            stackHeight: 1,
            activity: "ping"
          }]
        },
        {
          type: ST.RESUME,
          activity: "ping",
          stops: [{
            line: 34,
            methodName: "Ping>>#validate:",
            stackHeight: 1,
            activity: "ping"
          }]
        }
      ]
    },
    {
      title: "stepping to promise resolution for onError",
      test: PING_PONG_FILE,
      initialBreakpoints: [createLineBreakpointData(PING_PONG_URI, 78, true)],
      initialStop: {
        line: 78,
        methodName: "Pong>>#stop",
        stackHeight: 1,
        activity: "pong"
      },
      steps: [
        {
          type: ST.STEP_OVER,
          activity: "pong",
          stops: [{
            methodName: "Pong>>#stop",
            line: 78,
            stackHeight: 1,
            activity: "pong"
          }]
        },
        {
          type: ST.STEP_TO_PROMISE_RESOLUTION,
          activity: "pong",
          stops: [{
            methodName: "Pong>>#stop",
            line: 79,
            stackHeight: 1,
            activity: "pong"
          }]
        },
        {
          type: ST.RESUME,
          activity: "pong",
          stops: [{
            methodName: "Pong>>#λstop@78@27:",
            line: 78,
            stackHeight: 1,
            activity: "pong"
          }]
        },
        {
          type: ST.RESUME,
          activity: "pong",
          stops: [{
            line: 71,
            methodName: "Thing>>#println",
            stackHeight: 1,
            activity: "pong"
          }]
        }
      ]
    }
  ];

  for (const suite of steppingTests) {

    describe(suite.title, () => {
      let ctrl: TestController;

      before("Start SOMns and Connect", () => {
        const arg = suite.testArg ? [suite.testArg] : null;
        conn = new TestConnection(arg, false, suite.test);
        ctrl = new TestController(suite, conn, conn.fullyConnected);
      });

      after(closeConnectionAfterSuite);

      it("should stop initially at breakpoint", () => {
        return ctrl.stopsDoneForStep.then(stops => {
          expect(stops).has.lengthOf(1);
          expect(stops[0]).to.deep.equal(suite.initialStop);
        });
      });

      describe("should", () => {
        if (!suite.steps) { return; }

        suite.steps.forEach(step => {
          const expectedStops = step.stops ? step.stops.length : 0;
          const desc = step.desc ? step.desc : `do ${step.type} on ${step.activity} and stop ${expectedStops} times.`;
          it(desc, () => {
            const stopPs = ctrl.doNextStep(step.type, step.activity, step.stops);

            return stopPs.then(allStops => {
              expectStops(allStops, step.stops);
            });
          });
        });
      });
    });
  }
});
