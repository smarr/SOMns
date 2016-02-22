package som.primitives.arithmetic;

import som.interpreter.nodes.nary.UnaryExpressionNode;
import som.primitives.Primitive;

import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.source.SourceSection;


@GenerateNodeFactory
@Primitive("doubleExp:")
public abstract class ExpPrim extends UnaryExpressionNode {
  public ExpPrim(final SourceSection source) { super(source); }

  @Specialization
  public final double doExp(final double rcvr) {
    return Math.exp(rcvr);
  }
}
