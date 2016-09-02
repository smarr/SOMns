package som.primitives.actors;

import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.source.SourceSection;

import som.VmSettings;
import som.interpreter.actors.Actor;
import som.interpreter.actors.SFarReference;
import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.nary.BinaryComplexOperation;
import som.primitives.ObjectPrims.IsValue;
import som.primitives.ObjectPrimsFactory.IsValueFactory;
import som.primitives.Primitive;
import som.primitives.actors.CreateActorPrim.Splzr;
import som.vm.Primitives.Specializer;


@GenerateNodeFactory
@Primitive(primitive = "actors:createFromValue:",
           selector = "createActorFromValue:",
           specializer   = Splzr.class)
@NodeChild(value = "isValue", type = IsValue.class, executeWith = "receiver")
public abstract class CreateActorPrim extends BinaryComplexOperation {
  public static class Splzr extends Specializer {
    @Override
    public boolean matches(final Primitive prim, final Object receiver, ExpressionNode[] args) {
      return receiver == ActorClasses.ActorModule;
    }

    @Override
    public <T> T create(final NodeFactory<T> factory, final Object[] arguments,
        final ExpressionNode[] argNodes, final SourceSection section,
        final boolean eagerWrapper) {
      return factory.createNode(section, argNodes[0], argNodes[1],
          IsValueFactory.create(section, null));
    }
  }

  protected CreateActorPrim(final SourceSection source) { super(false, source); }

  @Specialization(guards = "isValue")
  public final SFarReference createActor(final Object nil, final Object value, final boolean isValue) {
    Actor actor = Actor.createActor();
    SFarReference ref = new SFarReference(actor, value);

    if (VmSettings.ACTOR_TRACING) {
      Actor.traceActorsExceptMainOne(ref);
    }
    return ref;
  }

  // TODO: add a proper error or something if it isn't a value...
}
