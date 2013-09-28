package som.interpreter.nodes.specialized;

import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.MessageNode;
import som.vm.Universe;
import som.vmobjects.Block;
import som.vmobjects.Class;
import som.vmobjects.Method;
import som.vmobjects.Object;
import som.vmobjects.Symbol;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;

public class IfTrueAndIfFalseMessageNode extends MessageNode {
  private final Class falseClass;
  private final Class trueClass;
  private final Method blockMethod;
  private final boolean executeIf;
  private final Object[] noArgs;

  public IfTrueAndIfFalseMessageNode(final ExpressionNode receiver,
      final ExpressionNode[] arguments, final Symbol selector,
      final Universe universe, final Block block, boolean executeIf) {
    super(receiver, arguments, selector, universe);
    falseClass     = universe.falseObject.getSOMClass();
    trueClass      = universe.trueObject.getSOMClass();
    blockMethod    = block.getMethod();
    this.executeIf = executeIf;
    noArgs = new Object[0];
  }

  @Override
  public Object executeGeneric(final VirtualFrame frame) {
    // determine receiver, determine arguments is not necessary, because
    // the node is specialized only when the argument is a literal node
    Object rcvr = receiver.executeGeneric(frame);

    return evaluateBody(frame, rcvr);
  }

  public Object evaluateBody(final VirtualFrame frame, Object rcvr) {
    Class currentRcvrClass = classOfReceiver(rcvr, receiver);

    if ((executeIf  && (currentRcvrClass == trueClass)) ||
        (!executeIf && (currentRcvrClass == falseClass))) {
      Block b = universe.newBlock(blockMethod, frame.materialize(), 1);
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

  public Object fallbackForNonBoolReceiver(final VirtualFrame frame,
      Object rcvr, Class currentRcvrClass) {
    CompilerDirectives.transferToInterpreter();

    // So, it might just be a polymorphic send site.
    PolymorpicMessageNode poly = new PolymorpicMessageNode(receiver,
        arguments, selector, universe, currentRcvrClass);
    Block b = universe.newBlock(blockMethod, frame.materialize(), 1);
    replace(poly, "Receiver wasn't a boolean. So, we need to do the actual send.");
    return doFullSend(frame, rcvr, new Object[] {b}, currentRcvrClass);
  }
}
