package som.interpreter.nodes.specialized;

import som.interpreter.Arguments;
import som.interpreter.FrameOnStackMarker;
import som.interpreter.Method;
import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.MessageNode;
import som.vm.Universe;
import som.vmobjects.Class;
import som.vmobjects.Invokable;
import som.vmobjects.Object;
import som.vmobjects.Symbol;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.FrameFactory;

public class AbstractInlinedMessageNode extends MessageNode {

  private final Class      rcvrClass;
  protected final Invokable  invokable;

  @Child private final ExpressionNode methodBody;

  private final FrameFactory frameFactory;
  private final Method inlinedMethod;

  public AbstractInlinedMessageNode(final ExpressionNode receiver,
      final ExpressionNode[] arguments, final Symbol selector,
      final Universe universe,
      final Class rcvrClass,
      final Invokable invokable,
      final FrameFactory frameFactory,
      final Method inlinedMethod,
      final ExpressionNode methodBody) {
    super(receiver, arguments, selector, universe);
    this.rcvrClass = rcvrClass;
    this.invokable = invokable;
    this.methodBody = methodBody;
    this.frameFactory = frameFactory;
    this.inlinedMethod = inlinedMethod;
  }

  @Override
  public Object executeGeneric(final VirtualFrame frame) {
    // evaluate all the expressions: first determine receiver
    Object rcvr = receiver.executeGeneric(frame);

    // then determine the arguments
    Object[] args = determineArguments(frame);

    Class currentRcvrClass = classOfReceiver(rcvr, receiver);

    if (currentRcvrClass == rcvrClass) {
      return executeInlined(frame, rcvr, args);
    } else {
      return generalizeNode(frame, rcvr, args, currentRcvrClass);
    }
  }

  private Object executeInlined(final VirtualFrame caller, final Object rcvr,
      final Object[] args) {
    // CompilerDirectives.transferToInterpreter();
    final VirtualFrame frame = frameFactory.create(
        inlinedMethod.getFrameDescriptor(), caller.pack(),
        new Arguments(rcvr, args));

    final FrameOnStackMarker marker = Method.initializeFrame(inlinedMethod,
        frame.materialize());

    return Method.messageSendExecution(marker, frame, methodBody);
  }

  private Object generalizeNode(final VirtualFrame frame, final Object rcvr,
      final Object[] args, final Class currentRcvrClass) {
    CompilerDirectives.transferToInterpreter();
    // So, it might just be a polymorphic send site.
    PolymorpicMessageNode poly = new PolymorpicMessageNode(receiver,
        arguments, selector, universe, rcvrClass, invokable, currentRcvrClass);

    replace(poly, "It is not a monomorpic send.");
    return doFullSend(frame, rcvr, args, currentRcvrClass);
  }
}
