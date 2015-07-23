package som.primitives.actors;

import som.interpreter.actors.Actor;
import som.interpreter.actors.SFarReference;
import som.interpreter.nodes.nary.UnaryExpressionNode;
import som.primitives.Primitive;

import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;


@GenerateNodeFactory
@Primitive("actorsCreateFromValue:")
public abstract class CreateActorPrim extends UnaryExpressionNode {

  public static boolean isValue(final Object value) {
    // TODO: make sure this is a real SOMns Value, i.e. deeply immutable
    return true;
  }

  @Specialization(guards = "isValue(value)")
  public final SFarReference createActor(final Object value) {
    Actor actor = new Actor();
    SFarReference ref = new SFarReference(actor, value);
    return ref;
  }
}
