import { createSectionBreakpointData, createLineBreakpointData } from "../src/messages";
import { ACTOR_URI, ACTOR_FILE, ACTOR2_FILE, ACTOR2_URI } from "./test-setup";
import { BreakpointType as BT, SteppingType as ST } from "./somns-support";
import { Test, Stop, describeDebuggerTests } from "./stepping";

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

const MTFooLine: Stop = {
  line: 11,
  methodName: "MyActor>>#foo",
  stackHeight: 1,
  activity: "MyActor"
};

const steppingTests: Test[] = [
  {
    title: "stepping to message receiver on same actor",
    test: ACTOR2_FILE,
    testArg: "stepToMessageReceiverOnSameActor",
    initialBreakpoints: [
      createSectionBreakpointData(ACTOR2_URI, 14, 12, 3, BT.MSG_SENDER, true)],
    initialStop: {
      line: 14,
      methodName: "ActA>>#doSelfSend",
      stackHeight: 1,
      activity: "actA"
    },
    steps: [
      {
        type: ST.STEP_TO_MESSAGE_RECEIVER,
        activity: "actA",
        stops: [{
          // we do the step, which is remote and local, so, we stop locally first
          line: 15,
          methodName: "ActA>>#doSelfSend",
          stackHeight: 1,
          activity: "actA"
        }]
      },
      {
        type: ST.RESUME,
        activity: "actA",
        stops: [{
          // after resuming, we arrive at the actual step target
          line: 19,
          methodName: "ActA>>#doSelfSend2",
          stackHeight: 1,
          activity: "actA"
        }]
      }
    ]
  },
  {
    title: "stepping to message receiver on other actor",
    test: ACTOR2_FILE,
    testArg: "stepToMessageReceiverOnOtherActor",
    initialBreakpoints: [
      createSectionBreakpointData(ACTOR2_URI, 33, 12, 3, BT.MSG_SENDER, true)],
    initialStop: {
      line: 33,
      methodName: "ActB>>#doSendToA",
      stackHeight: 1,
      activity: "actB"
    },
    steps: [
      {
        type: ST.STEP_TO_MESSAGE_RECEIVER,
        activity: "actB",
        stops: [{
          // we do the step, which is remote and local, so, we stop locally first
          line: 34,
          methodName: "ActB>>#doSendToA",
          stackHeight: 1,
          activity: "actB"
        },
        {
          // and then, in parallel, we stop in the remote actor
          line: 25,
          methodName: "ActA>>#finish",
          stackHeight: 1,
          activity: "actA"
        }]
      },
      {
        type: ST.RESUME,
        activity: "actA"
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
    test: ACTOR2_FILE,
    testArg: "returnFromTurnToPromiseResolutionForSelfSend",
    initialBreakpoints: [createLineBreakpointData(ACTOR2_URI, 49, true)],
    initialStop: {
      line: 49,
      methodName: "ActC>>#msg",
      stackHeight: 1,
      activity: "ActC"
    },
    steps: [
      {
        type: ST.RETURN_FROM_TURN_TO_PROMISE_RESOLUTION,
        activity: "ActC",
        stops: [{
          line: 44,
          methodName: "ActC>>#λdoSelfSend@43@23:",
          stackHeight: 1,
          activity: "ActC"
        }]
      },
      {
        type: ST.RESUME,
        activity: "ActC"
      }
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
    title: "stepping to promise resolver on self send",
    test: ACTOR2_FILE,
    testArg: "stepToMessageReceiverOnSameActor",
    initialBreakpoints: [
      createSectionBreakpointData(ACTOR2_URI, 14, 12, 3, BT.MSG_SENDER, true)],
    initialStop: {
      line: 14,
      methodName: "ActA>>#doSelfSend",
      stackHeight: 1,
      activity: "ActA"
    },
    steps: [
      {
        type: ST.STEP_TO_PROMISE_RESOLVER,
        activity: "ActA",
        stops: [{ // first we step locally, the stop for promise resolver is only in a later turn
          line: 15,
          methodName: "ActA>>#doSelfSend",
          stackHeight: 1,
          activity: "ActA"
        }]
      },
      {
        type: ST.RESUME,
        activity: "ActA",
        stops: [{
          line: 19,
          methodName: "ActA>>#doSelfSend2",
          stackHeight: 1,
          activity: "ActA"
        }]
      }
    ]
  },
  {
    title: "stepping to promise resolution for explicit promise",
    test: ACTOR2_FILE,
    testArg: "stepToResolutionExplicitPromise",
    initialBreakpoints: [createLineBreakpointData(ACTOR2_URI, 53, true)],
    initialStop: {
      line: 53,
      methodName: "ActC>>#makePromise",
      stackHeight: 1,
      activity: "ActC"
    },
    steps: [
      {
        type: ST.STEP_OVER,
        activity: "ActC",
        stops: [{
          line: 54,
          methodName: "ActC>>#makePromise",
          stackHeight: 1,
          activity: "ActC"
        }]
      },
      {
        type: ST.STEP_TO_PROMISE_RESOLUTION,
        activity: "ActC",
        stops: [{
          line: 54,
          methodName: "ActC>>#makePromise",
          stackHeight: 1,
          activity: "ActC"
        }]
      },
      {
        type: ST.RESUME,
        activity: "ActC",
        stops: [{
          line: 56,
          methodName: "ActC>>#λmakePromise@55@32:",
          stackHeight: 1,
          activity: "ActC"
        }]
      }
    ]
  },
  {
    title: "stepping to promise resolution for whenResolved",
    test: ACTOR2_FILE,
    testArg: "stepToResolutionOfWhenResolved",
    initialBreakpoints: [createLineBreakpointData(ACTOR2_URI, 66, true)],
    initialStop: {
      line: 66,
      methodName: "ActC>>#whenResolved",
      activity: "ActC",
      stackHeight: 1
    },
    steps: [
      {
        type: ST.STEP_TO_PROMISE_RESOLUTION,
        activity: "ActC",
        stops: [{
          line: 66,
          methodName: "ActC>>#whenResolved",
          activity: "ActC",
          stackHeight: 1
        }]
      },
      {
        type: ST.RESUME,
        activity: "ActC",
        stops: [{
          line: 70,
          methodName: "ActC>>#λwhenResolved@69@24:",
          stackHeight: 1,
          activity: "ActC"
        }]
      }
    ]
  },
  {
    title: "stepping to promise resolution for whenResolvedOnError",
    test: ACTOR2_FILE,
    testArg: "stepToResolutionOfWhenResolvedError",
    initialBreakpoints: [createLineBreakpointData(ACTOR2_URI, 79, true)],
    initialStop: {
      line: 79,
      methodName: "ActC>>#whenResolvedError",
      activity: "ActC",
      stackHeight: 1
    },
    steps: [
      {
        type: ST.STEP_TO_PROMISE_RESOLUTION,
        activity: "ActC",
        stops: [{
          line: 79,
          methodName: "ActC>>#whenResolvedError",
          activity: "ActC",
          stackHeight: 1
        }]
      },
      {
        type: ST.RESUME,
        activity: "ActC",
        stops: [{
          line: 83,
          methodName: "ActC>>#λwhenResolvedError@82@24:",
          stackHeight: 1,
          activity: "ActC"
        }]
      }
    ]
  },
  {
    title: "stepping to promise resolution for onError",
    test: ACTOR2_FILE,
    testArg: "stepToResolutionOnError",
    initialBreakpoints: [createLineBreakpointData(ACTOR2_URI, 96, true)],
    initialStop: {
      line: 96,
      methodName: "ActC>>#onError",
      activity: "ActC",
      stackHeight: 1
    },
    steps: [
      {
        type: ST.STEP_TO_PROMISE_RESOLUTION,
        activity: "ActC",
        stops: [{
          line: 96,
          methodName: "ActC>>#onError",
          activity: "ActC",
          stackHeight: 1
        }]
      },
      {
        type: ST.RESUME,
        activity: "ActC",
        stops: [{
          line: 100,
          methodName: "ActC>>#λonError@99@24:",
          stackHeight: 1,
          activity: "ActC"
        }]
      }
    ]
  }
];

describeDebuggerTests("Actor Stepping", steppingTests);
