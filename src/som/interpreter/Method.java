/**
 * Copyright (c) 2013 Stefan Marr, stefan.marr@vub.ac.be
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package som.interpreter;

import som.interpreter.Arguments.BinaryArguments;
import som.interpreter.Arguments.KeywordArguments;
import som.interpreter.Arguments.TernaryArguments;
import som.interpreter.Arguments.UnaryArguments;
import som.interpreter.nodes.ArgumentEvaluationNode;
import som.interpreter.nodes.BinaryMessageNode;
import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.GlobalNode.GlobalReadNode;
import som.interpreter.nodes.KeywordMessageNode;
import som.interpreter.nodes.TernaryMessageNode;
import som.interpreter.nodes.UnaryMessageNode;
import som.interpreter.nodes.literals.BlockNode;
import som.interpreter.nodes.literals.LiteralNode;
import som.vm.Universe;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.SourceSection;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.InlinedCallSite;
import com.oracle.truffle.api.nodes.NodeUtil;


public class Method extends Invokable {

  @CompilationFinal private final FrameSlot[] temporarySlots;
  private final Universe universe;

  public Method(final ExpressionNode expressions,
                final FrameSlot[] argumentSlots,
                final FrameSlot[] temporarySlots,
                final FrameDescriptor frameDescriptor,
                final Universe  universe) {
    super(expressions, argumentSlots, frameDescriptor);
    this.temporarySlots       = temporarySlots;
    this.universe             = universe;
  }

  @Override
  public Object execute(final VirtualFrame frame) {
    initializeFrame(frame);
    return messageSendExecution(frame, expressionOrSequence);
  }

  protected static SAbstractObject messageSendExecution(final VirtualFrame frame,
      final ExpressionNode expr) {
    FrameOnStackMarker marker = Arguments.get(frame).getFrameOnStackMarker();
    SAbstractObject result;
    boolean restart;

    do {
      restart = false;
      try {
        result = (SAbstractObject) expr.executeGeneric(frame); // TODO: work out whether there is another way than this cast!
      } catch (ReturnException e) {
        if (!e.reachedTarget(marker)) {
          marker.frameNoLongerOnStack();
          throw e;
        } else {
          result = e.result();
        }
      } catch (RestartLoopException e) {
        restart = true;
        result  = null;
      }
    } while (restart);

    marker.frameNoLongerOnStack();
    return result;
  }

  @ExplodeLoop
  protected void initializeFrame(final VirtualFrame frame) {
    Arguments args = Arguments.get(frame);
    for (int i = 0; i < argumentSlots.length; i++) {
      frame.setObject(argumentSlots[i], args.getArgument(i));
    }

    for (int i = 0; i < temporarySlots.length; i++) {
      frame.setObject(temporarySlots[i], universe.nilObject);
    }
  }


  @Override
  public String toString() {
    SourceSection ss = getSourceSection();
    final String name = ss.getIdentifier();
    final String location = getSourceSection().toString();
    return "Method " + name + ":" + location + "@" + Integer.toHexString(hashCode());
  }

  @Override
  public boolean isAlwaysToBeInlined() {
    if (expressionOrSequence instanceof LiteralNode
        // we can't do the direct inlining for block nodes, because they need a properly initialized frame
        && !(expressionOrSequence instanceof BlockNode)) {
      return true;
    } else if (expressionOrSequence instanceof GlobalReadNode) {
      return true;
    }
    return false; // TODO: determine "quick" methods based on the AST, just self nodes, just field reads, etc.
  }

  @Override
  public ExpressionNode inline(final CallTarget inlinableCallTarget, final SSymbol selector) {
    ExpressionNode body = NodeUtil.cloneNode(getUninitializedBody());
    if (isAlwaysToBeInlined()) {
      switch (argumentSlots.length) {
        case 0:
          return new UnaryInlinedExpression(selector, universe, body, inlinableCallTarget);
        case 1:
          return new BinaryInlinedExpression(selector, universe, body, inlinableCallTarget);
        case 2:
          return new TernaryInlinedExpression(selector, universe, body, inlinableCallTarget);
        default:
          return new KeywordInlinedExpression(selector, universe, body, inlinableCallTarget);
      }
    }

    switch (argumentSlots.length) {
      case 0:
        return new UnaryInlinedMethod(selector, universe, body, null,
            inlinableCallTarget, frameDescriptor, temporarySlots);
      case 1:
        return new BinaryInlinedMethod(selector, universe, body, null, null,
            inlinableCallTarget, frameDescriptor, argumentSlots, temporarySlots);
      case 2:
        return new TernaryInlinedMethod(selector, universe, body, null, null, null,
            inlinableCallTarget, frameDescriptor, argumentSlots, temporarySlots);
      default:
        return new KeywordInlinedMethod(selector, universe, body, null, null,
            inlinableCallTarget, frameDescriptor, argumentSlots, temporarySlots);
    }
  }

  private static final class UnaryInlinedExpression extends UnaryMessageNode implements InlinedCallSite {
    @Child private ExpressionNode expression;

    private final CallTarget callTarget;

    UnaryInlinedExpression(final SSymbol selector, final Universe universe,
        final ExpressionNode body, final CallTarget callTarget) {
      super(selector, universe);
      this.expression = body;
      this.callTarget = callTarget;
    }

    @Override
    public Object executeEvaluated(final VirtualFrame frame, final Object receiver) {
      return expression.executeGeneric(frame);
    }

    @Override public Object executeGeneric(final VirtualFrame frame) { return executeEvaluated(frame, null); }
    @Override public CallTarget getCallTarget()   { return callTarget; }
    @Override public ExpressionNode getReceiver() { return null; }
  }

  private static final class UnaryInlinedMethod extends UnaryMessageNode implements InlinedCallSite {
    @Child private ExpressionNode expressionOrSequence;
    @Child private ExpressionNode receiver;

    private final CallTarget      callTarget;
    private final FrameDescriptor frameDescriptor;

    @CompilationFinal private final FrameSlot[] temporarySlots;

    UnaryInlinedMethod(final SSymbol selector, final Universe universe,
        final ExpressionNode msgBody, final ExpressionNode receiver,
        final CallTarget callTarget, final FrameDescriptor frameDescriptor,
        final FrameSlot[] temporarySlots) {
      super(selector, universe);
      this.expressionOrSequence = adoptChild(msgBody);
      this.callTarget           = callTarget;
      this.frameDescriptor      = frameDescriptor;
      this.temporarySlots       = temporarySlots;
    }

    @ExplodeLoop
    private void initializeFrame(final VirtualFrame frame) {
      for (int i = 0; i < temporarySlots.length; i++) {
        frame.setObject(temporarySlots[i], universe.nilObject);
      }
    }

    @Override
    public Object executeGeneric(final VirtualFrame frame) {
      Object rcvr = receiver.executeGeneric(frame);
      return executeEvaluated(frame, rcvr);
    }

    @Override
    public Object executeEvaluated(final VirtualFrame frame, final Object receiver) {
      UnaryArguments args = new UnaryArguments((SAbstractObject) receiver);
      VirtualFrame childFrame = Truffle.getRuntime().createVirtualFrame(frame.pack(), args, frameDescriptor);
      initializeFrame(childFrame);
      return messageSendExecution(childFrame, expressionOrSequence);
    }

    @Override
    public CallTarget getCallTarget() {
      return callTarget;
    }

    @Override
    public ExpressionNode getReceiver() {
      return receiver;
    }
  }

  private static final class BinaryInlinedExpression extends BinaryMessageNode implements InlinedCallSite {
    @Child private ExpressionNode expression;

    private final CallTarget callTarget;

    BinaryInlinedExpression(final SSymbol selector, final Universe universe,
        final ExpressionNode body, final CallTarget callTarget) {
      super(selector, universe);
      this.expression = body;
      this.callTarget = callTarget;
    }

    @Override
    public Object executeEvaluated(final VirtualFrame frame, final Object receiver, final Object argument) {
      return expression.executeGeneric(frame);
    }

    @Override public Object executeGeneric(final VirtualFrame frame) { return executeEvaluated(frame, null, null); }
    @Override public CallTarget getCallTarget()   { return callTarget; }
    @Override public ExpressionNode getReceiver() { return null; }
    @Override public ExpressionNode getArgument() { return null; }
  }

  private static final class BinaryInlinedMethod extends BinaryMessageNode implements InlinedCallSite {
    @Child private ExpressionNode expressionOrSequence;
    @Child private ExpressionNode receiver;
    @Child private ExpressionNode argument;

    private final CallTarget      callTarget;
    private final FrameDescriptor frameDescriptor;

    @CompilationFinal private final FrameSlot[] argumentSlots;
    @CompilationFinal private final FrameSlot[] temporarySlots;

    BinaryInlinedMethod(final SSymbol selector, final Universe universe,
        final ExpressionNode msgBody, final ExpressionNode receiver,
        final ExpressionNode argument, final CallTarget callTarget,
        final FrameDescriptor frameDescriptor,
        final FrameSlot[] argumentSlots,
        final FrameSlot[] temporarySlots) {
      super(selector, universe);
      this.expressionOrSequence = adoptChild(msgBody);
      this.callTarget           = callTarget;
      this.frameDescriptor      = frameDescriptor;
      this.argumentSlots        = argumentSlots;
      this.temporarySlots       = temporarySlots;
      this.argument             = argument;
    }

    @ExplodeLoop
    private void initializeFrame(final VirtualFrame frame) {
      Arguments args = Arguments.get(frame);
      frame.setObject(argumentSlots[0], args.getArgument(0));

      for (int i = 0; i < temporarySlots.length; i++) {
        frame.setObject(temporarySlots[i], universe.nilObject);
      }
    }

    @Override
    public Object executeGeneric(final VirtualFrame frame) {
      Object rcvr = receiver.executeGeneric(frame);
      Object arg  = argument.executeGeneric(frame);
      return executeEvaluated(frame, rcvr, arg);
    }

    @Override
    public Object executeEvaluated(final VirtualFrame frame, final Object receiver, final Object argument) {
      BinaryArguments args = new BinaryArguments((SAbstractObject) receiver, (SAbstractObject) argument);
      VirtualFrame childFrame = Truffle.getRuntime().createVirtualFrame(frame.pack(), args, frameDescriptor);
      initializeFrame(childFrame);
      return messageSendExecution(childFrame, expressionOrSequence);
    }

    @Override
    public CallTarget getCallTarget() {
      return callTarget;
    }

    @Override
    public ExpressionNode getReceiver() {
      return receiver;
    }

    @Override
    public ExpressionNode getArgument() {
      return argument;
    }
  }

  private static final class TernaryInlinedExpression extends TernaryMessageNode implements InlinedCallSite {
    @Child private ExpressionNode expression;

    private final CallTarget callTarget;

    TernaryInlinedExpression(final SSymbol selector, final Universe universe,
        final ExpressionNode body, final CallTarget callTarget) {
      super(selector, universe);
      this.expression = body;
      this.callTarget = callTarget;
    }

    @Override
    public Object executeEvaluated(final VirtualFrame frame, final Object receiver, final Object firstArg, final Object secondArg) {
      return expression.executeGeneric(frame);
    }

    @Override public Object executeGeneric(final VirtualFrame frame) { return executeEvaluated(frame, null, null, null); }
    @Override public CallTarget getCallTarget()   { return callTarget; }
    @Override public ExpressionNode getReceiver() { return null; }
    @Override public ExpressionNode getFirstArg() { return null; }
    @Override public ExpressionNode getSecondArg() { return null; }
  }

  private static final class TernaryInlinedMethod extends TernaryMessageNode implements InlinedCallSite {
    @Child private ExpressionNode expressionOrSequence;
    @Child private ExpressionNode receiver;
    @Child private ExpressionNode firstArg;
    @Child private ExpressionNode secondArg;

    private final CallTarget      callTarget;
    private final FrameDescriptor frameDescriptor;

    @CompilationFinal private final FrameSlot[] argumentSlots;
    @CompilationFinal private final FrameSlot[] temporarySlots;

    TernaryInlinedMethod(final SSymbol selector, final Universe universe,
        final ExpressionNode msgBody, final ExpressionNode receiver,
        final ExpressionNode firstArg, final ExpressionNode secondArg,
        final CallTarget callTarget, final FrameDescriptor frameDescriptor,
        final FrameSlot[] argumentSlots,
        final FrameSlot[] temporarySlots) {
      super(selector, universe);
      this.expressionOrSequence = adoptChild(msgBody);
      this.callTarget           = callTarget;
      this.frameDescriptor      = frameDescriptor;
      this.argumentSlots        = argumentSlots;
      this.temporarySlots       = temporarySlots;
      this.firstArg             = firstArg;
      this.secondArg            = secondArg;
    }

    @ExplodeLoop
    private void initializeFrame(final VirtualFrame frame) {
      Arguments args = Arguments.get(frame);
      frame.setObject(argumentSlots[0], args.getArgument(0));
      frame.setObject(argumentSlots[1], args.getArgument(1));

      for (int i = 0; i < temporarySlots.length; i++) {
        frame.setObject(temporarySlots[i], universe.nilObject);
      }
    }

    @Override
    public Object executeGeneric(final VirtualFrame frame) {
      Object rcvr = receiver.executeGeneric(frame);
      Object arg1 = firstArg.executeGeneric(frame);
      Object arg2 = secondArg.executeGeneric(frame);
      return executeEvaluated(frame, rcvr, arg1, arg2);
    }

    @Override
    public Object executeEvaluated(final VirtualFrame frame, final Object receiver, final Object arg1, final Object arg2) {
      TernaryArguments args = new TernaryArguments((SAbstractObject) receiver, (SAbstractObject) arg1, (SAbstractObject) arg2);
      VirtualFrame childFrame = Truffle.getRuntime().createVirtualFrame(frame.pack(), args, frameDescriptor);
      initializeFrame(childFrame);
      return messageSendExecution(childFrame, expressionOrSequence);
    }

    @Override
    public CallTarget getCallTarget() {
      return callTarget;
    }

    @Override
    public ExpressionNode getReceiver() {
      return receiver;
    }

    @Override
    public ExpressionNode getFirstArg() {
      return firstArg;
    }

    @Override
    public ExpressionNode getSecondArg() {
      return secondArg;
    }
  }

  private static final class KeywordInlinedExpression extends KeywordMessageNode implements InlinedCallSite {
    @Child private ExpressionNode expression;

    private final CallTarget callTarget;

    KeywordInlinedExpression(final SSymbol selector, final Universe universe,
        final ExpressionNode body, final CallTarget callTarget) {
      super(selector, universe);
      this.expression = body;
      this.callTarget = callTarget;
    }

    @Override
    public Object executeEvaluated(final VirtualFrame frame, final Object receiver, final Object[] arguments) {
      return expression.executeGeneric(frame);
    }

    @Override public Object executeGeneric(final VirtualFrame frame) { return executeEvaluated(frame, null, null); }
    @Override public CallTarget getCallTarget()   { return callTarget; }
    @Override public ExpressionNode getReceiver() { return null; }
    @Override public ArgumentEvaluationNode getArguments() { return null; }
  }

  private static final class KeywordInlinedMethod extends KeywordMessageNode implements InlinedCallSite {
    @Child    private ExpressionNode   expressionOrSequence;
    @Child    private ExpressionNode   receiver;
    @Children private ExpressionNode[] arguments;

    private final CallTarget      callTarget;
    private final FrameDescriptor frameDescriptor;

    @CompilationFinal private final FrameSlot[] argumentSlots;
    @CompilationFinal private final FrameSlot[] temporarySlots;

    KeywordInlinedMethod(final SSymbol selector, final Universe universe,
        final ExpressionNode msgBody, final ExpressionNode receiver,
        final ExpressionNode[] arguments,
        final CallTarget callTarget, final FrameDescriptor frameDescriptor,
        final FrameSlot[] argumentSlots,
        final FrameSlot[] temporarySlots) {
      super(selector, universe);
      this.expressionOrSequence = adoptChild(msgBody);
      this.callTarget           = callTarget;
      this.frameDescriptor      = frameDescriptor;
      this.argumentSlots        = argumentSlots;
      this.temporarySlots       = temporarySlots;
      this.arguments            = arguments;
    }

    @ExplodeLoop
    private void initializeFrame(final VirtualFrame frame) {
      Arguments args = Arguments.get(frame);
      for (int i = 0; i < argumentSlots.length; i++) {
        frame.setObject(argumentSlots[i], args.getArgument(i));
      }

      for (int i = 0; i < temporarySlots.length; i++) {
        frame.setObject(temporarySlots[i], universe.nilObject);
      }
    }

    @ExplodeLoop
    @Override
    public Object executeGeneric(final VirtualFrame frame) {
      Object rcvr = receiver.executeGeneric(frame);

      Object[] args = new Object[arguments.length];
      for (int i = 0; i < arguments.length; i++) {
        args[i] = arguments[i].executeGeneric(frame);
      }
      return executeEvaluated(frame, rcvr, args);
    }

    @Override
    public Object executeEvaluated(final VirtualFrame frame, final Object receiver, final Object[] arguments) {
      KeywordArguments args = new KeywordArguments((SAbstractObject) receiver, (SAbstractObject[]) arguments);
      VirtualFrame childFrame = Truffle.getRuntime().createVirtualFrame(frame.pack(), args, frameDescriptor);
      initializeFrame(childFrame);
      return messageSendExecution(childFrame, expressionOrSequence);
    }

    @Override
    public CallTarget getCallTarget() {
      return callTarget;
    }

    @Override
    public ExpressionNode getReceiver() {
      return receiver;
    }

    @Override
    public ArgumentEvaluationNode getArguments() {
      return new ArgumentEvaluationNode(arguments);
    }
  }
}
