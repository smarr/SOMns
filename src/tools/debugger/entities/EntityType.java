package tools.debugger.entities;

public enum EntityType {
  PROCESS("process", 1),
  CHANNEL("channel", 2),
  CH_MSG("ch-msg",   3),
  ACTOR("actor",     4),
  PROMISE("promise", 5),
  ACT_MSG("act-msg", 6),
  TURN("turn",       7),
  TASK("task",       8),
  THREAD("thread",   9),
  LOCK("lock",       10),
  TRANSACTION("transaction", 11),
  MONITOR("monitor", 12),

  /** Is used for implementation-level threads, for instance in actor or
      fork/join pools. They are relevant to understand scheduling order etc,
      but they are not part of the language semantics */
  IMPL_THREAD("implThread",  13);

  // REM: if we wrap here over 32, make sure TraceData constants are updated

  public final String name;
  public final byte   id;

  EntityType(final String name, final int id) {
    this.name = name;
    this.id   = (byte) id;
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
