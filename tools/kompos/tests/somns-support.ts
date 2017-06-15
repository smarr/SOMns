export type EntityNames = "process" | "channel" | "message" | "actor" | "turn"
  | "task" | "thread" | "lock" | "transaction";
export type ActivityNames = "process" | "actor" | "task" | "thread";

export enum EntityId {
  PROCESS = 1,
  CHANNEL = 2,
  MESSAGE = 3,
  ACTOR   = 4,
  PROMISE = 5,
  TURN    = 6,
  TASK    = 7,
  THREAD  = 8,
  LOCK    = 9,
  TRANSACTION = 10
}

export enum ActivityId {
  PROCESS = 1,
  ACTOR   = 4,
  TASK    = 6,
  THREAD  = 7
}

export namespace BreakpointType {
  export const MSG_SENDER   = "msgSenderBP";
  export const MSG_RECEIVER = "msgReceiverBP";

  export const ASYNC_MSG_BEFORE_EXEC = "asyncMsgBeforeExecBP";
  export const ASYNC_MSG_AFTER_EXEC  = "asyncMsgAfterExecBP";
  export const PROMISE_RESOLVER      = "promiseResolverBP";
  export const PROMISE_RESOLUTION    = "promiseResolutionBP";

  export const CHANNEL_BEFORE_SEND = "channelBeforeSendBP";
  export const CHANNEL_AFTER_RCV   = "channelAfterRcvBP";
  export const CHANNEL_BEFORE_RCV  = "channelBeforeRcvBP";
  export const CHANNEL_AFTER_SEND  = "channelAfterSendBP";

  export const ACTIVITY_CREATION    = "activityCreationBP";
  export const ACTIVITY_ON_EXEC     = "activityOnExecBP";
  export const ACTIVITY_BEFORE_JOIN = "activityBeforeJoinBP";
  export const ACTIVITY_AFTER_JOIN  = "activityAfterJoinBP";

  export const ATOMIC_BEFORE        = "atomicBeforeBP";
  export const ATOMIC_BEFORE_COMMIT = "atomicBeforeCommitBP";
  export const ATOMIC_AFTER_COMMIT  = "atomicAfterCommitBP";

  export const LOCK_BEFORE = "lockBeforeBP";
  export const LOCK_AFTER  = "lockAfterBP";

  export const UNLOCK_BEFORE = "unlockBeforeBP";
  export const UNLOCK_AFTER  = "unlockAfterBP";
}

export namespace SteppingType {
  export const STEP_INTO = "stepInto";
  export const STEP_OVER = "stepOver";
  export const RETURN    = "return";
  export const RESUME    = "resume";
  export const PAUSE     = "pause";
  export const STOP      = "stop";

  export const STEP_INTO_ACTIVITY   = "stepIntoActivity";
  export const RETURN_FROM_ACTIVITY = "returnFromActivity";

  export const STEP_TO_CHANNEL_RCVR   = "stepToChannelRcvr";
  export const STEP_TO_CHANNEL_SENDER = "stepToChannelSender";

  export const STEP_TO_NEXT_TX   = "stepToNextTx";
  export const STEP_TO_COMMIT    = "stepToCommit";
  export const STEP_AFTER_COMMIT = "stepAfterCommit";

  export const STEP_TO_MESSAGE_RECEIVER = "stepToMessageRcvr";
  export const STEP_TO_PROMISE_RESOLVER = "stepToPromiseResolver";
  export const STEP_TO_NEXT_TURN        = "stepToNextTurn";
  export const RETURN_FROM_TURN_TO_PROMISE_RESOLUTION = "returnFromTurnToPromiseResolution";
}
