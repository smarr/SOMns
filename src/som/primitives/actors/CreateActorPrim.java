package som.primitives.actors;

import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.source.SourceSection;

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
import tools.concurrency.ActorExecutionTrace;


@GenerateNodeFactory
@Primitive(primitive = "actors:createFromValue:", selector  = "createActorFromValue:",
           specializer = IsActorModule.class, requiresContext = true)
public abstract class CreateActorPrim extends BinaryComplexOperation {
  private final VM vm;

  @Child protected IsValue isValue;

  protected CreateActorPrim(final boolean eagWrap, final SourceSection source, final VM vm) {
    super(eagWrap, source);
    this.vm = vm;
    isValue = IsValueNodeGen.createSubNode();
  }

  @Specialization(guards = "isValue.executeEvaluated(argument)")
  public final SFarReference createActor(final Object receiver, final Object argument) {
    Actor actor = Actor.createActor(vm);
    SFarReference ref = new SFarReference(actor, argument);

    if (VmSettings.ACTOR_TRACING) {
      ActorExecutionTrace.actorCreation(ref, sourceSection);
    }
    return ref;
  }

  @Specialization(guards = "!isValue.executeEvaluated(argument)")
  public final Object throwNotAValueException(final Object receiver, final Object argument) {
    return KernelObj.signalException("signalNotAValueWith:", argument);
  }
}
