package tools.debugger.entities;

import som.vm.VmSettings;
import tools.TraceData;

public enum ActivityType {
  PROCESS(EntityType.PROCESS, "&#10733;",  Marker.PROCESS_CREATION, Marker.PROCESS_COMPLETION),
  ACTOR(EntityType.ACTOR,     "&#128257;", Marker.ACTOR_CREATION),
  TASK(EntityType.TASK,       "&#8623;",   Marker.TASK_SPAWN),
  THREAD(EntityType.THREAD,   "&#11123;",  Marker.THREAD_SPAWN);

  private final EntityType type;
  private final String icon;

  private final byte creationMarker;
  private final byte completionMarker;

  ActivityType(final EntityType type, final String icon,
      final byte creationMarker, final byte completionMarker) {
    this.type = type;
    this.icon = icon;
    this.creationMarker   = creationMarker;
    this.completionMarker = completionMarker;
  }

  ActivityType(final EntityType type, final String icon,
      final byte creationMarker) {
    this(type, icon, creationMarker, (byte) 0);
  }

  public EntityType getType() { return type; }
  public String getName() { return type.name; }

  public byte getId() { return type.id; }
  public String getIcon() { return icon; }

  public byte getCreationMarker()   { return creationMarker; }
  public byte getCompletionMarker() { return completionMarker; }

  public int getCreationSize()   {
    return VmSettings.TRUFFLE_DEBUGGER_ENABLED ? (11 + TraceData.SOURCE_SECTION_SIZE) : 11;
  }
  public int getCompletionSize() { return 1; }
}
