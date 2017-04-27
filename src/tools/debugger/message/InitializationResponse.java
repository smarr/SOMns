package tools.debugger.message;

import tools.debugger.entities.ActivityType;
import tools.debugger.entities.BreakpointType;
import tools.debugger.entities.EntityType;
import tools.debugger.message.Message.OutgoingMessage;


@SuppressWarnings("unused")
public final class InitializationResponse extends OutgoingMessage {
  private final ServerCapabilities capabilities;

  private InitializationResponse(final ServerCapabilities capabilities) {
    this.capabilities = capabilities;
  }

  private static final class Type {
    private final byte id;
    private final String label;

    private Type(final byte id, final String label) {
      this.id    = id;
      this.label = label;
    }
  }

  private static final class BreakpointData {
    private final String name;
    private final String label;
    private final String[] applicableTo;

    private BreakpointData(final BreakpointType bp) {
      this.name = bp.name;
      this.label = bp.label;

      this.applicableTo = new String[bp.applicableTo.length];

      for (int i = 0; i < bp.applicableTo.length; i += 1) {
        applicableTo[i] = bp.applicableTo[i].getSimpleName();
      }
    }
  }


  private static final class ServerCapabilities {
    private final Type[] activityTypes;
    private final Type[] entityTypes;
    private final BreakpointData[] breakpointTypes;

    private ServerCapabilities(final EntityType[] supportedEntities,
        final ActivityType[] supportedActivities,
        final BreakpointType[] supportedBreakpoints) {
      activityTypes   = new Type[supportedActivities.length];
      entityTypes     = new Type[supportedEntities.length];
      breakpointTypes = new BreakpointData[supportedBreakpoints.length];

      int i = 0;
      for (EntityType e : supportedEntities) {
        entityTypes[i] = new Type(e.id, e.name);
        i += 1;
      }

      i = 0;
      for (ActivityType e : supportedActivities) {
        activityTypes[i] = new Type(e.getId(), e.getName());
        i += 1;
      }

      i = 0;
      for (BreakpointType e : supportedBreakpoints) {
        breakpointTypes[i] = new BreakpointData(e);
        i += 1;
      }
    }
  }

  public static InitializationResponse create(final EntityType[] supportedEntities,
      final ActivityType[] supportedActivities, final BreakpointType[] supportedBreakpoints) {
    return new InitializationResponse(new ServerCapabilities(supportedEntities,
        supportedActivities, supportedBreakpoints));
  }
}
