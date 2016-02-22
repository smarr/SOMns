package som.primitives.arithmetic;

import som.interpreter.nodes.nary.UnaryBasicOperation;
import som.primitives.Primitive;

import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.source.SourceSection;


@GenerateNodeFactory
@Primitive("doubleSin:")
public abstract class SinPrim extends UnaryBasicOperation {
  public SinPrim(final SourceSection source) { super(source); }

  @Specialization
  public final double doSin(final double rcvr) {
    return Math.sin(rcvr);
  }
}
