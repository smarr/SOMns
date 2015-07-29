package som.primitives.actors;

import som.interpreter.actors.Actor;
import som.interpreter.actors.SFarReference;
import som.interpreter.nodes.nary.UnaryExpressionNode;
import som.primitives.ObjectPrims.IsValue;
import som.primitives.Primitive;

import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;


@GenerateNodeFactory
@Primitive("actorsCreateFromValue:")
@NodeChild(value = "isValue", type = IsValue.class, executeWith = "receiver")
public abstract class CreateActorPrim extends UnaryExpressionNode {

  @Specialization(guards = "isValue")
  public final SFarReference createActor(final Object value, final boolean isValue) {
    Actor actor = new Actor();
    SFarReference ref = new SFarReference(actor, value);
    return ref;
  }

  // TODO: add a proper error or something if it isn't a value...
}
