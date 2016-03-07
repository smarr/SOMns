package som.primitives.arithmetic;

import java.math.BigInteger;

import som.compiler.Tags;
import som.interpreter.nodes.nary.BinaryBasicOperation;

import com.oracle.truffle.api.source.SourceSection;

import dym.Tagging;


public abstract class ArithmeticPrim extends BinaryBasicOperation {

  protected ArithmeticPrim(final SourceSection source) {
    super(Tagging.cloneAndAddTags(source, Tags.OP_ARITHMETIC));
  }

  protected static final Number reduceToLongIfPossible(final BigInteger result) {
    if (result.bitLength() > Long.SIZE - 1) {
      return result;
    } else {
      return result.longValue();
    }
  }
}
