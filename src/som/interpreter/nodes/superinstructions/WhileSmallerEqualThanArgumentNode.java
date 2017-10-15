package som.interpreter.nodes.superinstructions;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotTypeException;
import com.oracle.truffle.api.frame.VirtualFrame;

import som.compiler.Variable;
import som.interpreter.SArguments;
import som.interpreter.nodes.ArgumentReadNode.LocalArgumentReadNode;
import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.LocalVariableNode.LocalVariableReadNode;
import som.interpreter.nodes.SOMNode;
import som.interpreter.nodes.nary.EagerBinaryPrimitiveNode;
import som.interpreter.nodes.nary.ExprWithTagsNode;
import som.interpreter.nodes.specialized.SomLoop;
import som.interpreter.nodes.specialized.whileloops.WhileInlinedLiteralsNode;
import som.interpreter.objectstorage.ObjectTransitionSafepoint;
import som.primitives.arithmetic.LessThanOrEqualPrim;
import som.vm.constants.Nil;
import tools.dym.Tags;


/**
 * Matches the following AST.
 *
 * <pre>
 * WhileInlinedLiteralsNode (expectedBool == true)
 *   EagerBinaryPrimitiveNode
 *     LocalVariableReadNode (of type Long)
 *     LocalArgumentReadNode (of type Long)
 *     LessThanOrEqualPrim
 *   ExpressionNode
 * </pre>
 *
 * and replaces it with
 *
 * <pre>
 * WhileSmallerEqualThanArgumentNode
 *   ExpressionNode
 * </pre>
 */
public abstract class WhileSmallerEqualThanArgumentNode extends ExprWithTagsNode {

  private FrameSlot             variableSlot;
  private final int             argumentIndex;
  @Child private ExpressionNode bodyNode;

  @SuppressWarnings("unused") private final WhileInlinedLiteralsNode originalSubtree;

  public WhileSmallerEqualThanArgumentNode(final Variable.Local variable,
      final int argumentIndex,
      final ExpressionNode bodyNode, final WhileInlinedLiteralsNode originalSubtree) {
    this.variableSlot = variable.getSlot();
    this.argumentIndex = argumentIndex;
    this.bodyNode = bodyNode;
    this.originalSubtree = originalSubtree;
  }

  @Override
  protected boolean isTaggedWith(final Class<?> tag) {
    if (tag == Tags.LoopNode.class) {
      return true;
    } else {
      return super.isTaggedWith(tag);
    }
  }

  private boolean evaluateCondition(final VirtualFrame frame) throws FrameSlotTypeException {
    Object argumentValue = SArguments.arg(frame, argumentIndex);
    if (!(argumentValue instanceof Long)) {
      // Argument is not of type Long! (should never happen)
      throw new FrameSlotTypeException();
    }
    // Frame.getLong might throw FrameSlotTypeException
    return frame.getLong(variableSlot) <= (Long) argumentValue;
  }

  @Specialization(rewriteOn = {FrameSlotTypeException.class})
  public Object executeSpecialized(final VirtualFrame frame) throws FrameSlotTypeException {
    long iterationCount = 0;

    // TODO: this is a simplification, we don't cover the case receiver isn't a boolean
    boolean loopConditionResult = evaluateCondition(frame);

    try {
      while (loopConditionResult) {
        bodyNode.executeGeneric(frame);
        loopConditionResult = evaluateCondition(frame);

        if (CompilerDirectives.inInterpreter()) {
          iterationCount++;
        }
        ObjectTransitionSafepoint.INSTANCE.checkAndPerformSafepoint();
      }
    } finally {
      if (CompilerDirectives.inInterpreter()) {
        SomLoop.reportLoopCount(iterationCount, this);
      }
    }
    return Nil.nilObject;
  }

  @Specialization(replaces = {"executeSpecialized"})
  public Object executeAndDeoptimize(final VirtualFrame frame) {
    // Execute the original subtree and replace myself with it
    Object result = originalSubtree.executeGeneric(frame);
    replace(originalSubtree);
    return result;
  }

  @Override
  public boolean isResultUsed(final ExpressionNode child) {
    return false;
  }

  /**
   * Check if the AST subtree has the correct shape.
   */
  public static boolean isWhileSmallerEqualThanArgumentNode(final boolean expectedBool,
      ExpressionNode conditionNode,
      final VirtualFrame frame) {
    // whileFalse: does not match
    if (!expectedBool) {
      return false;
    }
    conditionNode = SOMNode.unwrapIfNecessary(conditionNode);
    // ... is the condition a binary operation?
    if (conditionNode instanceof EagerBinaryPrimitiveNode) {
      EagerBinaryPrimitiveNode eagerNode = (EagerBinaryPrimitiveNode) conditionNode;
      // is the operation ``LocalVariable <= LocalArgument``?
      if (SOMNode.unwrapIfNecessary(eagerNode.getReceiver()) instanceof LocalVariableReadNode
          && SOMNode.unwrapIfNecessary(
              eagerNode.getArgument()) instanceof LocalArgumentReadNode
          && SOMNode.unwrapIfNecessary(
              eagerNode.getPrimitive()) instanceof LessThanOrEqualPrim) {
        LocalArgumentReadNode arg =
            (LocalArgumentReadNode) SOMNode.unwrapIfNecessary(eagerNode.getArgument());
        LocalVariableReadNode variable =
            (LocalVariableReadNode) SOMNode.unwrapIfNecessary(eagerNode.getReceiver());
        // Are variable and argument both of type Long?
        if (SArguments.arg(frame, arg.getArgumentIndex()) instanceof Long
            && frame.isLong(variable.getVar().getSlot())) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Replace ``node`` with a superinstruction. Assumes that the AST subtree has the correct
   * shape.
   */
  public static WhileSmallerEqualThanArgumentNode replaceNode(
      final WhileInlinedLiteralsNode node) {
    // Extract local variable slot and argument index
    EagerBinaryPrimitiveNode conditionNode =
        (EagerBinaryPrimitiveNode) SOMNode.unwrapIfNecessary(node.getConditionNode());
    LocalVariableReadNode variableRead =
        (LocalVariableReadNode) SOMNode.unwrapIfNecessary(conditionNode.getReceiver());
    LocalArgumentReadNode argumentRead =
        (LocalArgumentReadNode) SOMNode.unwrapIfNecessary(conditionNode.getArgument());
    // replace node with superinstruction
    WhileSmallerEqualThanArgumentNode newNode = WhileSmallerEqualThanArgumentNodeGen.create(
        variableRead.getVar(), argumentRead.getArgumentIndex(), node.getBodyNode(), node)
                                                                                    .initialize(
                                                                                        node.getSourceSection());
    node.replace(newNode);
    newNode.adoptChildren();
    // Without the following line, WhileSmallerEqualThanArgumentNode is not taken into
    // account when running the dynamic metrics tool.
    // However, if we uncomment the following line, `./som -dm` fails because of
    // the instrumentation nodes are messed up. But why?
    // VM.insertInstrumentationWrapper(newNode);
    return newNode;
  }

  public ExpressionNode getBodyNode() {
    return bodyNode;
  }
}
