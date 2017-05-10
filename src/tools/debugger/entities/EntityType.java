package tools.debugger.entities;

import tools.TraceData;

public enum EntityType {
  PROCESS("process", 1, TraceData.PROCESS_CREATION, TraceData.PROCESS_COMPLETION),
  CHANNEL("channel", 2, TraceData.CHANNEL_CREATION, 4),
  CH_MSG("ch-msg",   3, TraceData.CHANNEL_MESSAGE_SEND, TraceData.CHANNEL_MESSAGE_RCV),
  ACTOR("actor",     4, TraceData.ACTOR_CREATION,   8),
  PROMISE("promise", 5, TraceData.PROMISE_CREATION, 10),
  ACT_MSG("act-msg", 6, TraceData.ACTOR_MSG_SEND, 12),
  TURN("turn",       7, TraceData.TURN_START, TraceData.TURN_END),
  TASK("task",       8, TraceData.TASK_SPAWN, TraceData.TASK_JOIN),
  THREAD("thread",   9, TraceData.THREAD_SPAWN, TraceData.THREAD_JOIN),
  LOCK("lock",       10, 19, 20),
  TRANSACTION("transaction", 11, TraceData.TRANSACTION_START, TraceData.TRANSACTION_END),
  MONITOR("monitor", 12, TraceData.MONITOR_START, TraceData.MONITOR_END),

  /** Is used for implementation-level threads, for instance in actor or
      fork/join pools. They are relevant to understand scheduling order etc,
      but they are not part of the language semantics */
  IMPL_THREAD("implThread",  13, TraceData.IMPL_THREAD, TraceData.IMPL_THREAD_CURRENT_ACTIVITY);

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

  public static byte[] getIds(final EntityType[] entities) {
    if (entities == null) { return null; }

    byte[] entityIds = new byte[entities.length];
    for (int i = 0; i < entities.length; i += 1) {
      entityIds[i] = entities[i].id;
    }
    return entityIds;
  }
}
