package som.interpreter.nodes.specialized;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.source.SourceSection;

import som.interpreter.nodes.OperationNode;
import som.interpreter.nodes.nary.BinaryBasicOperation;
import som.interpreter.nodes.nary.BinaryComplexOperation;
import som.interpreter.nodes.nary.BinaryExpressionNode;
import som.interpreter.nodes.specialized.AndMessageNode.AndOrSplzr;
import som.interpreter.nodes.specialized.OrMessageNode.OrSplzr;
import som.interpreter.nodes.specialized.OrMessageNodeFactory.OrBoolMessageNodeFactory;
import som.primitives.Primitive;
import som.vmobjects.SBlock;
import som.vmobjects.SInvokable;
import tools.dym.Tags.ControlFlowCondition;
import tools.dym.Tags.OpComparison;


@GenerateNodeFactory
@Primitive(selector = "or:", noWrapper = true, specializer = OrSplzr.class)
@Primitive(selector = "||",  noWrapper = true, specializer = OrSplzr.class)
public abstract class OrMessageNode extends BinaryComplexOperation {
  public static final class OrSplzr extends AndOrSplzr {
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public OrSplzr(final Primitive prim, final NodeFactory<BinaryExpressionNode> fact) {
      super(prim, fact, (NodeFactory) OrBoolMessageNodeFactory.getInstance());
    }
  }

  private final SInvokable blockMethod;
  @Child private DirectCallNode blockValueSend;

  public OrMessageNode(final SBlock arg, final SourceSection source) {
    super(false, source);
    blockMethod = arg.getMethod();
    blockValueSend = Truffle.getRuntime().createDirectCallNode(
        blockMethod.getCallTarget());
  }

  public OrMessageNode(final OrMessageNode copy) {
    super(false, copy.getSourceSection());
    blockMethod = copy.blockMethod;
    blockValueSend = copy.blockValueSend;
  }

  @Override
  protected boolean isTaggedWithIgnoringEagerness(final Class<?> tag) {
    if (tag == ControlFlowCondition.class) {
      return true;
    } else if (tag == OpComparison.class) {
      return true;
    } else {
      return super.isTaggedWithIgnoringEagerness(tag);
    }
  }

  protected final boolean isSameBlock(final SBlock argument) {
    return argument.getMethod() == blockMethod;
  }

  @Specialization(guards = "isSameBlock(argument)")
  public final boolean doOr(final VirtualFrame frame, final boolean receiver,
      final SBlock argument) {
    if (receiver) {
      return true;
    } else {
      return (boolean) blockValueSend.call(frame, new Object[] {argument});
    }
  }

  @GenerateNodeFactory
  public abstract static class OrBoolMessageNode extends BinaryBasicOperation implements OperationNode {
    public OrBoolMessageNode(final SourceSection source) { super(false, source); }

    @Override
    protected boolean isTaggedWithIgnoringEagerness(final Class<?> tag) {
      if (tag == OpComparison.class) {
        return true;
      } else {
        return super.isTaggedWithIgnoringEagerness(tag);
      }
    }

    @Specialization
    public final boolean doOr(final VirtualFrame frame, final boolean receiver,
        final boolean argument) {
      return receiver || argument;
    }

    @Override
    public String getOperation() {
      return "||";
    }

    @Override
    public int getNumArguments() {
      return 2;
    }
  }
}
