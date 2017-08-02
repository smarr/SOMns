package som.primitives.arithmetic;

import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;

import bd.primitives.Primitive;
import som.interpreter.nodes.nary.UnaryBasicOperation;
import tools.dym.Tags.OpArithmetic;


@GenerateNodeFactory
@Primitive(primitive = "doubleSin:", selector = "sin", receiverType = Double.class)
public abstract class SinPrim extends UnaryBasicOperation {
  @Override
  protected boolean isTaggedWithIgnoringEagerness(final Class<?> tag) {
    if (tag == OpArithmetic.class) { // TODO: is this good enough?
      return true;
    } else {
      return super.isTaggedWithIgnoringEagerness(tag);
    }
  }

  @Specialization
  public final double doSin(final double rcvr) {
    return Math.sin(rcvr);
  }
}
