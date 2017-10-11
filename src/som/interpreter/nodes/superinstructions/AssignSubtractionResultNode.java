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
import som.interpreter.nodes.MessageSendNode;
import som.interpreter.nodes.MessageSendNode.GenericMessageSendNode;
import som.interpreter.nodes.MessageSendNode.AbstractMessageSendNode;
import som.interpreter.nodes.SOMNode;
import som.interpreter.nodes.nary.EagerBinaryPrimitiveNode;
import som.primitives.arithmetic.SubtractionPrim;
import tools.dym.Tags;

/**
 * Created by fred on 11/10/17.
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
    double result = leftValue - rightValue;
    frame.setDouble(slot, result);
    return result;
  }

  @Specialization(replaces = {"writeDouble"})
  public final Object writeGeneric(final VirtualFrame frame,
                                   final Object leftValue,
                                   final Object rightValue) {
    /* Replace myself with the stored original subtree */
    assert SOMNode.unwrapIfNecessary(originalSubtree.getExp()) instanceof EagerBinaryPrimitiveNode;
    EagerBinaryPrimitiveNode eagerNode = (EagerBinaryPrimitiveNode)SOMNode.unwrapIfNecessary(originalSubtree.getExp());
    Object result = eagerNode.executeEvaluated(frame, leftValue, rightValue);
    originalSubtree.writeGeneric(frame, result);
    replace(originalSubtree);
    return result;
  }

  // uses expValue to make sure guard is not converted to assertion
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
    /* This should never happen because ``replaceAfterScopeChange`` is only called in the
    parsing stage, whereas the ``IncrementOperationNode`` superinstruction is only inserted
    into the AST *after* parsing. */
    throw new RuntimeException("replaceAfterScopeChange: This should never happen!");
  }

  public LocalVariableWriteNode getOriginalSubtree() {
    return originalSubtree;
  }


  public static boolean isAssignSubtractionResultOperation(ExpressionNode exp) {
    exp = SOMNode.unwrapIfNecessary(exp);
    if(exp instanceof EagerBinaryPrimitiveNode) {
      EagerBinaryPrimitiveNode eagerNode = (EagerBinaryPrimitiveNode)exp;
      if(SOMNode.unwrapIfNecessary(eagerNode.getReceiver()) instanceof GenericMessageSendNode
              && SOMNode.unwrapIfNecessary(eagerNode.getArgument()) instanceof GenericMessageSendNode
              && SOMNode.unwrapIfNecessary(eagerNode.getPrimitive()) instanceof SubtractionPrim) {
        return true;
      }
    }
    return false;
  }

  public static void replaceNode(LocalVariableWriteNode node) {
    EagerBinaryPrimitiveNode eagerNode = (EagerBinaryPrimitiveNode)SOMNode.unwrapIfNecessary(node.getExp());
    AssignSubtractionResultNode newNode = AssignSubtractionResultNodeGen.create(
            node.getVar(),
            node,
            (AbstractMessageSendNode)eagerNode.getReceiver(),
            (AbstractMessageSendNode)eagerNode.getArgument()
    ).initialize(node.getSourceSection());
    node.replace(newNode);
    VM.insertInstrumentationWrapper(newNode);
  }

  public abstract AbstractMessageSendNode getLeft();
  public abstract AbstractMessageSendNode getRight();
}
