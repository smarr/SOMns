package som.interpreter.nodes.specialized;

import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.MessageNode;
import som.vm.Universe;
import som.vmobjects.SBlock;
import som.vmobjects.SClass;
import som.vmobjects.SMethod;
import som.vmobjects.SObject;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;


public class IfTrueIfFalseMessageNode extends MessageNode {

  private final SClass falseClass;
  private final SClass trueClass;

  private final SMethod blockMethodTrueBranch;
  private final SMethod blockMethodFalseBranch;

  private final SObject[] noArgs;

  public IfTrueIfFalseMessageNode(final ExpressionNode receiver,
      final ExpressionNode[] arguments, final SSymbol selector, final Universe universe,
      final SBlock trueBlock, final SBlock falseBlock) {
    this(receiver, arguments, selector, universe,
        (trueBlock  != null) ? trueBlock.getMethod()  : null,
        (falseBlock != null) ? falseBlock.getMethod() : null);
    assert arguments != null && arguments.length == 2;
  }

  public IfTrueIfFalseMessageNode(final ExpressionNode receiver,
      final ExpressionNode[] arguments, final SSymbol selector,
      final Universe universe,
      final SMethod trueBlockMethod, final SMethod falseBlockMethod) {
    super(receiver, arguments, selector, universe);
    falseClass = universe.falseObject.getSOMClass();
    trueClass  = universe.trueObject.getSOMClass();

    blockMethodTrueBranch  = trueBlockMethod;
    blockMethodFalseBranch = falseBlockMethod;

    noArgs = new SObject[0];
  }

  @Override
  public SObject executeGeneric(final VirtualFrame frame) {
    // determine receiver, determine arguments is not necessary, because
    // the node is specialized only when  the arguments are literal nodes
    SObject rcvr = receiver.executeGeneric(frame);

    SObject trueExpResult  = null;
    SObject falseExpResult = null;

    if (blockMethodTrueBranch == null) {
      trueExpResult = arguments[0].executeGeneric(frame);
    }
    if (blockMethodFalseBranch == null) {
      falseExpResult = arguments[1].executeGeneric(frame);
    }

    return evaluateBody(frame, rcvr, trueExpResult, falseExpResult);
  }

  public SObject evaluateBody(final VirtualFrame frame, final SObject rcvr,
      final SObject trueResult, final SObject falseResult) {
    SClass currentRcvrClass = classOfReceiver(rcvr, receiver);

    if (currentRcvrClass == trueClass) {
      if (blockMethodTrueBranch == null) {
        return trueResult;
      } else {
        SBlock b = universe.newBlock(blockMethodTrueBranch, frame.materialize(), 1);
        return blockMethodTrueBranch.invoke(frame.pack(), b, noArgs);
      }
    } else if (currentRcvrClass == falseClass) {
      if (blockMethodFalseBranch == null) {
        return falseResult;
      } else {
        SBlock b = universe.newBlock(blockMethodFalseBranch, frame.materialize(), 1);
        return blockMethodFalseBranch.invoke(frame.pack(), b, noArgs);
      }
    } else {
      return fallbackForNonBoolReceiver(frame, rcvr, currentRcvrClass);
    }
  }

  private SObject fallbackForNonBoolReceiver(final VirtualFrame frame,
      final SObject rcvr, final SClass currentRcvrClass) {
    CompilerDirectives.transferToInterpreter();

    // So, it might just be a polymorphic send site.
    PolymorpicMessageNode poly = new PolymorpicMessageNode(receiver,
        arguments, selector, universe, currentRcvrClass);
    SBlock trueBlock  = universe.newBlock(blockMethodTrueBranch,  frame.materialize(), 1);
    SBlock falseBlock = universe.newBlock(blockMethodFalseBranch, frame.materialize(), 1);
    replace(poly, "Receiver wasn't a boolean. So, we need to do the actual send.");
    return doFullSend(frame, rcvr, new SObject[] {trueBlock, falseBlock}, currentRcvrClass);
  }

  @Override
  public ExpressionNode cloneForInlining() {
    return new IfTrueIfFalseMessageNode(receiver, arguments, selector,
        universe, blockMethodTrueBranch, blockMethodFalseBranch);
  }
}
