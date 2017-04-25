package tools.debugger.message;

import tools.debugger.entities.ActivityType;
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

  private static final class ServerCapabilities {
    private final Type[] activityTypes;
    private final Type[] entityTypes;

    private ServerCapabilities(final EntityType[] supportedEntities,
        final ActivityType[] supportedActivities) {
      activityTypes = new Type[supportedActivities.length];
      entityTypes   = new Type[supportedEntities.length];

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
    }
  }

  public static InitializationResponse create(final EntityType[] supportedEntities,
      final ActivityType[] supportedActivities) {
    return new InitializationResponse(new ServerCapabilities(supportedEntities, supportedActivities));
  }
}
