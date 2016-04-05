package som.primitives.arithmetic;

import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.source.SourceSection;

import som.interpreter.nodes.nary.UnaryExpressionNode;
import som.primitives.Primitive;


@GenerateNodeFactory
@Primitive("doubleExp:")
public abstract class ExpPrim extends UnaryExpressionNode {
  public ExpPrim(final boolean eagWrap, final SourceSection source) { super(eagWrap, source); }
  public ExpPrim(final SourceSection source) { super(false, source); }

  @Specialization
  public final double doExp(final double rcvr) {
    return Math.exp(rcvr);
  }
}
