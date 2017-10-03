package som.interpreter.nodes.superinstructions;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.FrameSlotTypeException;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeUtil;
import som.VM;
import som.compiler.Variable;
import som.interpreter.InliningVisitor;
import som.interpreter.nodes.ArgumentReadNode;
import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.LocalVariableNode;
import som.interpreter.nodes.SOMNode;
import som.interpreter.nodes.literals.IntegerLiteralNode;
import som.interpreter.nodes.nary.EagerBinaryPrimitiveNode;
import som.primitives.arithmetic.AdditionPrim;
import som.primitives.arithmetic.MultiplicationPrim;
import tools.dym.Tags;

import java.util.List;

/**
 * Created by fred on 29/09/17.
 */
public abstract class AssignVariableProductNode extends LocalVariableNode {
  protected final FrameSlot leftSlot, rightSlot;
  protected final LocalVariableNode originalSubtree;

  public AssignVariableProductNode(final Variable.Local destination,
                                   final Variable.Local left,
                                   final Variable.Local right,
                                   final LocalVariableNode originalSubtree) {
    super(destination);
    this.leftSlot = left.getSlot();
    this.rightSlot = right.getSlot();
    this.originalSubtree = originalSubtree;
  }

  public AssignVariableProductNode(final AssignVariableProductNode node) {
    super(node.var);
    this.leftSlot = node.getLeftSlot();
    this.rightSlot = node.getRightSlot();
    this.originalSubtree = node.getOriginalSubtree();
  }

  public FrameSlot getLeftSlot() {
    return leftSlot;
  }

  public FrameSlot getRightSlot() {
    return rightSlot;
  }

  public LocalVariableNode getOriginalSubtree() {
    return originalSubtree;
  }

  @Specialization(guards = "isDoubleKind(frame)", rewriteOn = {FrameSlotTypeException.class})
  public final double writeDouble(final VirtualFrame frame) throws FrameSlotTypeException {
    double newValue = frame.getDouble(leftSlot) * frame.getDouble(rightSlot);
    frame.setDouble(slot, newValue);
    return newValue;
  }

  @Specialization(replaces = {"writeDouble"})
  public final Object writeGeneric(final VirtualFrame frame) {
    /* Replace myself with the stored original subtree */
    Object result = originalSubtree.executeGeneric(frame);
    replace(originalSubtree);
    return result;
  }

  // uses frame to make sure guard is not converted to assertion
  protected final boolean isDoubleKind(final VirtualFrame frame) {
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

  /** Check if the AST subtree has the correct shape, i.e. looks like this:
   * LocalVariableWriteNode
   * |- EagerBinaryPrimitiveNode
   *    |- LocalVariableReadNode
   *    |- LocalVariableReadNode
   *    |- MultiplicationPrim
   */
  public static boolean isAssignOperation(ExpressionNode exp) {
    exp = SOMNode.unwrapIfNecessary(exp);
    if(exp instanceof EagerBinaryPrimitiveNode) {
      List<Node> children = NodeUtil.findNodeChildren(exp);
      if(SOMNode.unwrapIfNecessary(children.get(0)) instanceof LocalVariableReadNode
              && SOMNode.unwrapIfNecessary(children.get(1)) instanceof LocalVariableReadNode
              && SOMNode.unwrapIfNecessary(children.get(2)) instanceof MultiplicationPrim) {
        return true;
      }
    }
    return false;
  }

  public static void replaceNode(LocalVariableWriteNode node) {
    // TODO: This could be optimized
    List<Node> eagerChildren = NodeUtil.findNodeChildren(
            SOMNode.unwrapIfNecessary(node.getExp()));
    Variable.Local left = ((LocalVariableReadNode)SOMNode.unwrapIfNecessary(eagerChildren.get(0))).getVar();
    Variable.Local right = ((LocalVariableReadNode)SOMNode.unwrapIfNecessary(eagerChildren.get(1))).getVar();
    AssignVariableProductNode newNode = AssignVariableProductNodeGen.create(node.getVar(),
            left,
            right,
            node).initialize(node.getSourceSection());
    node.replace(newNode);
    VM.insertInstrumentationWrapper(newNode);
  }
}
