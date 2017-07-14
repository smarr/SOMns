package som.interpreter.nodes.specialized;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.source.SourceSection;

import bd.primitives.Primitive;
import som.VM;
import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.OperationNode;
import som.interpreter.nodes.literals.BlockNode;
import som.interpreter.nodes.nary.BinaryBasicOperation;
import som.interpreter.nodes.nary.BinaryComplexOperation;
import som.interpreter.nodes.nary.BinaryExpressionNode;
import som.interpreter.nodes.specialized.AndMessageNode.AndOrSplzr;
import som.interpreter.nodes.specialized.AndMessageNodeFactory.AndBoolMessageNodeFactory;
import som.vm.Primitives.Specializer;
import som.vmobjects.SBlock;
import som.vmobjects.SInvokable;
import tools.dym.Tags.ControlFlowCondition;
import tools.dym.Tags.OpComparison;


@GenerateNodeFactory
@Primitive(selector = "and:", noWrapper = true, specializer = AndOrSplzr.class)
@Primitive(selector = "&&", noWrapper = true, specializer = AndOrSplzr.class)
public abstract class AndMessageNode extends BinaryComplexOperation {
  public static class AndOrSplzr extends Specializer<BinaryExpressionNode> {
    protected final NodeFactory<BinaryExpressionNode> boolFact;

    @SuppressWarnings({"unchecked", "rawtypes"})
    public AndOrSplzr(final Primitive prim,
        final NodeFactory<BinaryExpressionNode> fact, final VM vm) {
      this(prim, fact, (NodeFactory) AndBoolMessageNodeFactory.getInstance(), vm);
    }

    protected AndOrSplzr(final Primitive prim, final NodeFactory<BinaryExpressionNode> msgFact,
        final NodeFactory<BinaryExpressionNode> boolFact, final VM vm) {
      super(prim, msgFact, vm);
      this.boolFact = boolFact;
    }

    @Override
    public final boolean matches(final Object[] args, final ExpressionNode[] argNodes) {
      // XXX: this is the case when doing parse-time specialization
      if (args == null) {
        return true;
      }

      return args[0] instanceof Boolean &&
          (args[1] instanceof Boolean ||
              unwrapIfNecessary(argNodes[1]) instanceof BlockNode);
    }

    @Override
    public final BinaryExpressionNode create(final Object[] arguments,
        final ExpressionNode[] argNodes, final SourceSection section,
        final boolean eagerWrapper) {
      if (unwrapIfNecessary(argNodes[1]) instanceof BlockNode) {
        return fact.createNode(arguments[1], argNodes[0], argNodes[1]).initialize(section);
      } else {
        assert arguments == null || arguments[1] instanceof Boolean;
        return boolFact.createNode(argNodes[0], argNodes[1]).initialize(section);
      }
    }
  }

  private final SInvokable      blockMethod;
  @Child private DirectCallNode blockValueSend;

  public AndMessageNode(final SBlock arg) {
    blockMethod = arg.getMethod();
    blockValueSend = Truffle.getRuntime().createDirectCallNode(
        blockMethod.getCallTarget());
  }

  protected final boolean isSameBlock(final SBlock argument) {
    return argument.getMethod() == blockMethod;
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

  @Specialization(guards = "isSameBlock(argument)")
  public final boolean doAnd(final boolean receiver, final SBlock argument) {
    if (receiver == false) {
      return false;
    } else {
      return (boolean) blockValueSend.call(new Object[] {argument});
    }
  }

  @GenerateNodeFactory
  public abstract static class AndBoolMessageNode extends BinaryBasicOperation
      implements OperationNode {
    @Override
    protected boolean isTaggedWithIgnoringEagerness(final Class<?> tag) {
      if (tag == OpComparison.class) {
        return true;
      } else {
        return super.isTaggedWithIgnoringEagerness(tag);
      }
    }

    @Specialization
    public final boolean doAnd(final VirtualFrame frame, final boolean receiver,
        final boolean argument) {
      return receiver && argument;
    }

    @Override
    public String getOperation() {
      return "&&";
    }

    @Override
    public int getNumArguments() {
      return 2;
    }
  }
}
