package som.interpreter.nodes.superinstructions;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotTypeException;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import som.VM;
import som.compiler.Variable;
import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.LocalVariableNode.LocalVariableReadNode;
import som.interpreter.nodes.SequenceNode;
import som.interpreter.nodes.literals.DoubleLiteralNode;
import som.interpreter.nodes.nary.EagerBinaryPrimitiveNode;
import som.interpreter.nodes.nary.ExprWithTagsNode;
import som.interpreter.nodes.specialized.IfInlinedLiteralNode;
import som.vm.constants.Nil;

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
    EagerBinaryPrimitiveNode conditionLeft = (EagerBinaryPrimitiveNode)condition.getReceiver();
    LocalVariableReadNode leftOperand = (LocalVariableReadNode)conditionLeft.getReceiver();
    LocalVariableReadNode rightOperand = (LocalVariableReadNode)conditionLeft.getArgument();
    DoubleLiteralNode thanNode = (DoubleLiteralNode)condition.getArgument();
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
        if(condition.getReceiver() instanceof EagerBinaryPrimitiveNode
                && condition.getArgument() instanceof DoubleLiteralNode) {
          EagerBinaryPrimitiveNode conditionLeft = (EagerBinaryPrimitiveNode)condition.getReceiver();
          if(conditionLeft.getOperation().equals("+")) {
            if(conditionLeft.getReceiver() instanceof LocalVariableReadNode
                    && conditionLeft.getArgument() instanceof LocalVariableReadNode) {
              LocalVariableReadNode leftOperand = (LocalVariableReadNode)conditionLeft.getReceiver();
              LocalVariableReadNode rightOperand = (LocalVariableReadNode)conditionLeft.getArgument();
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