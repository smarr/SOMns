package tools.debugger.entities;


public enum EntityType {
  PROCESS("process", 1),
  CHANNEL("channel", 2),
  MESSAGE("message", 3),
  ACTOR("actor",     4),
  TURN("turn",       5),
  TASK("task",       6),
  THREAD("thread",   7),
  LOCK("lock",       8),
  TRANSACTION("transaction", 9);

  public final String name;
  public final byte   id;

  EntityType(final String name, final int id) {
    this.name = name;
    this.id   = (byte) id;
  }
}
