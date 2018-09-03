package som.primitives.arithmetic;

import java.math.BigInteger;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.instrumentation.Tag;

import bd.primitives.Primitive;
import som.interpreter.nodes.nary.UnaryBasicOperation;
import tools.dym.Tags.OpArithmetic;


@GenerateNodeFactory
@Primitive(primitive = "intSqrt:")
@Primitive(primitive = "doubleSqrt:")
@Primitive(selector = "sqrt", receiverType = {Long.class, BigInteger.class, Double.class})
public abstract class SqrtPrim extends UnaryBasicOperation {
  @Override
  protected boolean hasTagIgnoringEagerness(final Class<? extends Tag> tag) {
    if (tag == OpArithmetic.class) {
      return true;
    } else {
      return super.hasTagIgnoringEagerness(tag);
    }
  }

  @Specialization
  public final double doLong(final long receiver) {
    return Math.sqrt(receiver);
  }

  @Specialization
  @TruffleBoundary
  public final double doBigInteger(final BigInteger receiver) {
    return Math.sqrt(receiver.doubleValue());
  }

  @Specialization
  public final double doDouble(final double receiver) {
    return Math.sqrt(receiver);
  }
}
