package tools.debugger.entities;

import tools.TraceData;


public enum DynamicScopeType {
  TURN(EntityType.ACT_MSG,            Marker.TURN_START,        Marker.TURN_END),
  MONITOR(EntityType.MONITOR,         Marker.MONITOR_ENTER,     Marker.MONITOR_EXIT),
  TRANSACTION(EntityType.TRANSACTION, Marker.TRANSACTION_START, Marker.TRANSACTION_END);

  private final EntityType type;
  private final byte startMarker;
  private final byte endMarker;

  DynamicScopeType(final EntityType type, final byte startMarker, final byte endMarker) {
    this.type = type;
    this.startMarker = startMarker;
    this.endMarker   = endMarker;
  }

  public byte getId()          { return type.id; }
  public byte getStartMarker() { return startMarker; }
  public byte getEndMarker()   { return endMarker;   }

  public int getStartSize() { return 9 + TraceData.SOURCE_SECTION_SIZE; }
  public int getEndSize()   { return 1; }
}
