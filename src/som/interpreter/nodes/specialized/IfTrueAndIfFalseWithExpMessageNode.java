package som.interpreter.nodes.specialized;

import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.MessageNode;
import som.vm.Universe;
import som.vmobjects.SClass;
import som.vmobjects.SObject;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;

/**
 * This is a special case of the #ifTrue: or #ifFalse: message that is used
 * when the argument to the message was not a block but a normal expression.
 *
 * @author smarr
 */
public class IfTrueAndIfFalseWithExpMessageNode extends MessageNode {
  private final SClass  falseClass;
  private final SClass  trueClass;
  private final boolean executeIf;

  public IfTrueAndIfFalseWithExpMessageNode(final ExpressionNode receiver,
      final ExpressionNode[] arguments, final SSymbol selector,
      final Universe universe, final boolean executeIf) {
    super(receiver, arguments, selector, universe);
    assert arguments != null && arguments.length == 1;
    falseClass     = universe.falseObject.getSOMClass();
    trueClass      = universe.trueObject.getSOMClass();
    this.executeIf = executeIf;
  }

  @Override
  public SObject executeGeneric(final VirtualFrame frame) {
    SObject rcvr   = receiver.executeGeneric(frame);
    SObject result = arguments[0].executeGeneric(frame);

    return evaluateBody(frame, rcvr, result);
  }

  public SObject evaluateBody(final VirtualFrame frame, final SObject rcvr,
      final SObject result) {
    SClass currentRcvrClass = classOfReceiver(rcvr, receiver);

    if ((executeIf  && (currentRcvrClass == trueClass)) ||
        (!executeIf && (currentRcvrClass == falseClass))) {
      return result;
    } else if ((!executeIf && currentRcvrClass == trueClass) ||
               (executeIf  && currentRcvrClass == falseClass)) {
      // this is the case that False>>#ifTrue: or True>>#ifFalse
      return universe.nilObject;
    } else {
      return fallbackForNonBoolReceiver(frame, rcvr, currentRcvrClass, result);
    }
  }

  public SObject fallbackForNonBoolReceiver(final VirtualFrame frame,
      final SObject rcvr, final SClass currentRcvrClass, final SObject result) {
    CompilerDirectives.transferToInterpreter();

    // So, it might just be a polymorphic send site.
    PolymorpicMessageNode poly = new PolymorpicMessageNode(receiver,
        arguments, selector, universe, currentRcvrClass);
    replace(poly, "Receiver wasn't a boolean. So, we need to do the actual send.");
    return doFullSend(frame, rcvr, new SObject[] {result}, currentRcvrClass);
  }

  /**
   * @return uninitialized node to allow for specialization
   */
  @Override
  public ExpressionNode cloneForInlining() {
    return new IfTrueAndIfFalseWithExpMessageNode(receiver, arguments, selector, universe, executeIf);
  }
}
