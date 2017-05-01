package tools.debugger.message;

import tools.concurrency.Tags;
import tools.debugger.entities.ActivityType;
import tools.debugger.entities.BreakpointType;
import tools.debugger.entities.EntityType;
import tools.debugger.entities.SteppingType;
import tools.debugger.message.Message.OutgoingMessage;


@SuppressWarnings("unused")
public final class InitializationResponse extends OutgoingMessage {
  private final ServerCapabilities capabilities;

  private InitializationResponse(final ServerCapabilities capabilities) {
    this.capabilities = capabilities;
  }

  private static final class Type {
    private final byte id;
    private final byte creation;
    private final byte completion;
    private final String label;

    private Type(final byte id, final String label, final byte creation, final byte completion) {
      this.id    = id;
      this.completion = completion;
      this.creation = creation;
      this.label = label;
    }
  }

  private static String[] tagsToStrings(final Class<? extends Tags>[] tags) {
    if (tags == null) {
      return null;
    }

    String[] result = new String[tags.length];

    for (int i = 0; i < tags.length; i += 1) {
      result[i] = tags[i].getSimpleName();
    }
    return result;
  }

  private static final class BreakpointData {
    private final String name;
    private final String label;
    private final String[] applicableTo;

    private BreakpointData(final BreakpointType bp) {
      this.name = bp.name;
      this.label = bp.label;
      this.applicableTo = tagsToStrings(bp.applicableTo);
    }
  }

  private static final class SteppingData {
    private final String name;
    private final String label;
    private final String group;
    private final String icon;
    private final String[] applicableTo;

    private SteppingData(final SteppingType type) {
      this.name = type.name;
      this.label = type.label;
      this.group = type.group.label;
      this.icon  = type.icon;
      this.applicableTo = tagsToStrings(type.applicableTo);
    }
  }

  private static final class ServerCapabilities {
    private final Type[] activityTypes;
    private final Type[] entityTypes;
    private final BreakpointData[] breakpointTypes;
    private final SteppingData[] steppingTypes;

    private ServerCapabilities(final EntityType[] supportedEntities,
        final ActivityType[] supportedActivities,
        final BreakpointType[] supportedBreakpoints,
        final SteppingType[] supportedSteps) {
      activityTypes   = new Type[supportedActivities.length];
      entityTypes     = new Type[supportedEntities.length];
      breakpointTypes = new BreakpointData[supportedBreakpoints.length];
      steppingTypes   = new SteppingData[supportedSteps.length];

      int i = 0;
      for (EntityType e : supportedEntities) {
        entityTypes[i] = new Type(e.id, e.name, e.creation, e.completion);
        i += 1;
      }

      i = 0;
      for (ActivityType e : supportedActivities) {
        activityTypes[i] = new Type(e.getId(), e.getName(), e.getCreation(), e.getCompletion());
        i += 1;
      }

      i = 0;
      for (BreakpointType e : supportedBreakpoints) {
        breakpointTypes[i] = new BreakpointData(e);
        i += 1;
      }

      i = 0;
      for (SteppingType e : supportedSteps) {
        steppingTypes[i] = new SteppingData(e);
        i += 1;
      }
    }
  }

  public static InitializationResponse create(final EntityType[] supportedEntities,
      final ActivityType[] supportedActivities, final BreakpointType[] supportedBreakpoints,
      final SteppingType[] supportedSteps) {
    return new InitializationResponse(new ServerCapabilities(supportedEntities,
        supportedActivities, supportedBreakpoints, supportedSteps));
  }
}
