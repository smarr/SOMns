import { expect } from "chai";
import { resolve } from "path";

import { BreakpointType, SteppingType } from "./somns-support";
import { TestConnection, HandleStoppedAndGetStackTrace, expectStack } from "./test-setup";
import { createSectionBreakpointData } from "../src/messages";

const STM_FILE = resolve("tests/stm.som");
const STM_URI  = "file:" + STM_FILE;

describe("Setting STM Breakpoints", () => {
  let conn: TestConnection;
  let ctrl: HandleStoppedAndGetStackTrace;

  const closeConnectionAfterSuite = (done) => {
    conn.fullyConnected.then(_ => { conn.close(done); });
    conn.fullyConnected.catch(reason => done(reason));
  };

  before("Start SOMns and Connect", () => {
    const beforeBp = createSectionBreakpointData(STM_URI, 11, 8, 75,
      BreakpointType.ATOMIC_BEFORE, true);
    const commitBp = createSectionBreakpointData(STM_URI, 11, 8, 75,
      BreakpointType.ATOMIC_BEFORE_COMMIT, true);
    conn = new TestConnection(null, null, STM_FILE);
    ctrl = new HandleStoppedAndGetStackTrace(
      [beforeBp, commitBp], conn, conn.fullyConnected, 6);
  });

  after(closeConnectionAfterSuite);

  const thread1 = [
    {s: 6, n: "STM>>#doCount:", l: 11},
    // line 13 is ok, because the bp uses source section of whole block
    {s: 7, n: "STM>>#λdoCount@12@8", l: 13},
    {s: 6, n: "STM>>#doCount:", l: 17},
  ];

  const thread2 = [
    {s: 2, n: "STM>>#doCount:", l: 11},
    {s: 3, n: "STM>>#λdoCount@12@8", l: 13},
    {s: 3, n: "STM>>#λdoCount@12@8", l: 13},
  ];

  let actId = 0;
  it("should break on #atomic, 1st time, and resume", () => {
    return ctrl.stackPs[0].then(msg => {
      actId = msg.activityId;
      conn.sendDebuggerAction(SteppingType.RESUME, ctrl.stoppedActivities[msg.requestId]);

      const exp = (msg.activityId === 0 ? thread1 : thread2)[0];
      return expectStack(msg.stackFrames, exp.s, exp.n, exp.l);
    });
  });

  it("should break on #atomic, 2nd time, and resume", () => {
    return ctrl.stackPs[1].then(msg => {
      expect(msg.activityId).not.equal(actId);
      conn.sendDebuggerAction(SteppingType.RESUME, ctrl.stoppedActivities[msg.requestId]);

      const exp = (msg.activityId === 0 ? thread1 : thread2)[0];
      return expectStack(msg.stackFrames, exp.s, exp.n, exp.l);
    });
  });

  let postponedAct = null;

  it("should stop before commit, 1st time, and single step", () => {
    return ctrl.stackPs[2].then(msg => {
      if (msg.activityId === 0) {
        conn.sendDebuggerAction(SteppingType.STEP_INTO, ctrl.stoppedActivities[msg.requestId]);
      } else {
        postponedAct = ctrl.stoppedActivities[msg.requestId];
      }

      const exp = (msg.activityId === 0 ? thread1 : thread2)[1];
      return expectStack(msg.stackFrames, exp.s, exp.n, exp.l);
    });
  });

  it("should stop before commit, 2nd time, and single step", () => {
    return ctrl.stackPs[3].then(msg => {
      if (msg.activityId === 0) {
        conn.sendDebuggerAction(SteppingType.STEP_INTO, ctrl.stoppedActivities[msg.requestId]);
      } else {
        postponedAct = ctrl.stoppedActivities[msg.requestId];
      }

      const exp = (msg.activityId === 0 ? thread1 : thread2)[1];
      return expectStack(msg.stackFrames, exp.s, exp.n, exp.l);
    });
  });

  it("1st thread should stop before next operation", () => {
    return ctrl.stackPs[4].then(msg => {
      console.assert(msg.activityId === 0);
      console.assert(postponedAct !== null);
      conn.sendDebuggerAction(SteppingType.STEP_INTO, postponedAct);

      const exp = thread1[2];
      return expectStack(msg.stackFrames, exp.s, exp.n, exp.l);
    });
  });

  it("2nd thread should retry transaction", () => {
    return ctrl.stackPs[5].then(msg => {
      const exp = (msg.activityId === 0 ? thread1 : thread2)[2];
      return expectStack(msg.stackFrames, exp.s, exp.n, exp.l);
    });
  });

});
