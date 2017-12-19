import { resolve } from "path";

import { BreakpointType as BT, SteppingType as ST } from "./somns-support";
import { createSectionBreakpointData, createLineBreakpointData } from "../src/messages";
import { Test, describeDebuggerTests } from "./stepping";

const STM_FILE = resolve("tests/stm.ns");
const STM_URI = "file:" + STM_FILE;

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

describeDebuggerTests("Setting STM Breakpoints", steppingTests);
