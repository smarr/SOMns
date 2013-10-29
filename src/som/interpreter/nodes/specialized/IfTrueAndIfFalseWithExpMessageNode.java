package som.interpreter.nodes.specialized;

import som.interpreter.nodes.AbstractMessageNode;
import som.interpreter.nodes.ExpressionNode;
import som.vm.Universe;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SClass;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Generic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;

/**
 * This is a special case of the #ifTrue: or #ifFalse: message that is used
 * when the argument to the message was not a block but a normal expression.
 *
 * @author smarr
 */
public abstract class IfTrueAndIfFalseWithExpMessageNode extends AbstractMessageNode {
  private final boolean executeIf;

  public IfTrueAndIfFalseWithExpMessageNode(final SSymbol selector,
      final Universe universe, final boolean executeIf) {
    super(selector, universe);
    this.executeIf = executeIf;
  }

  public IfTrueAndIfFalseWithExpMessageNode(final IfTrueAndIfFalseWithExpMessageNode node) {
    this(node.selector, node.universe, node.executeIf);
  }

  public boolean isIfTrue() {
    return executeIf;
  }

  public boolean isIfFalse() {
    return !executeIf;
  }

  @Specialization(order = 10, guards = {"isIfTrue", "isBooleanReceiver"})
  public SAbstractObject doIfTrue(final VirtualFrame frame, final SAbstractObject receiver,
      final Object arguments) {
    SClass rcvrClass = classOfReceiver(receiver, getReceiver());
    if (rcvrClass == universe.trueClass) {
      return ((SAbstractObject[]) arguments)[0];
    } else {
      return universe.nilObject;
    }
  }

  @Specialization(order = 20, guards = {"isIfFalse", "isBooleanReceiver"})
  public SAbstractObject doIfFalse(final VirtualFrame frame, final SAbstractObject receiver,
      final Object arguments) {
    SClass rcvrClass = classOfReceiver(receiver, getReceiver());
    if (rcvrClass == universe.falseClass) {
      return ((SAbstractObject[]) arguments)[0];
    } else {
      return universe.nilObject;
    }
  }

  @Generic
  public SAbstractObject doGeneric(final VirtualFrame frame, final SAbstractObject receiver,
      final Object arguments) {
    if (!isBooleanReceiver(receiver)) {
      return fallbackForNonBoolReceiver(receiver).
          doGeneric(frame, receiver, arguments);
    }
    if (executeIf) {
      return doIfTrue(frame, receiver, arguments);
    } else {
      return doIfFalse(frame, receiver, arguments);
    }
  }

  protected PolymorpicMessageNode fallbackForNonBoolReceiver(final SAbstractObject rcvr) {
    CompilerDirectives.transferToInterpreter();

    SClass currentRcvrClass = classOfReceiver(rcvr, getReceiver());

    // So, it might just be a polymorphic send site.
    PolymorpicMessageNode poly = PolymorpicMessageNodeFactory.create(selector,
        universe, currentRcvrClass, getReceiver(), getArguments());
    return replace(poly, "Receiver wasn't a boolean. " +
        "So, we need to do the actual send.");
  }

  /**
   * @return uninitialized node to allow for specialization
   */
  @Override
  public ExpressionNode cloneForInlining() {
    return IfTrueAndIfFalseWithExpMessageNodeFactory.create(selector, universe,
        executeIf, getReceiver(), getArguments());

  }
}
