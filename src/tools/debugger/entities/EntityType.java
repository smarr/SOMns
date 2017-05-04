package tools.debugger.entities;

import tools.TraceData;

public enum EntityType {
  PROCESS("process", 1, TraceData.PROCESS_CREATION, TraceData.PROCESS_COMPLETION),
  CHANNEL("channel", 2, TraceData.CHANNEL_CREATION, 4),
  MESSAGE("message", 3, 5, 6),
  ACTOR("actor",     4, TraceData.ACTOR_CREATION,   8),
  PROMISE("promise", 5, TraceData.PROMISE_CREATION, 10),
  TURN("turn",       6, 11, 12),
  TASK("task",       7, TraceData.TASK_SPAWN, TraceData.TASK_JOIN),
  THREAD("thread",   8, TraceData.THREAD_SPAWN, TraceData.THREAD_JOIN),
  LOCK("lock",       9, 17, 18),
  TRANSACTION("transaction", 10, 19, 20),

  /** Is used for implementation-level threads, for instance in actor or
      fork/join pools. They are relevant to understand scheduling order etc,
      but they are not part of the language semantics */
  IMPL_THREAD("implThread",  11, TraceData.IMPL_THREAD, 22);

  // REM: if we wrap here over 32, make sure TraceData constants are updated

  public final String name;
  public final byte   id;

  /** Marker used in trace to indicate creation of entity. */
  public final byte creation;

  /** Marker used in trace to indicate completion of entity. */
  public final byte completion;

  EntityType(final String name, final int id, final int creation, final int completion) {
    this.name = name;
    this.id   = (byte) id;
    this.creation   = (byte) creation;
    this.completion = (byte) completion;
  }
}
