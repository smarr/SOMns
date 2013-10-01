package som.interpreter.nodes.specialized;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;

import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.MessageNode;
import som.vm.Universe;
import som.vmobjects.SBlock;
import som.vmobjects.SClass;
import som.vmobjects.SMethod;
import som.vmobjects.SObject;
import som.vmobjects.SSymbol;


public class IfTrueIfFalseMessageNode extends MessageNode {

  private final SClass falseClass;
  private final SClass trueClass;

  private final SMethod blockMethodTrueBranch;
  private final SMethod blockMethodFalseBranch;

  private final SObject[] noArgs;

  public IfTrueIfFalseMessageNode(ExpressionNode receiver,
      ExpressionNode[] arguments, SSymbol selector, Universe universe,
      SBlock trueBlock, SBlock falseBlock) {
    super(receiver, arguments, selector, universe);
    falseClass     = universe.falseObject.getSOMClass();
    trueClass      = universe.trueObject.getSOMClass();

    blockMethodTrueBranch  = trueBlock.getMethod();
    blockMethodFalseBranch = falseBlock.getMethod();

    noArgs = new SObject[0];
  }

  @Override
  public SObject executeGeneric(final VirtualFrame frame) {
    // determine receiver, determine arguments is not necessary, because
    // the node is specialized only when  the arguments are literal nodes
    SObject rcvr = receiver.executeGeneric(frame);

    return evaluateBody(frame, rcvr);
  }

  public SObject evaluateBody(final VirtualFrame frame, SObject rcvr) {
    SClass currentRcvrClass = classOfReceiver(rcvr, receiver);

    if (currentRcvrClass == trueClass) {
      SBlock b = universe.newBlock(blockMethodTrueBranch, frame.materialize(), 1);
      return blockMethodTrueBranch.invoke(frame.pack(), b, noArgs);
    } else if (currentRcvrClass == falseClass) {
      SBlock b = universe.newBlock(blockMethodFalseBranch, frame.materialize(), 1);
      return blockMethodFalseBranch.invoke(frame.pack(), b, noArgs);
    } else {
      return fallbackForNonBoolReceiver(frame, rcvr, currentRcvrClass);
    }
  }

  private SObject fallbackForNonBoolReceiver(final VirtualFrame frame,
      SObject rcvr, SClass currentRcvrClass) {
    CompilerDirectives.transferToInterpreter();

    // So, it might just be a polymorphic send site.
    PolymorpicMessageNode poly = new PolymorpicMessageNode(receiver,
        arguments, selector, universe, currentRcvrClass);
    SBlock trueBlock  = universe.newBlock(blockMethodTrueBranch,  frame.materialize(), 1);
    SBlock falseBlock = universe.newBlock(blockMethodFalseBranch, frame.materialize(), 1);
    replace(poly, "Receiver wasn't a boolean. So, we need to do the actual send.");
    return doFullSend(frame, rcvr, new SObject[] {trueBlock, falseBlock}, currentRcvrClass);
  }
}
