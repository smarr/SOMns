package som.interpreter.nodes.superinstructions;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.FrameSlotTypeException;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeUtil;
import som.VM;
import som.compiler.Variable;
import som.interpreter.InliningVisitor;
import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.LocalVariableNode;
import som.interpreter.nodes.SOMNode;
import som.interpreter.nodes.literals.IntegerLiteralNode;
import som.interpreter.nodes.nary.EagerBinaryPrimitiveNode;
import som.primitives.arithmetic.AdditionPrim;
import tools.dym.Tags;

import java.util.List;

public abstract class IncrementOperationNode extends LocalVariableNode {
  private final long increment;
  private final LocalVariableNode originalSubtree;

  public IncrementOperationNode(final Variable.Local variable,
                                final long increment,
                                final LocalVariableNode originalSubtree) {
    super(variable);
    this.increment = increment;
    this.originalSubtree = originalSubtree;
  }

  public IncrementOperationNode(final IncrementOperationNode node) {
    super(node.var);
    this.increment = node.getIncrement();
    this.originalSubtree = node.getOriginalSubtree();
  }

  public long getIncrement() {
    return increment;
  }

  @Specialization(guards = "isLongKind(frame)", rewriteOn = {FrameSlotTypeException.class})
  public final long writeLong(final VirtualFrame frame) throws FrameSlotTypeException {
    long newValue = frame.getLong(slot) + increment;
    frame.setLong(slot, newValue);
    return newValue;
  }

  @Specialization(replaces = {"writeLong"})
  public final Object writeGeneric(final VirtualFrame frame) {
    /* Replace myself with the stored original subtree */
    Object result = originalSubtree.executeGeneric(frame);
    replace(originalSubtree);
    return result;
  }

  protected final boolean isLongKind(final VirtualFrame frame) { // uses frame to make sure guard is not converted to assertion
    if (slot.getKind() == FrameSlotKind.Long) {
      return true;
    }
    if (slot.getKind() == FrameSlotKind.Illegal) {
      slot.setKind(FrameSlotKind.Long);
      return true;
    }
    return false;
  }

  @Override
  protected final boolean isTaggedWith(final Class<?> tag) {
    if (tag == Tags.LocalVarWrite.class) {
      return true;
    } else {
      return super.isTaggedWith(tag);
    }
  }

  @Override
  public String toString() {
    return this.getClass().getSimpleName() + "[" + var.name + "]";
  }

  @Override
  public void replaceAfterScopeChange(final InliningVisitor inliner) {
    /* This should never happen because ``replaceAfterScopeChange`` is only called in the
    parsing stage, whereas the ``IncrementOperationNode`` superinstruction is only inserted
    into the AST *after* parsing. */
    throw new RuntimeException("replaceAfterScopeChange: This should never happen!");
  }

  public LocalVariableNode getOriginalSubtree() {
    return originalSubtree;
  }

  /** Check if the AST subtree has the shape of an increment operation, i.e. looks like this:
   * LocalVariableWriteNode
   * |- EagerBinaryPrimitiveNode
   *    |- LocalVariableReadNode (with var == this.var)
   *    |- IntegerLiteralNode
   *    |- AdditionPrim
   */
  public static boolean isIncrementOperation(ExpressionNode exp, Variable.Local var) {
    exp = SOMNode.unwrapIfNecessary(exp);
    if(exp instanceof EagerBinaryPrimitiveNode) {
      EagerBinaryPrimitiveNode eagerNode = (EagerBinaryPrimitiveNode)exp;
      if(SOMNode.unwrapIfNecessary(eagerNode.getReceiver()) instanceof LocalVariableReadNode
              && SOMNode.unwrapIfNecessary(eagerNode.getArgument()) instanceof IntegerLiteralNode
              && SOMNode.unwrapIfNecessary(eagerNode.getPrimitive()) instanceof AdditionPrim) {
        LocalVariableReadNode read = (LocalVariableReadNode)SOMNode.unwrapIfNecessary(eagerNode.getReceiver());
        if(read.getVar().equals(var)) {
          return true;
        }
      }
    }
    return false;
  }

  public static void replaceNode(LocalVariableWriteNode node) {
    EagerBinaryPrimitiveNode eagerNode = (EagerBinaryPrimitiveNode)SOMNode.unwrapIfNecessary(node.getExp());
    long increment = ((IntegerLiteralNode)eagerNode.getArgument()).getValue();
    IncrementOperationNode newNode = IncrementOperationNodeGen.create(node.getVar(),
            increment,
            node).initialize(node.getSourceSection());
    node.replace(newNode);
    VM.insertInstrumentationWrapper(newNode);
  }
}