package som.primitives;

import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.source.SourceSection;

import som.interpreter.nodes.nary.UnaryBasicOperation;
import som.interpreter.nodes.nary.UnaryExpressionNode;
import tools.dym.Tags.OpArithmetic;
import tools.highlight.Tags.LiteralTag;


public abstract class DoublePrims  {

  @GenerateNodeFactory
  @Primitive("doubleRound:")
  public abstract static class RoundPrim extends UnaryBasicOperation {
    public RoundPrim(final boolean eagWrap, final SourceSection source) { super(eagWrap, source); }
    public RoundPrim(final SourceSection source) { super(false, source); }

    @Override
    protected boolean isTaggedWithIgnoringEagerness(final Class<?> tag) {
      if (tag == OpArithmetic.class) {
        return true;
      } else {
        return super.isTaggedWithIgnoringEagerness(tag);
      }
    }

    @Specialization
    public final long doDouble(final double receiver) {
      return Math.round(receiver);
    }
  }

  @GenerateNodeFactory
  @Primitive("doubleAsInteger:")
  public abstract static class AsIntPrim extends UnaryBasicOperation {
    public AsIntPrim(final boolean eagWrap, final SourceSection source) { super(eagWrap, source); }
    public AsIntPrim(final SourceSection source) { super(false, source); }

    @Override
    protected boolean isTaggedWithIgnoringEagerness(final Class<?> tag) {
      if (tag == OpArithmetic.class) {
        return true;
      } else {
        return super.isTaggedWithIgnoringEagerness(tag);
      }
    }

    @Specialization
    public final long doDouble(final double receiver) {
      return (long) receiver;
    }
  }

  @GenerateNodeFactory
  @Primitive("doublePositiveInfinity:")
  public abstract static class PositiveInfinityPrim extends UnaryExpressionNode {
    public PositiveInfinityPrim(final SourceSection source) { super(false, source); }

    @Override
    protected boolean isTaggedWithIgnoringEagerness(final Class<?> tag) {
      if (tag == LiteralTag.class) {
        return true;
      } else {
        return super.isTaggedWithIgnoringEagerness(tag);
      }
    }

    @Specialization
    public final double doSClass(final Object receiver) {
      return Double.POSITIVE_INFINITY;
    }
  }
}
