package som.primitives;

import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;

import som.interpreter.nodes.nary.UnaryBasicOperation;
import tools.dym.Tags.OpArithmetic;


@GenerateNodeFactory
@Primitive(primitive = "doubleCos:", selector = "cos", receiverType = Double.class)
public abstract class CosPrim extends UnaryBasicOperation {
  @Override
  protected boolean isTaggedWithIgnoringEagerness(final Class<?> tag) {
    if (tag == OpArithmetic.class) { // TODO: is this good enough?
      return true;
    } else {
      return super.isTaggedWithIgnoringEagerness(tag);
    }
  }

  @Specialization
  public final double doCos(final double rcvr) {
    return Math.cos(rcvr);
  }
}
