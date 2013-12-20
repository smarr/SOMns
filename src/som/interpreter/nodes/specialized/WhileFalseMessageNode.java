package som.interpreter.nodes.specialized;

import som.interpreter.Arguments;
import som.interpreter.nodes.messages.BinarySendNode;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SBlock;
import som.vmobjects.SMethod;
import som.vmobjects.SObject;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;


public abstract class WhileFalseMessageNode extends BinarySendNode {
  public WhileFalseMessageNode(final BinarySendNode node) { super(node); }
  public WhileFalseMessageNode(final WhileFalseMessageNode node) { super(node); }

  private Object executeBlock(final VirtualFrame frame,
      final SBlock block) {
    SMethod   method  = block.getMethod();
    Arguments context = block.getContext(); // TODO: test whether the current implementation is correct, or whether it should be the following: Method.getUpvalues(frame);
    SBlock b = universe.newBlock(method, context);
    return method.invoke(frame.pack(), b, universe);
  }

  protected boolean receiverIsFalseObject(final SObject receiver) {
    return receiver == universe.falseObject;
  }

  @Specialization
  public SAbstractObject doWhileFalse(final VirtualFrame frame,
      final SBlock loopCondition, final SBlock loopBody) {
    Object loopConditionResult = executeBlock(frame, loopCondition);

    // TODO: this is a simplification, we don't cover the case receiver isn't a boolean
    while (loopConditionResult == universe.falseObject) {
      executeBlock(frame, loopBody);
      loopConditionResult = executeBlock(frame, loopCondition);
    }

    return universe.nilObject;
  }

  @Specialization(guards = "receiverIsFalseObject")
  public SAbstractObject doWhileFalse(final VirtualFrame frame,
      final SObject loopCondition, final SBlock loopBody) {
    while (true) { // --> while (true), because the condition that the receiver is the falseObject holds
      executeBlock(frame, loopBody);
    }
  }
}
