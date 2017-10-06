package som.interpreter.nodes.superinstructions;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotTypeException;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.InstrumentableFactory;
import som.VM;
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
 * Matches the following AST:
 *
 * WhileInlinedLiteralsNode (expectedBool == true)
 *   EagerBinaryPrimitiveNode
 *     LocalVariableReadNode (of type Long)
 *     LocalArgumentReadNode (of type Long)
 *     LessThanOrEqualPrim
 *   ExpressionNode
 *
 * and replaces it with
 *
 * WhileSmallerEqualThanArgumentNode
 *   ExpressionNode
 *
 */
abstract public class WhileSmallerEqualThanArgumentNode extends ExprWithTagsNode {

  private FrameSlot variableSlot;
  private final int argumentIndex;
  @Child private ExpressionNode bodyNode;

  @SuppressWarnings("unused") private final WhileInlinedLiteralsNode originalSubtree;

  public WhileSmallerEqualThanArgumentNode(final Variable.Local variable, final int argumentIndex,
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
    if(!(argumentValue instanceof Long))
      throw new FrameSlotTypeException(); // Argument is not of type Long! (should never happen)
    // Frame.getLong might throw FrameSlotTypeException
    return frame.getLong(variableSlot) <= (Long)argumentValue;
  }

  @Specialization(rewriteOn = { FrameSlotTypeException.class })
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

  static public boolean isWhileSmallerEqualThanArgumentNode(boolean expectedBool,
                                                            ExpressionNode conditionNode,
                                                            VirtualFrame frame) {
    // whileFalse: does not match
    if(!expectedBool)
      return false;
    conditionNode = SOMNode.unwrapIfNecessary(conditionNode);
    // ... is the condition a binary operation?
    if(conditionNode instanceof EagerBinaryPrimitiveNode) {
      EagerBinaryPrimitiveNode eagerNode = (EagerBinaryPrimitiveNode)conditionNode;
      // is the operation ``LocalVariable <= LocalArgument``?
      if(SOMNode.unwrapIfNecessary(eagerNode.getReceiver()) instanceof LocalVariableReadNode
              && SOMNode.unwrapIfNecessary(eagerNode.getArgument()) instanceof LocalArgumentReadNode
              && SOMNode.unwrapIfNecessary(eagerNode.getPrimitive()) instanceof LessThanOrEqualPrim) {
        LocalArgumentReadNode arg = (LocalArgumentReadNode)SOMNode.unwrapIfNecessary(eagerNode.getArgument());
        LocalVariableReadNode variable = (LocalVariableReadNode)SOMNode.unwrapIfNecessary(eagerNode.getReceiver());
        // Are variable and argument both of type Long?
        if(SArguments.arg(frame, arg.getArgumentIndex()) instanceof Long
          && frame.isLong(variable.getVar().getSlot())) {
          return true;
        }
      }
    }
    return false;
  }

  static public WhileSmallerEqualThanArgumentNode replaceNode(WhileInlinedLiteralsNode node) {
    // Extract local variable slot and argument index
    EagerBinaryPrimitiveNode conditionNode = (EagerBinaryPrimitiveNode)SOMNode.unwrapIfNecessary(node.getConditionNode());
    LocalVariableReadNode variableRead = (LocalVariableReadNode)SOMNode.unwrapIfNecessary(conditionNode.getReceiver());
    LocalArgumentReadNode argumentRead = (LocalArgumentReadNode)SOMNode.unwrapIfNecessary(conditionNode.getArgument());
    // replace node with superinstruction
    WhileSmallerEqualThanArgumentNode newNode = WhileSmallerEqualThanArgumentNodeGen.create(
      variableRead.getVar(), argumentRead.getArgumentIndex(), node.getBodyNode(), node
    ).initialize(node.getSourceSection());
    node.replace(newNode);
    //VM.insertInstrumentationWrapper(newNode); // TODO: Fix instrumentation of While Node!!
    //newNode.adoptChildren();
    return newNode;
  }
}
