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
import som.interpreter.nodes.LocalVariableNode;
import som.interpreter.nodes.LocalVariableNode.LocalVariableReadNode;
import som.interpreter.nodes.LocalVariableNode.LocalVariableWriteNode;
import som.interpreter.nodes.LocalVariableNodeFactory.LocalVariableReadNodeFactory;
import som.interpreter.nodes.LocalVariableNodeFactory.LocalVariableWriteNodeFactory;
import som.interpreter.nodes.TernaryMessageNode;
import som.interpreter.nodes.UnaryMessageNode;
import som.interpreter.nodes.literals.BlockNode;
import som.interpreter.nodes.literals.LiteralNode;
import som.vm.Universe;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.SourceSection;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.InlinedCallSite;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.nodes.NodeVisitor;


public class Method extends Invokable {

  private final FrameSlot[] temporarySlots;
  private final int      numUpvalues;
  private final Universe universe;

  public Method(final ExpressionNode expressions,
                final int numArguments,
                final int numUpvalues,
                final FrameSlot[] temporarySlots,
                final FrameDescriptor frameDescriptor,
                final Universe  universe) {
    super(expressions, numArguments, frameDescriptor);
    this.temporarySlots = temporarySlots;
    this.universe       = universe;
    this.numUpvalues    = numUpvalues;
  }

  @Override
  public Object execute(final VirtualFrame frame) {
    initializeFrame(frame);
    return expressionOrSequence.executeGeneric(frame);
  }

  @Override
  public int getNumberOfUpvalues() {
    return numUpvalues;
  }

