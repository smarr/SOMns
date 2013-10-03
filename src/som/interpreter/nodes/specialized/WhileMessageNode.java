package som.interpreter.nodes.specialized;

import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.MessageNode;
import som.vm.Universe;
import som.vmobjects.SBlock;
import som.vmobjects.SClass;
import som.vmobjects.SMethod;
import som.vmobjects.SObject;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.frame.VirtualFrame;


public class WhileMessageNode extends MessageNode {
  private final SClass falseClass;
  private final SClass trueClass;

  private final SMethod blockMethodCondition;
  private final SMethod blockMethodLoopBody;

  private final boolean whileTrue;

  private final SObject[] noArgs;

  public WhileMessageNode(final ExpressionNode receiver,
      final ExpressionNode[] arguments, final SSymbol selector,
      final Universe universe, final SMethod condition,
      final SMethod loopBody, final boolean whileTrue) {
    super(receiver, arguments, selector, universe);
    assert arguments != null && arguments.length == 1;

    trueClass  = universe.trueObject.getSOMClass();
    falseClass = universe.falseObject.getSOMClass();

    this.blockMethodCondition = condition;
    this.blockMethodLoopBody  = loopBody;
    this.whileTrue = whileTrue;

    noArgs = new SObject[0];
  }

  private SObject executeBlock(final VirtualFrame frame,
      final SMethod blockMethod) {
    SBlock b = universe.newBlock(blockMethod, frame.materialize(), 1);
    return blockMethod.invoke(frame.pack(), b, noArgs);
  }

  @Override
  public SObject executeGeneric(final VirtualFrame frame) {
    SObject rcvr;

    rcvr = evalConditionIfNecessary(frame);

    SObject arg = null;

    if (blockMethodLoopBody == null) {
      arg = arguments[0].executeGeneric(frame);
    }


    return evaluateBody(frame, rcvr, arg);
  }

  public SObject evalConditionIfNecessary(final VirtualFrame frame) {
    SObject rcvr;
    if (blockMethodCondition == null) {
      rcvr = receiver.executeGeneric(frame);
    } else {
      rcvr = executeBlock(frame, blockMethodCondition);
    }
    return rcvr;
  }

  public SObject evaluateBody(final VirtualFrame frame, final SObject rcvr,
      final SObject arg) {
    SClass currentCondClass = classOfReceiver(rcvr, receiver);

    while ((currentCondClass == trueClass  &&  whileTrue) ||
           (currentCondClass == falseClass && !whileTrue)) {
      if (blockMethodLoopBody != null) {
        executeBlock(frame, blockMethodLoopBody);
      }

      SObject newConditionResult = evalConditionIfNecessary(frame);
      currentCondClass = classOfReceiver(newConditionResult, receiver);
    }

    return universe.nilObject;
  }

  @Override
  public ExpressionNode cloneForInlining() {
    return new WhileMessageNode(receiver, arguments, selector, universe,
        blockMethodCondition, blockMethodLoopBody, whileTrue);
  }
}
