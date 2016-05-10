package som.primitives;

import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.source.SourceSection;

import som.interpreter.nodes.nary.UnaryBasicOperation;

@GenerateNodeFactory
@Primitive("doubleCos:")
public abstract class CosPrim extends UnaryBasicOperation {
  public CosPrim(final boolean eagWrap, final SourceSection source) { super(eagWrap, source); }
  public CosPrim(final SourceSection source) { super(false, source); }

  @Specialization
  public final double doCos(final double rcvr) {
    return Math.cos(rcvr);
  }
}
