export type EntityNames = "process" | "channel" | "message" | "actor" | "turn"
  | "task" | "thread" | "lock" | "transaction";
export type ActivityNames = "process" | "actor" | "task" | "thread";

export enum EntityId {
  PROCESS = 1,
  CHANNEL = 2,
  MESSAGE = 3,
  ACTOR   = 4,
  TURN    = 5,
  TASK    = 6,
  THREAD  = 7,
  LOCK    = 8,
  TRANSACTION = 9
}

export enum ActivityId {
  PROCESS = 1,
  ACTOR   = 4,
  TASK    = 6,
  THREAD  = 7
}
