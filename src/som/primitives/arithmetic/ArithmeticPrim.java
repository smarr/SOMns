package som.primitives.arithmetic;

import java.math.BigInteger;

import com.oracle.truffle.api.source.SourceSection;

import som.interpreter.nodes.nary.BinaryBasicOperation;
import tools.dym.Tags.OpArithmetic;


public abstract class ArithmeticPrim extends BinaryBasicOperation {
  protected ArithmeticPrim(final boolean eagWrap, final SourceSection source) { super(eagWrap, source); }

  @Override
  protected boolean isTaggedWith(final Class<?> tag) {
    if (tag == OpArithmetic.class) {
      return true;
    } else {
      return super.isTaggedWith(tag);
    }
  }

  protected static final Number reduceToLongIfPossible(final BigInteger result) {
    if (result.bitLength() > Long.SIZE - 1) {
      return result;
    } else {
      return result.longValue();
    }
  }
}
