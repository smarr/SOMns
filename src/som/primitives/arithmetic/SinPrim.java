package som.primitives.arithmetic;

import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.source.SourceSection;

import som.interpreter.nodes.nary.UnaryBasicOperation;
import som.primitives.Primitive;
import tools.dym.Tags.OpArithmetic;


@GenerateNodeFactory
@Primitive("doubleSin:")
public abstract class SinPrim extends UnaryBasicOperation {
  public SinPrim(final boolean eagWrap, final SourceSection source) { super(eagWrap, source); }
  public SinPrim(final SourceSection source) { super(false, source); }

  @Override
  protected boolean isTaggedWithIgnoringEagerness(final Class<?> tag) {
    if (tag == OpArithmetic.class) { // TODO: is this good enough?
      return true;
    } else {
      return super.isTaggedWithIgnoringEagerness(tag);
    }
  }

  @Specialization
  public final double doSin(final double rcvr) {
    return Math.sin(rcvr);
  }
}