  @ExplodeLoop
  protected void initializeFrame(final VirtualFrame frame) {
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

  private ExpressionNode prepareBody(final ExpressionNode clonedBody) {
    clonedBody.accept(new NodeVisitor() {
      @Override
      public boolean visit(final Node node) {
        prepareBodyNode(node);
        assert !(node instanceof Method);
        return true;
      }});
    return clonedBody;
  }

  private void prepareBodyNode(final Node node) {
    if (node instanceof LocalVariableNode) {
        LocalVariableNode varNode = (LocalVariableNode) node;
        FrameSlot inlinedSlot = frameDescriptor.findFrameSlot(varNode.getSlotIdentifier());
        assert inlinedSlot != null;

        if (node instanceof LocalVariableReadNode) {
            node.replace(LocalVariableReadNodeFactory.create(inlinedSlot));
        } else if (node instanceof LocalVariableWriteNode) {
            node.replace(LocalVariableWriteNodeFactory.create(inlinedSlot,
                ((LocalVariableWriteNode) varNode).getExp()));
        }
    }
  }

  private FrameSlot[] getInlinedTemporarySlots(final FrameDescriptor descriptor) {
    FrameSlot[] inlined = new FrameSlot[temporarySlots.length];
    for (int i = 0; i < temporarySlots.length; i++) {
      inlined[i] = descriptor.findFrameSlot(temporarySlots[i].getIdentifier());
    }
    return inlined;
  }

  @Override
  public ExpressionNode inline(final CallTarget inlinableCallTarget, final SSymbol selector) {
    // We clone the AST, frame descriptors, and slots to facilitate their
    // independent specialization.
    ExpressionNode  ininedBody = prepareBody(NodeUtil.cloneNode(getUninitializedBody()));
    FrameDescriptor inlinedFrameDescriptor = frameDescriptor.copy();
    FrameSlot[]     inlinedSlots = getInlinedTemporarySlots(inlinedFrameDescriptor);


    if (isAlwaysToBeInlined()) {
      switch (numArguments) {
        case 0:
          return new UnaryInlinedExpression(selector,   universe, ininedBody, inlinableCallTarget);
        case 1:
          return new BinaryInlinedExpression(selector,  universe, ininedBody, inlinableCallTarget);
        case 2:
          return new TernaryInlinedExpression(selector, universe, ininedBody, inlinableCallTarget);
        default:
          return new KeywordInlinedExpression(selector, universe, ininedBody, inlinableCallTarget);
      }
    }

    switch (numArguments) {
      case 0:
        return new UnaryInlinedMethod(selector, universe, ininedBody,
            inlinableCallTarget, numUpvalues, inlinedFrameDescriptor, inlinedSlots);
      case 1:
        return new BinaryInlinedMethod(selector, universe, ininedBody,
            inlinableCallTarget, numUpvalues, inlinedFrameDescriptor, inlinedSlots);
      case 2:
        return new TernaryInlinedMethod(selector, universe, ininedBody,
            inlinableCallTarget, numUpvalues, inlinedFrameDescriptor, inlinedSlots);
      default:
        return new KeywordInlinedMethod(selector, universe, ininedBody,
            inlinableCallTarget, numUpvalues, inlinedFrameDescriptor, inlinedSlots);
    }
  }

  private static class UnaryInlinedExpression extends UnaryMessageNode implements InlinedCallSite {
    @Child protected ExpressionNode expression;

    private final CallTarget callTarget;

    UnaryInlinedExpression(final SSymbol selector, final Universe universe,
        final ExpressionNode body, final CallTarget callTarget) {
      super(selector, universe);
      this.expression = adoptChild(body);
      this.callTarget = callTarget;
    }

    @Override
    public Object executeEvaluated(final VirtualFrame frame, final Object receiver) {
      return expression.executeGeneric(frame);
    }

    @Override
    public Object executeGeneric(final VirtualFrame frame) {
      throw new IllegalStateException("executeGeneric() is not supported for these nodes, they always need to be called from a SendNode.");
    }

    @Override public CallTarget getCallTarget()   { return callTarget; }
    @Override public ExpressionNode getReceiver() { return null; }
  }

  private static final class UnaryInlinedMethod extends UnaryInlinedExpression implements InlinedCallSite {
    private final FrameDescriptor frameDescriptor;
    private final int             numUpvalues;
    private final FrameSlot[]     temporarySlots;

    UnaryInlinedMethod(final SSymbol selector, final Universe universe,
        final ExpressionNode msgBody,
        final CallTarget callTarget,
        final int numUpvalues,
        final FrameDescriptor frameDescriptor,
        final FrameSlot[] temporarySlots) {
      super(selector, universe, msgBody, callTarget);
      this.frameDescriptor      = frameDescriptor;
      this.temporarySlots       = temporarySlots;
      this.numUpvalues          = numUpvalues;
    }

    @ExplodeLoop
    private void initializeFrame(final VirtualFrame frame) {
      for (int i = 0; i < temporarySlots.length; i++) {
        frame.setObject(temporarySlots[i], universe.nilObject);
      }
    }

    @Override
    public Object executeEvaluated(final VirtualFrame frame, final Object receiver) {
      UnaryArguments args = new UnaryArguments(receiver, numUpvalues, universe.nilObject);
      VirtualFrame childFrame = Truffle.getRuntime().createVirtualFrame(frame.pack(), args, frameDescriptor);
      initializeFrame(childFrame);
      return expression.executeGeneric(childFrame);
    }
  }

  private static class BinaryInlinedExpression extends BinaryMessageNode implements InlinedCallSite {
    @Child protected ExpressionNode expression;

    private final CallTarget callTarget;

    BinaryInlinedExpression(final SSymbol selector, final Universe universe,
        final ExpressionNode body, final CallTarget callTarget) {
      super(selector, universe);
      this.expression = adoptChild(body);
      this.callTarget = callTarget;
    }

    @Override
    public Object executeEvaluated(final VirtualFrame frame, final Object receiver, final Object argument) {
      return expression.executeGeneric(frame);
    }

    @Override
    public Object executeGeneric(final VirtualFrame frame) {
      throw new IllegalStateException("executeGeneric() is not supported for these nodes, they always need to be called from a SendNode.");
    }

    @Override public CallTarget getCallTarget()   { return callTarget; }
    @Override public ExpressionNode getReceiver() { return null; }
    @Override public ExpressionNode getArgument() { return null; }
  }

  private static final class BinaryInlinedMethod extends BinaryInlinedExpression implements InlinedCallSite {
    private final FrameDescriptor frameDescriptor;
    private final int             numUpvalues;
    private final FrameSlot[]     temporarySlots;

    BinaryInlinedMethod(final SSymbol selector, final Universe universe,
        final ExpressionNode msgBody, final CallTarget callTarget,
        final int numUpvalues,
        final FrameDescriptor frameDescriptor,
        final FrameSlot[] temporarySlots) {
      super(selector, universe, msgBody, callTarget);
      this.frameDescriptor      = frameDescriptor;
      this.temporarySlots       = temporarySlots;
      this.numUpvalues          = numUpvalues;
    }

    @ExplodeLoop
    private void initializeFrame(final VirtualFrame frame) {
      for (int i = 0; i < temporarySlots.length; i++) {
        frame.setObject(temporarySlots[i], universe.nilObject);
      }
    }

    @Override
    public Object executeEvaluated(final VirtualFrame frame, final Object receiver, final Object argument) {
      BinaryArguments args = new BinaryArguments(receiver, argument, numUpvalues, universe.nilObject);
      VirtualFrame childFrame = Truffle.getRuntime().createVirtualFrame(frame.pack(), args, frameDescriptor);
      initializeFrame(childFrame);
      return expression.executeGeneric(childFrame);
    }
  }

  private static class TernaryInlinedExpression extends TernaryMessageNode implements InlinedCallSite {
    @Child protected ExpressionNode expression;

    private final CallTarget callTarget;

    TernaryInlinedExpression(final SSymbol selector, final Universe universe,
        final ExpressionNode body, final CallTarget callTarget) {
      super(selector, universe);
      this.expression = adoptChild(body);
      this.callTarget = callTarget;
    }

    @Override
    public Object executeEvaluated(final VirtualFrame frame, final Object receiver, final Object firstArg, final Object secondArg) {
      return expression.executeGeneric(frame);
    }

    @Override
    public Object executeGeneric(final VirtualFrame frame) {
      throw new IllegalStateException("executeGeneric() is not supported for these nodes, they always need to be called from a SendNode.");
    }
    @Override public CallTarget getCallTarget()   { return callTarget; }
    @Override public ExpressionNode getReceiver() { return null; }
    @Override public ExpressionNode getFirstArg() { return null; }
    @Override public ExpressionNode getSecondArg() { return null; }
  }

  private static final class TernaryInlinedMethod extends TernaryInlinedExpression implements InlinedCallSite {
    private final FrameDescriptor frameDescriptor;
    private final int             numUpvalues;
    private final FrameSlot[]     temporarySlots;

    TernaryInlinedMethod(final SSymbol selector, final Universe universe,
        final ExpressionNode msgBody,
        final CallTarget callTarget, final int numUpvalues,
        final FrameDescriptor frameDescriptor,
        final FrameSlot[] temporarySlots) {
      super(selector, universe, msgBody, callTarget);
      this.frameDescriptor      = frameDescriptor;
      this.temporarySlots       = temporarySlots;
      this.numUpvalues          = numUpvalues;
    }

    @ExplodeLoop
    private void initializeFrame(final VirtualFrame frame) {
      for (int i = 0; i < temporarySlots.length; i++) {
        frame.setObject(temporarySlots[i], universe.nilObject);
      }
    }

    @Override
    public Object executeEvaluated(final VirtualFrame frame, final Object receiver, final Object arg1, final Object arg2) {
      TernaryArguments args = new TernaryArguments(receiver, arg1, arg2,
          numUpvalues, universe.nilObject);
      VirtualFrame childFrame = Truffle.getRuntime().createVirtualFrame(frame.pack(),
          args, frameDescriptor);
      initializeFrame(childFrame);
      return expression.executeGeneric(childFrame);
    }
  }

  private static class KeywordInlinedExpression extends KeywordMessageNode implements InlinedCallSite {
    @Child protected ExpressionNode expression;

    private final CallTarget callTarget;

    KeywordInlinedExpression(final SSymbol selector, final Universe universe,
        final ExpressionNode body, final CallTarget callTarget) {
      super(selector, universe);
      this.expression = adoptChild(body);
      this.callTarget = callTarget;
    }

    @Override
    public Object executeEvaluated(final VirtualFrame frame, final Object receiver, final Object[] arguments) {
      return expression.executeGeneric(frame);
    }

    @Override
    public Object executeGeneric(final VirtualFrame frame) {
      throw new IllegalStateException("executeGeneric() is not supported for these nodes, they always need to be called from a SendNode.");
    }
    @Override public CallTarget getCallTarget()   { return callTarget; }
    @Override public ExpressionNode getReceiver() { return null; }
    @Override public ArgumentEvaluationNode getArguments() { return null; }
  }

  private static final class KeywordInlinedMethod extends KeywordInlinedExpression implements InlinedCallSite {
    private final FrameDescriptor frameDescriptor;
    private final int             numUpvalues;
    private final FrameSlot[]     temporarySlots;

    KeywordInlinedMethod(final SSymbol selector, final Universe universe,
        final ExpressionNode msgBody,
        final CallTarget callTarget,
        final int numUpvalues,
        final FrameDescriptor frameDescriptor,
        final FrameSlot[] temporarySlots) {
      super(selector, universe, msgBody, callTarget);
      this.frameDescriptor      = frameDescriptor;
      this.temporarySlots       = temporarySlots;
      this.numUpvalues          = numUpvalues;
    }

    @ExplodeLoop
    private void initializeFrame(final VirtualFrame frame) {
      for (int i = 0; i < temporarySlots.length; i++) {
        frame.setObject(temporarySlots[i], universe.nilObject);
      }
    }

    @Override
    public Object executeEvaluated(final VirtualFrame frame,
        final Object receiver, final Object[] arguments) {
      KeywordArguments args = new KeywordArguments(receiver, arguments,
          numUpvalues, universe.nilObject);
      VirtualFrame childFrame = Truffle.getRuntime().createVirtualFrame(frame.pack(),
          args, frameDescriptor);
      initializeFrame(childFrame);
      return expression.executeGeneric(childFrame);
    }
  }
}
