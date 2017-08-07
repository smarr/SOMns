import { expect } from "chai";
import { resolve } from "path";

import { BreakpointType, SteppingType } from "./somns-support";
import { TestConnection, HandleStoppedAndGetStackTrace, expectStack } from "./test-setup";
import { createSectionBreakpointData, createLineBreakpointData, StackTraceResponse } from "../src/messages";

const FJ_FILE = resolve("tests/forkjoin.ns");
const FJ_URI = "file:" + FJ_FILE;

describe("Setting Fork/Join Breakpoints", () => {
  let conn: TestConnection;
  let ctrl: HandleStoppedAndGetStackTrace;

  const closeConnectionAfterSuite = (done) => {
    conn.fullyConnected.then(_ => { conn.close(done); });
    conn.fullyConnected.catch(reason => done(reason));
  };

  const beforeBp = createSectionBreakpointData(FJ_URI, 9, 17, 83,
    BreakpointType.ACTIVITY_CREATION, true);

  before("Start SOMns and Connect", () => {
    const lBp = createLineBreakpointData(FJ_URI, 22, true);

    conn = new TestConnection(null, null, FJ_FILE);
    ctrl = new HandleStoppedAndGetStackTrace(
      [lBp, beforeBp], conn, conn.fullyConnected, 5);
  });

  after(closeConnectionAfterSuite);

  let mainActId;
  it("should break before #cnt:", () => {
    return ctrl.stackPs[0].then(msg => {
      mainActId = msg.activityId;
      conn.sendDebuggerAction(SteppingType.RESUME, ctrl.stoppedActivities[mainActId]);

      return expectStack(msg.stackFrames, 5, "ForkJoin>>#main:", 22);
    });
  });

  it("should break before #spawn:", () => {
    return ctrl.stackPs[1].then(msg => {
      expect(mainActId).is.equal(msg.activityId);

      // disable the breakpoint
      beforeBp.enabled = false;
      conn.updateBreakpoint(beforeBp);

      conn.sendDebuggerAction(SteppingType.STEP_INTO_ACTIVITY, ctrl.stoppedActivities[mainActId]);
      return expectStack(msg.stackFrames, 6, "ForkJoin>>#cnt:", 9);
    });
  });

  it("should step into activity, and stop next line", () => {
    const main = { s: 6, n: "ForkJoin>>#cnt:", l: 13 };
    const child = { s: 1, n: "ForkJoin>>#λcnt@9@24", l: 10 };

    const handler = function(msg: StackTraceResponse) {
      if (msg.activityId === 0) {
        conn.sendDebuggerAction(SteppingType.RESUME, ctrl.stoppedActivities[msg.requestId]);
        return expectStack(msg.stackFrames, main.s, main.n, main.l);
      } else {
        conn.sendDebuggerAction(SteppingType.RETURN_FROM_ACTIVITY, ctrl.stoppedActivities[msg.requestId]);
        return expectStack(msg.stackFrames, child.s, child.n, child.l);
      }
    };

    const p1 = ctrl.stackPs[2].then(msg => {
      return handler(msg);
    });

    const p2 = ctrl.stackPs[3].then(msg => {
      return handler(msg);
    });

    return Promise.all([p1, p2]);
  });

  it("should eventually stop at the root join operation", () => {
    return ctrl.stackPs[4].then(msg => {
      expect(mainActId).is.equal(msg.activityId);
      return expectStack(msg.stackFrames, 6, "ForkJoin>>#cnt:", 13);
    });
  });
});

