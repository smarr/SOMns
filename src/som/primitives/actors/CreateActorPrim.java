package som.primitives.actors;

import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;

import som.VM;
import som.interpreter.actors.Actor;
import som.interpreter.actors.SFarReference;
import som.interpreter.nodes.nary.BinaryComplexOperation;
import som.primitives.ObjectPrims.IsValue;
import som.primitives.ObjectPrimsFactory.IsValueFactory.IsValueNodeGen;
import som.primitives.Primitive;
import som.primitives.actors.PromisePrims.IsActorModule;
import som.vm.VmSettings;
import som.vm.constants.KernelObj;
import som.vmobjects.SClass;
import tools.concurrency.ActorExecutionTrace;
import tools.concurrency.Tags.ExpressionBreakpoint;
import tools.debugger.entities.ActivityType;


@GenerateNodeFactory
@Primitive(primitive = "actors:createFromValue:", selector = "createActorFromValue:",
    specializer = IsActorModule.class, requiresContext = true)
public abstract class CreateActorPrim extends BinaryComplexOperation {
  private final VM vm;

  @Child protected IsValue isValue;

  protected CreateActorPrim(final VM vm) {
    this.vm = vm;
    isValue = IsValueNodeGen.createSubNode();
  }

  @Specialization(guards = "isValue.executeEvaluated(argument)")
  public final SFarReference createActor(final Object receiver, final Object argument) {
    Actor actor = Actor.createActor(vm);
    SFarReference ref = new SFarReference(actor, argument);

    if (VmSettings.ACTOR_TRACING) {
      assert argument instanceof SClass;
      final SClass actorClass = (SClass) argument;
      ActorExecutionTrace.activityCreation(ActivityType.ACTOR, actor.getId(),
          actorClass.getName(), sourceSection);
    }
    return ref;
  }

  @Specialization(guards = "!isValue.executeEvaluated(argument)")
  public final Object throwNotAValueException(final Object receiver, final Object argument) {
    return KernelObj.signalException("signalNotAValueWith:", argument);
  }

  @Override
  protected boolean isTaggedWithIgnoringEagerness(final Class<?> tag) {
    if (tag == ExpressionBreakpoint.class) {
      return true;
    }
    return super.isTaggedWith(tag);
  }
}
