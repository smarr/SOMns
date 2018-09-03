package som.primitives.arithmetic;

import java.math.BigInteger;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.instrumentation.Tag;

import som.interpreter.nodes.nary.BinaryBasicOperation;
import tools.dym.Tags.OpArithmetic;


public abstract class ArithmeticPrim extends BinaryBasicOperation {
  @Override
  protected boolean hasTagIgnoringEagerness(final Class<? extends Tag> tag) {
    if (tag == OpArithmetic.class) {
      return true;
    } else {
      return super.hasTagIgnoringEagerness(tag);
    }
  }

  @TruffleBoundary
  protected static final Number reduceToLongIfPossible(final BigInteger result) {
    if (result.bitLength() > Long.SIZE - 1) {
      return result;
    } else {
      return result.longValue();
    }
  }
}
