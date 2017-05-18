package tools.debugger.entities;

import som.vm.VmSettings;


public class TraceSemantics {

  // Define Marker Constants, are used by Trace Parser
  public static final byte PROCESS_CREATION   = 1;
  public static final byte PROCESS_COMPLETION = 2;
  public static final byte ACTOR_CREATION     = 3;
  public static final byte TASK_SPAWN         = 4;
  public static final byte THREAD_SPAWN       = 5;

  public static final byte TURN_START        =  6;
  public static final byte TURN_END          =  7;
  public static final byte MONITOR_ENTER     =  8;
  public static final byte MONITOR_EXIT      =  9;
  public static final byte TRANSACTION_START = 10;
  public static final byte TRANSACTION_END   = 11;

  public static final byte CHANNEL_CREATION = 12;
  public static final byte PROMISE_CREATION = 13;

  public static final byte ACTOR_MSG_SEND     = 14;
  public static final byte CHANNEL_MSG_SEND   = 15;
  public static final byte PROMISE_RESOLUTION = 16;

  public static final byte CHANNEL_MSG_RCV = 17;
  public static final byte TASK_JOIN       = 18;
  public static final byte THREAD_JOIN     = 19;

  public static final byte IMPL_THREAD = 20;
  public static final byte IMPL_THREAD_CURRENT_ACTIVITY = 21;

  public enum ActivityDef {
    PROCESS(EntityType.PROCESS, PROCESS_CREATION, PROCESS_COMPLETION),
    ACTOR(EntityType.ACTOR,     ACTOR_CREATION),
    TASK(EntityType.TASK,       TASK_SPAWN),
    THREAD(EntityType.THREAD,   THREAD_SPAWN);

    private final EntityType type;
    private final byte creationMarker;
    private final byte completionMarker;

    ActivityDef(final EntityType type, final byte creationMarker) {
      this(type, creationMarker, (byte) 0);
    }

    ActivityDef(final EntityType type, final byte creationMarker,
        final byte completionMarker) {
      this.type = type;
      this.creationMarker   = creationMarker;
      this.completionMarker = completionMarker;
    }

    public byte getId()               { return type.id; }
    public byte getCreationMarker()   { return creationMarker; }
    public byte getCompletionMarker() { return completionMarker; }

    public int getCreationSize()   { return 11 + SOURCE_SECTION_SIZE; }
    public int getCompletionSize() { return 1; }
  }

  public enum DynamicScope {
    TURN(EntityType.ACT_MSG,            TURN_START,        TURN_END),
    MONITOR(EntityType.MONITOR,         MONITOR_ENTER,     MONITOR_EXIT),
    TRANSACTION(EntityType.TRANSACTION, TRANSACTION_START, TRANSACTION_END);

    private final EntityType type;
    private final byte startMarker;
    private final byte endMarker;

    DynamicScope(final EntityType type, final byte startMarker, final byte endMarker) {
      this.type = type;
      this.startMarker = startMarker;
      this.endMarker   = endMarker;
    }

    public byte getId()          { return type.id; }
    public byte getStartMarker() { return startMarker; }
    public byte getEndMarker()   { return endMarker;   }

    public int getStartSize() { return 9 + SOURCE_SECTION_SIZE; }
    public int getEndSize()   { return 1; }
  }

  public enum PassiveEntity {
    CHANNEL(EntityType.CHANNEL, CHANNEL_CREATION),
    CHANNEL_MSG(EntityType.CH_MSG),
    ACTOR_MSG(EntityType.ACT_MSG),
    PROMISE(EntityType.PROMISE, PROMISE_CREATION);

    private final EntityType type;
    private final byte creationMarker;

    PassiveEntity(final EntityType type) {
      this(type, 0);
    }

    PassiveEntity(final EntityType type, final int creationMarker) {
      this.type = type;
      this.creationMarker = (byte) creationMarker;
    }

    public byte getId()          { return type.id; }
    public byte getCreationMarker() { return creationMarker; }
    public int getCreationSize() { return 9 + SOURCE_SECTION_SIZE; }
  }

  public enum SendOp {
    ACTOR_MSG(ACTOR_MSG_SEND,      EntityType.ACT_MSG, EntityType.ACTOR),
    CHANNEL_SEND(CHANNEL_MSG_SEND, EntityType.CH_MSG,  EntityType.CHANNEL),
    PROMISE_RESOLUTION(TraceSemantics.PROMISE_RESOLUTION, EntityType.PROMISE, EntityType.PROMISE);

    private final byte id;
    private final EntityType entity;
    private final EntityType target;

    SendOp(final byte id, final EntityType entity, final EntityType target) {
      this.id = id;
      this.entity = entity;
      this.target = target;
    }

    public byte getId() { return id; }
    public EntityType getEntity() { return entity; }
    public EntityType getTarget() { return target; }
    public int getSize() { return 17; }
  }

  public enum ReceiveOp {
    CHANNEL_RCV(CHANNEL_MSG_RCV, EntityType.CHANNEL),
    TASK_JOIN(TraceSemantics.TASK_JOIN,     EntityType.TASK),
    THREAD_JOIN(TraceSemantics.THREAD_JOIN, EntityType.THREAD);

    private final byte id;
    private final EntityType source;

    ReceiveOp(final byte id, final EntityType source) {
      this.id = id;
      this.source = source;
    }

    public byte getId() { return id; }
    public EntityType getSource() { return source; }
    public int getSize() { return 9; }
  }

  public enum Implementation {
    IMPL_THREAD(TraceSemantics.IMPL_THREAD, 9),
    IMPL_CURRENT_ACTIVITY(TraceSemantics.IMPL_THREAD_CURRENT_ACTIVITY, 13);

    private final byte id;
    private final int  size;

    Implementation(final byte id, final int size) {
      this.id = id;
      this.size = size;
    }

    public byte getId()  { return id; }
    public int getSize() { return size; }
  }

  public static final int SOURCE_SECTION_SIZE = VmSettings.TRUFFLE_DEBUGGER_ENABLED ? 8 : 0;
}
