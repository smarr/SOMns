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

public class IfTrueAndIfFalseMessageNode extends MessageNode {
  private final SClass falseClass;
  private final SClass trueClass;
  private final SMethod blockMethod;
  private final boolean executeIf;
  private final SObject[] noArgs;

  public IfTrueAndIfFalseMessageNode(final ExpressionNode receiver,
      final ExpressionNode[] arguments, final SSymbol selector,
      final Universe universe, final SBlock block, boolean executeIf) {
    super(receiver, arguments, selector, universe);
    falseClass     = universe.falseObject.getSOMClass();
    trueClass      = universe.trueObject.getSOMClass();
    blockMethod    = block.getMethod();
    this.executeIf = executeIf;
    noArgs = new SObject[0];
  }

  @Override
  public SObject executeGeneric(final VirtualFrame frame) {
    // determine receiver, determine arguments is not necessary, because
    // the node is specialized only when the argument is a literal node
    SObject rcvr = receiver.executeGeneric(frame);

    return evaluateBody(frame, rcvr);
  }

  public SObject evaluateBody(final VirtualFrame frame, SObject rcvr) {
    SClass currentRcvrClass = classOfReceiver(rcvr, receiver);

    if ((executeIf  && (currentRcvrClass == trueClass)) ||
        (!executeIf && (currentRcvrClass == falseClass))) {
      SBlock b = universe.newBlock(blockMethod, frame.materialize(), 1);
      // this is the case True>>#ifTrue: or False>>#ifFalse
      return blockMethod.invoke(frame.pack(), b, noArgs);
    } else if ((!executeIf && currentRcvrClass == trueClass) ||
               (executeIf  && currentRcvrClass == falseClass)) {
      // this is the case that False>>#ifTrue: or True>>#ifFalse
      return universe.nilObject;
    } else {
      return fallbackForNonBoolReceiver(frame, rcvr, currentRcvrClass);
    }
  }

  public SObject fallbackForNonBoolReceiver(final VirtualFrame frame,
      SObject rcvr, SClass currentRcvrClass) {
    CompilerDirectives.transferToInterpreter();

    // So, it might just be a polymorphic send site.
    PolymorpicMessageNode poly = new PolymorpicMessageNode(receiver,
        arguments, selector, universe, currentRcvrClass);
    SBlock b = universe.newBlock(blockMethod, frame.materialize(), 1);
    replace(poly, "Receiver wasn't a boolean. So, we need to do the actual send.");
    return doFullSend(frame, rcvr, new SObject[] {b}, currentRcvrClass);
  }
}
