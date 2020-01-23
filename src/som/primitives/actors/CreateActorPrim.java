package som.primitives.actors;

import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.Tag;

import bd.primitives.Primitive;
import som.VM;
import som.interpreter.actors.Actor;
import som.interpreter.actors.SFarReference;
import som.interpreter.nodes.ExceptionSignalingNode;
import som.interpreter.nodes.nary.BinaryComplexOperation.BinarySystemOperation;
import som.primitives.ObjectPrims.IsValue;
import som.primitives.ObjectPrimsFactory.IsValueFactory.IsValueNodeGen;
import som.primitives.actors.PromisePrims.IsActorModule;
import som.vm.VmSettings;
import som.vmobjects.SClass;
import tools.concurrency.KomposTrace;
import tools.concurrency.Tags.ExpressionBreakpoint;
import tools.concurrency.TracingActors.TracingActor;
import tools.debugger.entities.ActivityType;
import tools.dym.Tags.CreateActor;
import tools.replay.TraceRecord;
import tools.replay.nodes.RecordEventNodes.RecordOneEvent;


@GenerateNodeFactory
@Primitive(primitive = "actors:createFromValue:", selector = "createActorFromValue:",
    specializer = IsActorModule.class)
public abstract class CreateActorPrim extends BinarySystemOperation {
  @Child protected IsValue                isValue = IsValueNodeGen.createSubNode();
  @Child protected ExceptionSignalingNode notAValue;
  @Child protected RecordOneEvent         trace;

  @Child protected TraceActorCreationNode trace = TraceActorCreationNode.create();

  @Override
  public final CreateActorPrim initialize(final VM vm) {
    super.initialize(vm);
    if (VmSettings.ACTOR_TRACING) {
      trace = insert(new RecordOneEvent(TraceRecord.ACTIVITY_CREATION));
    }
    notAValue = insert(ExceptionSignalingNode.createNotAValueNode(sourceSection));
    return this;
  }

  @Specialization(guards = "isValue.executeBoolean(frame, argument)")
  public final SFarReference createActor(final VirtualFrame frame, final Object receiver,
      final Object argument) {
    Actor actor = Actor.createActor(vm);
    SFarReference ref = new SFarReference(actor, argument);

    if (VmSettings.ACTOR_TRACING) {
      trace.record(((TracingActor) actor).getId());
    } else if (VmSettings.KOMPOS_TRACING) {
      assert argument instanceof SClass;
      final SClass actorClass = (SClass) argument;
      KomposTrace.activityCreation(ActivityType.ACTOR, actor.getId(),
          actorClass.getName(), sourceSection);
    }
    return ref;
  }

  @Fallback
  public final Object throwNotAValueException(final Object receiver, final Object argument) {
    return notAValue.signal(argument);
  }

  @Override
  protected boolean hasTagIgnoringEagerness(final Class<? extends Tag> tag) {
    if (tag == ExpressionBreakpoint.class) {
      return true;
    } else if (tag == CreateActor.class) {
      return true;
    }

    return super.hasTag(tag);
  }
}
