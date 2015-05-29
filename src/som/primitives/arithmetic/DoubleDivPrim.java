package som.primitives.arithmetic;

import java.math.BigInteger;

import som.primitives.Primitive;
import som.vm.NotYetImplementedException;
import som.vmobjects.SAbstractObject;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;


@GenerateNodeFactory
@Primitive({"int:divideDouble:", "double:divideDouble:"})
public abstract class DoubleDivPrim extends ArithmeticPrim {
  @Specialization
  public final double doDouble(final double left, final double right) {
    return left / right;
  }

  @Specialization
  public final double doLong(final long left, final long right) {
    return ((double) left) / right;
  }

  @Specialization
  public final double doDouble(final double left, final long right) {
    return doDouble(left, (double) right);
  }

  @Specialization
  public final SAbstractObject doLong(final long left, final BigInteger right) {
    CompilerAsserts.neverPartOfCompilation("DoubleDiv100");
    throw new NotYetImplementedException(); // TODO: need to implement the "/" case here directly... : return resendAsBigInteger("/", left, (SBigInteger) rightObj, frame.pack());
  }

  @Specialization
  public final SAbstractObject doLong(final long left, final double right) {
    CompilerAsserts.neverPartOfCompilation("DoubleDiv101");
    throw new NotYetImplementedException(); // TODO: need to implement the "/" case here directly... : return resendAsDouble("/", left, (SDouble) rightObj, frame.pack());
  }
}
