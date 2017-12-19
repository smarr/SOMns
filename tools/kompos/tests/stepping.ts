import { expect } from "chai";

import { BreakpointData } from "../src/messages";
import { TestConnection, TestController } from "./test-setup";

export interface Step {
  /** Type of the stepping operation to be performed. */
  type: string;

  /** Description, shown in the test. If not given, the type is used. */
  desc?: string;

  /**
   * Test specific handle for the activity.
   * Handles are matched to activity ids on first use.
   */
  activity: string;

  /** All execution stops that are a result of this step. */
  stops?: Stop[];
}

export interface Stop {
  /** Line at which execution stops. */
  line: number;

  /** Method in which execution stops. */
  methodName: string;

  /** Height of the stack when execution stops. */
  stackHeight: number;

  /**
   * Test specific handle for the activity.
   * Handles are matched to activity ids on first use.
   */
  activity: string;
}

export interface Test {
  title: string;

  /** Test file to be executed on SOMns. */
  test: string;

  /** Argument given to the test. */
  testArg?: string;

  initialBreakpoints?: BreakpointData[];

  initialStop: Stop;

  steps?: Step[];

  /** Mark test to be skipped. */
  skip?: boolean;
}

export function numberOfStops(test: Test) {
  let cnt = 0;

  console.assert(test.initialStop);
  cnt += 1;

  if (test.steps) {
    for (const step of test.steps) {
      if (step.stops) {
        for (const _ of step.stops) {
          cnt += 1;
        }
      }
    }
  }

  return cnt;
}

function removeMatching(actual: Stop[], match: Stop) {
  for (const aIdx in actual) {
    const a = actual[aIdx];

    // remove matching
    if (match.activity === a.activity && match.line === a.line &&
      match.methodName === a.methodName &&
      match.stackHeight === a.stackHeight) {
      delete actual[aIdx];
      return true;
    }
  }
  return false;
}

export function expectStops(actual?: Stop[], expected?: Stop[]) {
  if (expected === undefined || expected === null) {
    expect(actual).to.be.empty;
    return;
  }

  expect(actual).to.be.an("array");
  expect(expected).to.be.an("array");
  // expect(actual).to.have.lengthOf(expect.length);

  const expectedCopy = expected.slice();
  const copy = actual.slice();

  for (const e of expected) {
    const removed = removeMatching(copy, e);
    if (removed) {
      removeMatching(expectedCopy, e);
    }
  }

  const e = expectedCopy.filter(v => { return v !== undefined });
  const c = copy.filter(v => { return v !== undefined });
  expect(c).to.deep.equal(e);
  expect(c).to.be.empty;
}

export function describeDebuggerTests(title: string, steppingTests: Test[]) {
  describe(title, () => {
    let conn: TestConnection;

    const closeConnectionAfterSuite = (done) => {
      conn.fullyConnected.then(_ => { conn.close(done); });
      conn.fullyConnected.catch(reason => done(reason));
    };

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
}
