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
}
