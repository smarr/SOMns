package som.interpreter.nodes.specialized;

import som.interpreter.Invokable;
import som.interpreter.nodes.AbstractMessageNode;
import som.interpreter.nodes.ExpressionNode;
import som.vm.Universe;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SClass;
import som.vmobjects.SMethod;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.nodes.FrameFactory;

public abstract class AbstractInlinedMessageNode extends AbstractMessageNode {

  protected final SClass  rcvrClass;
  protected final SMethod invokable;

  @Child protected ExpressionNode methodBody;

  protected final FrameFactory frameFactory;
  protected final Invokable    inlinedMethod;

  public AbstractInlinedMessageNode(final SSymbol selector,
      final Universe universe,
      final SClass rcvrClass,
      final SMethod invokable,
      final FrameFactory frameFactory,
      final Invokable inlinedMethod,
      final ExpressionNode methodBody) {
    super(selector, universe);
    this.rcvrClass = rcvrClass;
    this.invokable = invokable;
    this.methodBody = adoptChild(methodBody);
    this.frameFactory = frameFactory;
    this.inlinedMethod = inlinedMethod;
  }

  public AbstractInlinedMessageNode(final AbstractInlinedMessageNode node) {
    this(node.selector, node.universe, node.rcvrClass, node.invokable,
        node.frameFactory, node.inlinedMethod, node.methodBody);
  }

  public boolean isCachedReceiverClass(final SAbstractObject receiver) {
    SClass currentRcvrClass = classOfReceiver(receiver, getReceiver());
    return currentRcvrClass == rcvrClass;
  }

  protected PolymorpicMessageNode generalizeNode(final SClass currentRcvrClass) {
    CompilerDirectives.transferToInterpreter();
    // So, it might just be a polymorphic send site.
    PolymorpicMessageNode poly = PolymorpicMessageNodeFactory.create(selector,
        universe, currentRcvrClass, getReceiver(), getArguments());
    return replace(poly, "It is not a monomorpic send.");
  }
}
