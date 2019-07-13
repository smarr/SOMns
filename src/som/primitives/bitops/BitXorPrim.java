package som.primitives.bitops;

import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;

import bd.primitives.Primitive;
import som.primitives.arithmetic.ArithmeticPrim;


@GenerateNodeFactory
@Primitive(selector = "bitXor:")
@Primitive(primitive = "int:bitXor:")
@Primitive(primitive = "double:bitXor:")
public abstract class BitXorPrim extends ArithmeticPrim {
  @Specialization
  public final long doLong(final long receiver, final long right) {
    return receiver ^ right;
  }

  @Specialization
  public final long doLong(final double receiver, final long right) {
    return ((long) receiver) ^ right;
  }
}
