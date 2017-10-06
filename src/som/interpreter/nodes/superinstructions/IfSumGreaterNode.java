package som.interpreter.nodes.superinstructions;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotTypeException;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.profiles.ConditionProfile;
import som.VM;
import som.compiler.Variable;
import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.LocalVariableNode;
import som.interpreter.nodes.SequenceNode;
import som.interpreter.nodes.literals.DoubleLiteralNode;
import som.interpreter.nodes.nary.EagerBinaryPrimitiveNode;
import som.interpreter.nodes.nary.ExprWithTagsNode;
import som.interpreter.nodes.specialized.IfInlinedLiteralNode;
import som.vm.constants.Nil;

import java.util.List;

/**
 * Created by fred on 06/10/17.
 */
abstract public class IfSumGreaterNode extends ExprWithTagsNode {
  private final FrameSlot left, right;
  private final double than;
  @Child private SequenceNode bodyNode;

  // In case we need to revert from this optimistic optimization, keep the
  // original nodes around
  @SuppressWarnings("unused") private final IfInlinedLiteralNode originalSubtree;

  public IfSumGreaterNode(final Variable.Local left, final Variable.Local right,
                          final double than,
                          final SequenceNode inlinedBodyNode,
                          final IfInlinedLiteralNode originalSubtree) {
    this.left = left.getSlot();
    this.right = right.getSlot();
    this.than = than;
    this.bodyNode = inlinedBodyNode;
    this.originalSubtree = originalSubtree;
  }

  private boolean evaluateCondition(final VirtualFrame frame) throws FrameSlotTypeException {
    return (frame.getDouble(left) + frame.getDouble(right)) > than;
  }

  @Specialization(rewriteOn={ FrameSlotTypeException.class })
  public Object executeSpecialized(final VirtualFrame frame) throws FrameSlotTypeException {
    if (evaluateCondition(frame)) {
      return bodyNode.executeGeneric(frame);
    } else {
      return Nil.nilObject;
    }
  }

  @Specialization(replaces = {"executeSpecialized"})
  public Object execute(final VirtualFrame frame) {
    Object result = originalSubtree.executeGeneric(frame);
    replace(originalSubtree);
    return result;
  }

  @Override
  public boolean isResultUsed(final ExpressionNode child) {
    Node parent = getParent();
    if (parent instanceof ExpressionNode) {
      return ((ExpressionNode) parent).isResultUsed(this);
    }
    return true;
  }

  public static IfSumGreaterNode replaceNode(final IfInlinedLiteralNode node) {
    EagerBinaryPrimitiveNode condition = (EagerBinaryPrimitiveNode)node.getConditionNode();
    List<Node> conditionChildren = NodeUtil.findNodeChildren(condition);
    EagerBinaryPrimitiveNode conditionLeft = (EagerBinaryPrimitiveNode)conditionChildren.get(0);
    List<Node> conditionLeftChildren = NodeUtil.findNodeChildren(conditionLeft);
    LocalVariableNode.LocalVariableReadNode leftOperand = (LocalVariableNode.LocalVariableReadNode)conditionLeftChildren.get(0);
    LocalVariableNode.LocalVariableReadNode rightOperand = (LocalVariableNode.LocalVariableReadNode)conditionLeftChildren.get(1);
    DoubleLiteralNode thanNode = (DoubleLiteralNode)conditionChildren.get(1);
    IfSumGreaterNode newNode = IfSumGreaterNodeGen.create(leftOperand.getVar(),
            rightOperand.getVar(),
            thanNode.getValue(),
            (SequenceNode)node.getBodyNode(),
            node).initialize(node.getSourceSection());
    node.replace(newNode);
    VM.insertInstrumentationWrapper(newNode);
    return newNode;
  }

  public static boolean isIfSumGreaterNode(ExpressionNode conditionNode,
                                           ExpressionNode bodyNode,
                                           VirtualFrame frame) {
    if(conditionNode instanceof EagerBinaryPrimitiveNode
            && bodyNode instanceof SequenceNode) {
      EagerBinaryPrimitiveNode condition = (EagerBinaryPrimitiveNode)conditionNode;
      if(condition.getOperation().equals(">")) {
        List<Node> conditionChildren = NodeUtil.findNodeChildren(condition);
        if(conditionChildren.get(0) instanceof EagerBinaryPrimitiveNode
                && conditionChildren.get(1) instanceof DoubleLiteralNode) {
          EagerBinaryPrimitiveNode conditionLeft = (EagerBinaryPrimitiveNode)conditionChildren.get(0);
          if(conditionLeft.getOperation().equals("+")) {
            List<Node> conditionLeftChildren = NodeUtil.findNodeChildren(conditionLeft);
            if(conditionLeftChildren.get(0) instanceof LocalVariableNode.LocalVariableReadNode
                    && conditionLeftChildren.get(1) instanceof LocalVariableNode.LocalVariableReadNode) {
              LocalVariableNode.LocalVariableReadNode leftOperand = (LocalVariableNode.LocalVariableReadNode)conditionLeftChildren.get(0);
              LocalVariableNode.LocalVariableReadNode rightOperand = (LocalVariableNode.LocalVariableReadNode)conditionLeftChildren.get(1);
              if(frame.isDouble(leftOperand.getVar().getSlot())
                      && frame.isDouble(rightOperand.getVar().getSlot())) {
                return true;
              }
            }
          }
        }
      }
    }
    return false;
  }
}