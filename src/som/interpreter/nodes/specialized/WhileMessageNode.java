package som.interpreter.nodes.specialized;

import som.interpreter.nodes.AbstractMessageNode;
import som.interpreter.nodes.ExpressionNode;
import som.vm.Universe;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SBlock;
import som.vmobjects.SClass;
import som.vmobjects.SMethod;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;


public abstract class WhileMessageNode extends AbstractMessageNode {

  private final SMethod blockMethodCondition;
  private final SMethod blockMethodLoopBody;

  private final boolean whileTrue;

  private static final SAbstractObject[] noArgs = new SAbstractObject[0];

  public WhileMessageNode(final SSymbol selector,
      final Universe universe, final SMethod condition,
      final SMethod loopBody, final boolean whileTrue) {
    super(selector, universe);
    this.blockMethodCondition = condition;
    this.blockMethodLoopBody  = loopBody;
    this.whileTrue = whileTrue;
  }

  public WhileMessageNode(final WhileMessageNode node) {
    this(node.selector, node.universe, node.blockMethodCondition,
        node.blockMethodLoopBody, node.whileTrue);
  }

  protected boolean isWhileTrue() {
    return whileTrue;
  }

  protected boolean isWhileFalse() {
    return !whileTrue;
  }

  private SAbstractObject executeBlock(final VirtualFrame frame,
      final SMethod blockMethod) {
    SBlock b = universe.newBlock(blockMethod, frame.materialize(), 1);
    return blockMethod.invoke(frame.pack(), b, noArgs);
  }

  public SAbstractObject evalConditionIfNecessary(final VirtualFrame frame, final SAbstractObject receiver) {
    if (blockMethodCondition == null) {
      return receiver;
    } else {
      return executeBlock(frame, blockMethodCondition);
    }
  }

  @Specialization(order = 1, guards = "isWhileTrue")
  public SAbstractObject doWhileTrue(final VirtualFrame frame,
      final SAbstractObject receiver,
      final Object arguments) {
    SAbstractObject rcvr = evalConditionIfNecessary(frame, receiver);
    SClass currentCondClass = classOfReceiver(rcvr, getReceiver());

    while (currentCondClass == universe.trueClass) {
      if (blockMethodLoopBody != null) {
        executeBlock(frame, blockMethodLoopBody);
      }

      SAbstractObject newConditionResult = evalConditionIfNecessary(frame, receiver);
      currentCondClass = classOfReceiver(newConditionResult, getReceiver());
    }

    return universe.nilObject;
  }

  @Specialization(order = 2, guards = "isWhileFalse")
  public SAbstractObject doWhileFalse(final VirtualFrame frame,
      final SAbstractObject receiver,
      final Object arguments) {
    SAbstractObject rcvr = evalConditionIfNecessary(frame, receiver);
    SClass currentCondClass = classOfReceiver(rcvr, getReceiver());

    while (currentCondClass == universe.falseClass) {
      if (blockMethodLoopBody != null) {
        executeBlock(frame, blockMethodLoopBody);
      }

      SAbstractObject newConditionResult = evalConditionIfNecessary(frame, receiver);
      currentCondClass = classOfReceiver(newConditionResult, getReceiver());
    }

    return universe.nilObject;
  }

  public SAbstractObject doGeneric(final VirtualFrame frame,
      final SAbstractObject receiver,
      final Object arguments) {
    if (isWhileTrue()) {
      return doWhileTrue(frame, receiver, arguments);
    } else {
      return doWhileFalse(frame, receiver, arguments);
    }
  }

  @Override
  public ExpressionNode cloneForInlining() {
    return WhileMessageNodeFactory.create(selector, universe,
        blockMethodCondition, blockMethodLoopBody, whileTrue, getReceiver(),
        getArguments());
  }
}
