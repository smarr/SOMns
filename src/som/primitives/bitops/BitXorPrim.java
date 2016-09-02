package som.primitives.bitops;

import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.source.SourceSection;

import som.primitives.Primitive;
import som.primitives.arithmetic.ArithmeticPrim;


@GenerateNodeFactory
@Primitive(primitive = "int:bitXor:", selector = "bitXor:")
public abstract class BitXorPrim extends ArithmeticPrim {
  protected BitXorPrim(final boolean eagWrap, final SourceSection source) { super(eagWrap, source); }
  protected BitXorPrim(final SourceSection source) { super(false, source); }

  @Specialization
  public final long doLong(final long receiver, final long right) {
    return receiver ^ right;
  }
}
