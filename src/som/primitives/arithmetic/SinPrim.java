package som.primitives.arithmetic;

import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.source.SourceSection;

import som.interpreter.nodes.nary.UnaryBasicOperation;
import som.primitives.Primitive;


@GenerateNodeFactory
@Primitive("doubleSin:")
public abstract class SinPrim extends UnaryBasicOperation {
  public SinPrim(final boolean eagWrap, final SourceSection source) { super(eagWrap, source); }
  public SinPrim(final SourceSection source) { super(false, source); }

  // TODO: assign some specific tag

  @Specialization
  public final double doSin(final double rcvr) {
    return Math.sin(rcvr);
  }
}
