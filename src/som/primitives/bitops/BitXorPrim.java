package som.primitives.bitops;

import som.interpreter.nodes.nary.BinaryExpressionNode;
import som.primitives.Primitive;

import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.source.SourceSection;


@GenerateNodeFactory
@Primitive("int:bitXor:")
public abstract class BitXorPrim extends BinaryExpressionNode {
  protected BitXorPrim(final SourceSection source) { super(source); }

  @Specialization
  public final long doLong(final long receiver, final long right) {
    return receiver ^ right;
  }
}
