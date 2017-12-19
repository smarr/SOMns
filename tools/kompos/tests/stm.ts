import { expect } from "chai";
import { resolve } from "path";

import { BreakpointType as BT, SteppingType as ST } from "./somns-support";
import { TestConnection, TestController } from "./test-setup";
import { createSectionBreakpointData, createLineBreakpointData } from "../src/messages";
import { Test, expectStops } from "./stepping";

const STM_FILE = resolve("tests/stm.ns");
const STM_URI = "file:" + STM_FILE;

describe("Setting STM Breakpoints", () => {
  let conn: TestConnection;

  const closeConnectionAfterSuite = (done) => {
    conn.fullyConnected.then(_ => { conn.close(done); });
    conn.fullyConnected.catch(reason => done(reason));
  };

  const steppingTests: Test[] = [
    {
      title: "stepping to message receiver on same actor",
      test: STM_FILE,
      initialBreakpoints: [
        createLineBreakpointData(STM_URI, 39, true),
        createSectionBreakpointData(STM_URI, 11, 8, 74, BT.ATOMIC_BEFORE, true),
        createSectionBreakpointData(STM_URI, 11, 8, 74, BT.ATOMIC_BEFORE_COMMIT, true)],
      initialStop: {
        line: 39,
        methodName: "STM>>#main:",
        stackHeight: 5,
        activity: "main"
      },
      steps: [
        {
          type: ST.RESUME,
          activity: "main",
          stops: [{
            line: 11,
            methodName: "STM>>#doCount:",
            stackHeight: 6,
            activity: "main"
          },
          {
            line: 11,
            methodName: "STM>>#doCount:",
            stackHeight: 2,
            activity: "thread2"
          }]
        },
        {
          type: ST.RESUME,
          activity: "main",
          stops: [{
            line: 13,
            methodName: "STM>>#λdoCount@11@16",
            stackHeight: 7,
            activity: "main"
          }]
        },
        {
          type: ST.RESUME,
          activity: "thread2",
          stops: [{
            line: 13,
            methodName: "STM>>#λdoCount@11@16",
            stackHeight: 3,
            activity: "thread2"
          }]
        },
        {
          type: ST.STEP_OVER,
          activity: "main",
          stops: [{
            line: 17,
            methodName: "STM>>#doCount:",
            stackHeight: 6,
            activity: "main"
          }]
        },
        {
          type: ST.RESUME,
          activity: "thread2",
          stops: [{
            line: 13,
            methodName: "STM>>#λdoCount@11@16",
            stackHeight: 3,
            activity: "thread2"
          }]
        }
      ]
    }
  ];

  for (const suite of steppingTests) {
    if (suite.skip) {
      describe.skip(suite.title, () => {
        if (!suite.steps) { return; }
        suite.steps.forEach(step => {
          const desc = step.desc ? step.desc : `do ${step.type} on ${step.activity}.`;
          it.skip(desc, () => { });
        });
      });
      continue;
    }

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
