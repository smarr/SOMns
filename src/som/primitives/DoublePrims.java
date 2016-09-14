package som.primitives;

import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.source.SourceSection;

import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.nary.UnaryBasicOperation;
import som.interpreter.nodes.nary.UnaryExpressionNode;
import som.vm.Primitives.Specializer;
import som.vm.constants.Classes;
import tools.dym.Tags.OpArithmetic;
import tools.highlight.Tags.LiteralTag;


public abstract class DoublePrims  {

  @GenerateNodeFactory
  @Primitive(primitive = "doubleRound:", selector = "round",
             receiverType = Double.class)
  public abstract static class RoundPrim extends UnaryBasicOperation {
    public RoundPrim(final boolean eagWrap, final SourceSection source) { super(eagWrap, source); }

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
  @Primitive(primitive = "doubleAsInteger:", selector = "asInteger",
             receiverType = Double.class)
  public abstract static class AsIntPrim extends UnaryBasicOperation {
    public AsIntPrim(final boolean eagWrap, final SourceSection source) { super(eagWrap, source); }

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

  public static class IsDoubleClass extends Specializer<ExpressionNode> {
    public IsDoubleClass(final Primitive prim, final NodeFactory<ExpressionNode> fact) { super(prim, fact); }

    @Override
    public boolean matches(final Object[] args, final ExpressionNode[] argNodess) {
      return args[0] == Classes.doubleClass;
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "doublePositiveInfinity:",
             selector = "PositiveInfinity", noWrapper = true,
             specializer = IsDoubleClass.class)
  public abstract static class PositiveInfinityPrim extends UnaryExpressionNode {
    public PositiveInfinityPrim(final boolean eagerWrapper, final SourceSection source) { super(eagerWrapper, source); }

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
