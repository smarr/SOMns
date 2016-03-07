package som.primitives.bitops;

import som.primitives.Primitive;
import som.primitives.arithmetic.ArithmeticPrim;

import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.source.SourceSection;


@GenerateNodeFactory
@Primitive("int:bitXor:")
public abstract class BitXorPrim extends ArithmeticPrim {

  protected BitXorPrim(final SourceSection source) {
    super(source);
  }

  @Specialization
  public final long doLong(final long receiver, final long right) {
    return receiver ^ right;
  }
}
