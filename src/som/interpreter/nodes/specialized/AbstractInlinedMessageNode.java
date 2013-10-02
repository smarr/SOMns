package som.interpreter.nodes.specialized;

import som.interpreter.Arguments;
import som.interpreter.FrameOnStackMarker;
import som.interpreter.Method;
import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.MessageNode;
import som.vm.Universe;
import som.vmobjects.SClass;
import som.vmobjects.SInvokable;
import som.vmobjects.SObject;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.FrameFactory;

public class AbstractInlinedMessageNode extends MessageNode {

  private   final SClass      rcvrClass;
  protected final SInvokable  invokable;

  @Child private ExpressionNode methodBody;

  private final FrameFactory frameFactory;
  private final Method inlinedMethod;

  public AbstractInlinedMessageNode(final ExpressionNode receiver,
      final ExpressionNode[] arguments, final SSymbol selector,
      final Universe universe,
      final SClass rcvrClass,
      final SInvokable invokable,
      final FrameFactory frameFactory,
      final Method inlinedMethod,
      final ExpressionNode methodBody) {
    super(receiver, arguments, selector, universe);
    this.rcvrClass = rcvrClass;
    this.invokable = invokable;
    this.methodBody = adoptChild(methodBody);
    this.frameFactory = frameFactory;
    this.inlinedMethod = inlinedMethod;
  }

  @Override
  public SObject executeGeneric(final VirtualFrame frame) {
    // evaluate all the expressions: first determine receiver
    SObject rcvr = receiver.executeGeneric(frame);

    // then determine the arguments
    SObject[] args = determineArguments(frame);

    SClass currentRcvrClass = classOfReceiver(rcvr, receiver);

    if (currentRcvrClass == rcvrClass) {
      return executeInlined(frame, rcvr, args);
    } else {
      return generalizeNode(frame, rcvr, args, currentRcvrClass);
    }
  }

  private SObject executeInlined(final VirtualFrame caller, final SObject rcvr,
      final SObject[] args) {
    // CompilerDirectives.transferToInterpreter();
    final VirtualFrame frame = frameFactory.create(
        inlinedMethod.getFrameDescriptor(), caller.pack(),
        new Arguments(rcvr, args));

    final FrameOnStackMarker marker = Method.initializeFrame(inlinedMethod, frame);

    return Method.messageSendExecution(marker, frame, methodBody);
  }

  private SObject generalizeNode(final VirtualFrame frame, final SObject rcvr,
      final SObject[] args, final SClass currentRcvrClass) {
    CompilerDirectives.transferToInterpreter();
    // So, it might just be a polymorphic send site.
    PolymorpicMessageNode poly = new PolymorpicMessageNode(receiver,
        arguments, selector, universe, rcvrClass, invokable, currentRcvrClass);

    replace(poly, "It is not a monomorpic send.");
    return doFullSend(frame, rcvr, args, currentRcvrClass);
  }
}
