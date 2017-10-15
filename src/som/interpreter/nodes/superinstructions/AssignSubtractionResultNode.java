package som.interpreter.nodes.superinstructions;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.VirtualFrame;

import som.VM;
import som.compiler.Variable;
import som.interpreter.InliningVisitor;
import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.LocalVariableNode;
import som.interpreter.nodes.MessageSendNode.AbstractMessageSendNode;
import som.interpreter.nodes.MessageSendNode.GenericMessageSendNode;
import som.interpreter.nodes.SOMNode;
import som.interpreter.nodes.nary.EagerBinaryPrimitiveNode;
import som.primitives.arithmetic.SubtractionPrim;
import tools.dym.Tags;


/**
 * Matches the following AST subtree.
 *
 * <pre>
 * LocalVariableWriteNode
 *   EagerBinaryPrimitiveNode
 *     GenericMessageSendNode
 *     GenericMessageSendNode
 *     SubtractionPrim
 * </pre>
 *
 * and replaces it with
 *
 * <pre>
 * AssignSubtractionResultNode
 *   GenericMessageSendNode
 *   GenericMessageSendNode
 * </pre>
 *
 */
@NodeChildren({
    @NodeChild(value = "left", type = AbstractMessageSendNode.class),
    @NodeChild(value = "right", type = AbstractMessageSendNode.class)
})
public abstract class AssignSubtractionResultNode extends LocalVariableNode {
  private final LocalVariableWriteNode originalSubtree;

  public AssignSubtractionResultNode(final Variable.Local variable,
      final LocalVariableWriteNode originalSubtree) {
    super(variable);
    this.originalSubtree = originalSubtree;
  }

  public AssignSubtractionResultNode(final AssignSubtractionResultNode node) {
    super(node.var);
    this.originalSubtree = node.getOriginalSubtree();
  }

  @Specialization(guards = {"isDoubleKind(leftValue)"})
  public final double writeDouble(final VirtualFrame frame,
      final double leftValue,
      final double rightValue) {
    // Use Truffle DSL to retrieve the left and right values, set slot, return value
    double result = leftValue - rightValue;
    frame.setDouble(slot, result);
    return result;
  }

  @Specialization(replaces = {"writeDouble"})
  public final Object writeGeneric(final VirtualFrame frame,
      final Object leftValue,
      final Object rightValue) {
    // Replace myself with the stored original subtree
    // As ``left`` and ``right`` are already evaluated, we have to take
    // special care to avoid evaluating them twice. Thus, we extract
    // the EagerBinaryPrimitiveNode, invoke its ``executeEvaluated``
    // method to get the subtraction result and use the original subtree's
    // ``writeGeneric`` method to write the result to a slot.
    assert SOMNode.unwrapIfNecessary(
        originalSubtree.getExp()) instanceof EagerBinaryPrimitiveNode;
    EagerBinaryPrimitiveNode eagerNode =
        (EagerBinaryPrimitiveNode) SOMNode.unwrapIfNecessary(originalSubtree.getExp());
    Object result = eagerNode.executeEvaluated(frame, leftValue, rightValue);
    originalSubtree.writeGeneric(frame, result);
    // Then, we replace ourselves.
    replace(originalSubtree);
    return result;
  }

  // uses leftValue to make sure guard is not converted to assertion
  protected final boolean isDoubleKind(final double leftValue) {
    if (slot.getKind() == FrameSlotKind.Double) {
      return true;
    }
    if (slot.getKind() == FrameSlotKind.Illegal) {
      slot.setKind(FrameSlotKind.Double);
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
    /*
     * This should never happen because ``replaceAfterScopeChange`` is only called in the
     * parsing stage, whereas the ``AssignSubtractionResultNode`` superinstruction is only
     * inserted
     * into the AST *after* parsing.
     */
    throw new RuntimeException("replaceAfterScopeChange: This should never happen!");
  }

  public LocalVariableWriteNode getOriginalSubtree() {
    return originalSubtree;
  }

  /**
   * Check if the subtree has the correct shape.
   */
  public static boolean isAssignSubtractionResultOperation(ExpressionNode exp) {
    exp = SOMNode.unwrapIfNecessary(exp);
    if (exp instanceof EagerBinaryPrimitiveNode) {
      EagerBinaryPrimitiveNode eagerNode = (EagerBinaryPrimitiveNode) exp;
      if (SOMNode.unwrapIfNecessary(eagerNode.getReceiver()) instanceof GenericMessageSendNode
          && SOMNode.unwrapIfNecessary(
              eagerNode.getArgument()) instanceof GenericMessageSendNode
          && SOMNode.unwrapIfNecessary(eagerNode.getPrimitive()) instanceof SubtractionPrim) {
        return true;
      }
    }
    return false;
  }

  /**
   * Replace ``node`` with a superinstruction. This assumes that the subtree has the correct
   * shape.
   */
  public static void replaceNode(final LocalVariableWriteNode node) {
    EagerBinaryPrimitiveNode eagerNode =
        (EagerBinaryPrimitiveNode) SOMNode.unwrapIfNecessary(node.getExp());
    AssignSubtractionResultNode newNode = AssignSubtractionResultNodeGen.create(
        node.getVar(),
        node,
        (AbstractMessageSendNode) eagerNode.getReceiver(),
        (AbstractMessageSendNode) eagerNode.getArgument()).initialize(node.getSourceSection());
    node.replace(newNode);
    VM.insertInstrumentationWrapper(newNode);
  }

  public abstract AbstractMessageSendNode getLeft();

  public abstract AbstractMessageSendNode getRight();
}
