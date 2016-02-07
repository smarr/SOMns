package som.primitives.arithmetic;

import java.math.BigInteger;

import som.interpreter.nodes.nary.BinaryBasicOperation;

import com.oracle.truffle.api.source.SourceSection;


public abstract class ArithmeticPrim extends BinaryBasicOperation {

  protected ArithmeticPrim(final SourceSection source) {
    super(source);
  }

  protected static final Number reduceToLongIfPossible(final BigInteger result) {
    if (result.bitLength() > Long.SIZE - 1) {
      return result;
    } else {
      return result.longValue();
    }
  }
}
