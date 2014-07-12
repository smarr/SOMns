package som.interpreter.nodes.specialized;

import som.interpreter.nodes.nary.BinaryExpressionNode;
import som.vm.Nil;
import som.vmobjects.SBlock;
import som.vmobjects.SObject;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.IndirectCallNode;


public abstract class WhilePrimitiveNode extends BinaryExpressionNode {
  final boolean predicateBool;
  @Child private IndirectCallNode call;

  protected WhilePrimitiveNode(final boolean predicateBool) {
    super(null);
    this.predicateBool = predicateBool;
    call = Truffle.getRuntime().createIndirectCallNode();
  }

  protected WhilePrimitiveNode(final WhilePrimitiveNode node) {
    this(node.predicateBool);
  }

  @Specialization
  protected SObject doWhileConditionally(final VirtualFrame frame,
      final SBlock loopCondition, final SBlock loopBody) {
    CompilerAsserts.neverPartOfCompilation(); // no caching, direct invokes, no loop count reporting...

    boolean loopConditionResult = (boolean) loopCondition.getMethod().
        invoke(frame, call, loopCondition);

    // TODO: this is a simplification, we don't cover the case receiver isn't a boolean
    while (loopConditionResult == predicateBool) {
      loopBody.getMethod().invoke(frame, call, loopBody);
      loopConditionResult = (boolean) loopCondition.getMethod().invoke(frame, call, loopCondition);
    }
    return Nil.nilObject;
  }

  @Override
  public void executeVoid(final VirtualFrame frame) {
    executeGeneric(frame);
  }

  public abstract static class WhileTruePrimitiveNode extends WhilePrimitiveNode {
    public WhileTruePrimitiveNode() { super(true); }
  }

  public abstract static class WhileFalsePrimitiveNode extends WhilePrimitiveNode {
    public WhileFalsePrimitiveNode() { super(false); }
  }
}
