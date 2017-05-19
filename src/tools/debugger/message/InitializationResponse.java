package tools.debugger.message;

import tools.concurrency.Tags;
import tools.debugger.entities.ActivityType;
import tools.debugger.entities.BreakpointType;
import tools.debugger.entities.DynamicScopeType;
import tools.debugger.entities.EntityType;
import tools.debugger.entities.Implementation;
import tools.debugger.entities.PassiveEntityType;
import tools.debugger.entities.ReceiveOp;
import tools.debugger.entities.SendOp;
import tools.debugger.entities.SteppingType;
import tools.debugger.message.Message.OutgoingMessage;


@SuppressWarnings("unused")
public final class InitializationResponse extends OutgoingMessage {
  private final ServerCapabilities capabilities;

  private InitializationResponse(final ServerCapabilities capabilities) {
    this.capabilities = capabilities;
  }

  private static final class ImplementationData {
    private final byte marker;
    private final int size;

    ImplementationData(final byte marker, final int size) {
      this.marker = marker;
      this.size   = size;
    }
  }

  private static final class ParseData {
    private final Integer creationSize;
    private final Integer completionSize;

    ParseData(final int creationSize,
        final int completionSize) {
      this.creationSize   = creationSize == 0 ? null : creationSize;
      this.completionSize = completionSize == 0 ? null : completionSize;
    }
  }

  private static final class Definition {
    private final byte id;
    private final Byte creation;
    private final Byte completion;
    private final String label;
    private final String marker;

    private Definition(final byte id, final String label, final byte creation,
        final byte completion, final String marker) {
      this.id = id;
      this.creation   = creation   == 0 ? null : creation;
      this.completion = completion == 0 ? null : completion;
      this.label = label;
      this.marker = marker;
    }
  }

  private static final class ReceiveDef {
    private final byte marker;
    private final byte source;

    ReceiveDef(final byte marker, final EntityType source) {
      this.marker = marker;
      this.source = source.id;
    }
  }

  private static final class SendDef {
    private final byte marker;
    private final byte entity;
    private final byte target;

    SendDef(final byte marker, final EntityType entity, final EntityType target) {
      this.marker = marker;
      this.entity = entity.id;
      this.target = target.id;
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
    private final byte[] forActivities;
    private final byte[] inScope;

    private SteppingData(final SteppingType type) {
      this.name = type.name;
      this.label = type.label;
      this.group = type.group.label;
      this.icon  = type.icon;
      this.applicableTo = tagsToStrings(type.applicableTo);
      this.inScope = EntityType.getIds(type.inScope);

      if (type.forActivities != null) {
        this.forActivities = new byte[type.forActivities.length];

        int i = 0;
        for (ActivityType t : type.forActivities) {
          this.forActivities[i] = t.getId();
          i += 1;
        }
      } else {
        this.forActivities = null;
      }
    }
  }

  private static Definition[] getDefinitions(final ActivityType[] activities) {
    final Definition[] result = new Definition[activities.length];

    for (int i = 0; i < activities.length; i += 1) {
      ActivityType a = activities[i];
      result[i] = new Definition(a.getId(), a.getName(), a.getCreationMarker(),
          a.getCompletionMarker(), a.getIcon());
    }
    return result;
  }

  private static Definition[] getDefinitions(final PassiveEntityType[] entities) {
    final Definition[] result = new Definition[entities.length];

    for (int i = 0; i < entities.length; i += 1) {
      PassiveEntityType e = entities[i];
      result[i] = new Definition(e.getId(), e.getName(), e.getCreationMarker(),
          (byte) 0, null);
    }
    return result;
  }

  private static Definition[] getDefinitions(final DynamicScopeType[] scopes) {
    final Definition[] result = new Definition[scopes.length];

    for (int i = 0; i < scopes.length; i += 1) {
      DynamicScopeType e = scopes[i];
      result[i] = new Definition(e.getId(), e.getName(), e.getStartMarker(),
          e.getEndMarker(), null);
    }
    return result;
  }

  private static ImplementationData[] getDefinitions(final Implementation[] implData) {
    final ImplementationData[] result = new ImplementationData[implData.length];

    for (int i = 0; i < implData.length; i += 1) {
      Implementation d = implData[i];
      result[i] = new ImplementationData(d.getId(), d.getSize());
    }

    return result;
  }

  private static ReceiveDef[] getDefinitions(final ReceiveOp[] ops) {
    final ReceiveDef[] result = new ReceiveDef[ops.length];

    for (int i = 0; i < ops.length; i += 1) {
      result[i] = new ReceiveDef(ops[i].getId(), ops[i].getSource());
    }
    return result;
  }

  private static SendDef[] getDefinitions(final SendOp[] ops) {
    final SendDef[] result = new SendDef[ops.length];

    for (int i = 0; i < ops.length; i += 1) {
      result[i] = new SendDef(ops[i].getId(), ops[i].getEntity(), ops[i].getTarget());
    }
    return result;
  }

  private static final class ServerCapabilities {
    private final Definition[] activities;
    private final Definition[] passiveEntities;
    private final Definition[] dynamicScopes;
    private final SendDef[] sendOps;
    private final ReceiveDef[] receiveOps;
    private final ParseData activityParseData;
    private final ParseData passiveEntityParseData;
    private final ParseData dynamicScopeParseData;
    private final ParseData sendReceiveParseData;
    private final ImplementationData[] implementationData;

    private final BreakpointData[] breakpointTypes;
    private final SteppingData[] steppingTypes;

    private ServerCapabilities(final EntityType[] supportedEntities,
        final ActivityType[] supportedActivities,
        final PassiveEntityType[] supportedPassiveEntities,
        final DynamicScopeType[] supportedDynamicScopes,
        final SendOp[] supportedSendOps,
        final ReceiveOp[] supportedReceiveOps,
        final BreakpointType[] supportedBreakpoints,
        final SteppingType[] supportedSteps,
        final Implementation[] implData) {
      activities      = getDefinitions(supportedActivities);
      passiveEntities = getDefinitions(supportedPassiveEntities);
      dynamicScopes   = getDefinitions(supportedDynamicScopes);
      sendOps         = getDefinitions(supportedSendOps);
      receiveOps      = getDefinitions(supportedReceiveOps);

      breakpointTypes = new BreakpointData[supportedBreakpoints.length];
      steppingTypes   = new SteppingData[supportedSteps.length];

      activityParseData = new ParseData(supportedActivities[0].getCreationSize(),
          supportedActivities[0].getCompletionSize());
      passiveEntityParseData = new ParseData(
          supportedPassiveEntities[0].getCreationSize(), 0);
      dynamicScopeParseData = new ParseData(
          supportedDynamicScopes[0].getStartSize(),
          supportedDynamicScopes[0].getEndSize());
      sendReceiveParseData = new ParseData(
          supportedSendOps[0].getSize(),
          supportedReceiveOps[0].getSize());

      implementationData = getDefinitions(implData);

      int i = 0;
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
      final ActivityType[] supportedActivities,
      final PassiveEntityType[] supportedPassiveEntities,
      final DynamicScopeType[] supportedDynamicScopes,
      final SendOp[] supportedSendOps,
      final ReceiveOp[] supportedReceiveOps,
      final BreakpointType[] supportedBreakpoints,
      final SteppingType[] supportedSteps, final Implementation[] implData) {
    return new InitializationResponse(new ServerCapabilities(supportedEntities,
        supportedActivities, supportedPassiveEntities, supportedDynamicScopes,
        supportedSendOps, supportedReceiveOps, supportedBreakpoints,
        supportedSteps, implData));
  }
}
