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
import som.interpreter.nodes.FieldNode.FieldReadNode;
import som.interpreter.nodes.FieldNode.FieldWriteNode;
import som.interpreter.nodes.FieldNodeFactory.FieldReadNodeFactory;
import som.interpreter.nodes.FieldNodeFactory.FieldWriteNodeFactory;
import som.interpreter.nodes.GlobalNode;
import som.interpreter.nodes.KeywordMessageNode;
import som.interpreter.nodes.LocalVariableNode.LocalVariableReadNode;
import som.interpreter.nodes.TernaryMessageNode;
import som.interpreter.nodes.UnaryMessageNode;
import som.interpreter.nodes.literals.BlockNode;
import som.interpreter.nodes.literals.LiteralNode;
import som.vm.Universe;
import som.vmobjects.SObject;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.SourceSection;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;


public class Method extends Invokable {

  private final Universe universe;
  private final LexicalContext outerContext;

  public Method(final SourceSection sourceSection,
                final FrameDescriptor frameDescriptor,
                final ExpressionNode expressions,
                final Universe universe,
                final LexicalContext outerContext) {
    super(sourceSection, frameDescriptor, expressions);
    this.universe     = universe;
    this.outerContext = outerContext;
  }

  @Override
  public Object execute(final VirtualFrame frame) {
    return expressionOrSequence.executeGeneric(frame);
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
    ExpressionNode body = expressionOrSequence.getFirstMethodBodyNode();
    if (body instanceof LiteralNode
        // we can't do the direct inlining for block nodes, because they need a properly initialized frame
        && !(body instanceof BlockNode)) {
      return true;
    } else if (body instanceof GlobalNode) {
      return true;
    } else if (body instanceof FieldReadNode) {
      return true;
    } else if (body instanceof FieldWriteNode &&
        ((FieldWriteNode) body).getValue() instanceof LocalVariableReadNode) {
      return true;
    }
    return false; // TODO: determine "quick" methods based on the AST, just self nodes, just field reads, etc.
  }

  @Override
  public Invokable cloneWithNewLexicalContext(final LexicalContext outerContext) {
    FrameDescriptor inlinedFrameDescriptor = getFrameDescriptor().copy();
    LexicalContext  inlinedContext = new LexicalContext(inlinedFrameDescriptor,
        outerContext);
    ExpressionNode  inlinedBody = Inliner.doInline(getUninitializedBody(),
        inlinedContext);
    return new Method(getSourceSection(), inlinedFrameDescriptor, inlinedBody,
        universe, outerContext);
  }

  @Override
  public ExpressionNode inline(final RootCallTarget inlinableCallTarget, final SSymbol selector) {
    CompilerAsserts.neverPartOfCompilation();

    if (getUninitializedBody().getFirstMethodBodyNode() instanceof FieldReadNode) {
      if (selector.getNumberOfSignatureArguments() != 1) {
        throw new RuntimeException("WTF?");
      }
      FieldReadNode org = (FieldReadNode) getUninitializedBody().getFirstMethodBodyNode();
      FieldReadNode inlined = FieldReadNodeFactory.create(org.getFieldIndex(), null); // self read should never be used because it comes preevaluated to this inlined node from the CachedSendNode
      return new UnaryInlinedExpressionUsingReceiver(selector, universe, inlined, inlinableCallTarget);
    } else if (getUninitializedBody().getFirstMethodBodyNode() instanceof FieldWriteNode
        && ((FieldWriteNode) getUninitializedBody().getFirstMethodBodyNode()).getValue() instanceof LocalVariableReadNode) {
      if (selector.getNumberOfSignatureArguments() != 2) {
        throw new RuntimeException("WTF?");
      }
      FieldWriteNode org = (FieldWriteNode) getUninitializedBody().getFirstMethodBodyNode();
      FieldWriteNode inlined = FieldWriteNodeFactory.create(org.getFieldIndex(), universe, null, null); // self read should never be used because it comes preevaluated to this inlined node from the CachedSendNode
      return new BinaryInlinedExpressionUsingValues(selector, universe, inlined, inlinableCallTarget);
    }

    // We clone the AST, frame descriptors, and slots to facilitate their
    // independent specialization.
    FrameDescriptor inlinedFrameDescriptor = getFrameDescriptor().copy();
    LexicalContext  inlinedContext = new LexicalContext(inlinedFrameDescriptor,
        outerContext);
    ExpressionNode  inlinedBody;
    if (isAlwaysToBeInlined()) {
      inlinedBody = Inliner.doInline(getUninitializedBody().getFirstMethodBodyNode(), inlinedContext);
    } else {
      inlinedBody = Inliner.doInline(getUninitializedBody(), inlinedContext);
    }

    if (isAlwaysToBeInlined()) {
      switch (selector.getNumberOfSignatureArguments()) {
        case 1:
          return new UnaryInlinedExpression(selector,   universe, inlinedBody, inlinableCallTarget);
        case 2:
          return new BinaryInlinedExpression(selector,  universe, inlinedBody, inlinableCallTarget);
        case 3:
          return new TernaryInlinedExpression(selector, universe, inlinedBody, inlinableCallTarget);
        default:
          return new KeywordInlinedExpression(selector, universe, inlinedBody, inlinableCallTarget);
      }
    }

    switch (selector.getNumberOfSignatureArguments()) {
      case 1:
        return new UnaryInlinedMethod(selector,   universe, inlinedBody,
            inlinableCallTarget, inlinedFrameDescriptor);
      case 2:
        return new BinaryInlinedMethod(selector,  universe, inlinedBody,
            inlinableCallTarget, inlinedFrameDescriptor);
      case 3:
        return new TernaryInlinedMethod(selector, universe, inlinedBody,
            inlinableCallTarget, inlinedFrameDescriptor);
      default:
        return new KeywordInlinedMethod(selector, universe, inlinedBody,
            inlinableCallTarget, inlinedFrameDescriptor);
    }
  }

  private static class UnaryInlinedExpression extends UnaryMessageNode {
    @Child protected ExpressionNode expression;

//    private final RootCallTarget originalCallTarget;

    UnaryInlinedExpression(final SSymbol selector, final Universe universe,
        final ExpressionNode body, final RootCallTarget callTarget) {
      super(selector, universe);
      this.expression = adoptChild(body);
//      this.originalCallTarget = callTarget;
    }

    @Override
    public Object executeEvaluated(final VirtualFrame frame, final Object receiver) {
      return expression.executeGeneric(frame);
    }

    @Override
    public Object executeGeneric(final VirtualFrame frame) {
      throw new IllegalStateException("executeGeneric() is not supported for these nodes, they always need to be called from a SendNode.");
    }

//    @Override public RootCallTarget getCallTarget() { return originalCallTarget; }
    @Override public ExpressionNode getReceiver() { return null; }
  }

  private static class UnaryInlinedExpressionUsingReceiver extends UnaryMessageNode {
    @Child protected FieldReadNode expression;

//    private final RootCallTarget originalCallTarget;

    UnaryInlinedExpressionUsingReceiver(final SSymbol selector, final Universe universe,
        final FieldReadNode body, final RootCallTarget callTarget) {
      super(selector, universe);
      this.expression = adoptChild(body);
//      this.originalCallTarget = callTarget;
    }

    @Override
    public Object executeEvaluated(final VirtualFrame frame, final Object receiver) {
      return expression.executeEvaluated((SObject) receiver);
    }

    @Override
    public Object executeGeneric(final VirtualFrame frame) {
      throw new IllegalStateException("executeGeneric() is not supported for these nodes, they always need to be called from a SendNode.");
    }

//    @Override public RootCallTarget getCallTarget() { return originalCallTarget; }
    @Override public ExpressionNode getReceiver()   { return null; }
  }

  private static final class UnaryInlinedMethod extends UnaryInlinedExpression {
    private final FrameDescriptor inlinedFrameDescriptor;

    UnaryInlinedMethod(final SSymbol selector, final Universe universe,
        final ExpressionNode msgBody,
        final RootCallTarget callTarget,
        final FrameDescriptor frameDescriptor) {
      super(selector, universe, msgBody, callTarget);
      inlinedFrameDescriptor = frameDescriptor;
    }

    @Override
    public Object executeEvaluated(final VirtualFrame frame, final Object receiver) {
      UnaryArguments args = new UnaryArguments(receiver);
      VirtualFrame childFrame = Truffle.getRuntime().createVirtualFrame(frame.pack(), args, inlinedFrameDescriptor);
      return expression.executeGeneric(childFrame);
    }
  }

  private static class BinaryInlinedExpression extends BinaryMessageNode {
    @Child protected ExpressionNode expression;

//    private final RootCallTarget originalCallTarget;

    BinaryInlinedExpression(final SSymbol selector, final Universe universe,
        final ExpressionNode body, final RootCallTarget callTarget) {
      super(selector, universe);
      this.expression = adoptChild(body);
//      this.originalCallTarget = callTarget;
    }

    @Override
    public Object executeEvaluated(final VirtualFrame frame, final Object receiver, final Object argument) {
      return expression.executeGeneric(frame);
    }

    @Override
    public Object executeGeneric(final VirtualFrame frame) {
      throw new IllegalStateException("executeGeneric() is not supported for these nodes, they always need to be called from a SendNode.");
    }

//    @Override public RootCallTarget getCallTarget() { return originalCallTarget; }
    @Override public ExpressionNode getReceiver() { return null; }
    @Override public ExpressionNode getArgument() { return null; }
  }

  private static class BinaryInlinedExpressionUsingValues extends BinaryMessageNode {
    @Child protected FieldWriteNode expression;

//    private final RootCallTarget originalCallTarget;

    BinaryInlinedExpressionUsingValues(final SSymbol selector, final Universe universe,
        final FieldWriteNode body, final RootCallTarget callTarget) {
      super(selector, universe);
      this.expression = adoptChild(body);
//      this.originalCallTarget = callTarget;
    }

    @Override
    public Object executeEvaluated(final VirtualFrame frame, final Object receiver, final Object argument) {
      return expression.executeEvaluated(frame, (SObject) receiver, argument);
    }

    @Override
    public Object executeGeneric(final VirtualFrame frame) {
      throw new IllegalStateException("executeGeneric() is not supported for these nodes, they always need to be called from a SendNode.");
    }

//    @Override public RootCallTarget getCallTarget()   { return originalCallTarget; }
    @Override public ExpressionNode getReceiver() { return null; }
    @Override public ExpressionNode getArgument() { return null; }
  }

  private static final class BinaryInlinedMethod extends BinaryInlinedExpression {
    private final FrameDescriptor inlinedFrameDescriptor;

    BinaryInlinedMethod(final SSymbol selector, final Universe universe,
        final ExpressionNode msgBody, final RootCallTarget callTarget,
        final FrameDescriptor frameDescriptor) {
      super(selector, universe, msgBody, callTarget);
      inlinedFrameDescriptor = frameDescriptor;
    }

    @Override
    public Object executeEvaluated(final VirtualFrame frame, final Object receiver, final Object argument) {
      BinaryArguments args = new BinaryArguments(receiver, argument);
      VirtualFrame childFrame = Truffle.getRuntime().createVirtualFrame(frame.pack(), args, inlinedFrameDescriptor);
      return expression.executeGeneric(childFrame);
    }
  }

  private static class TernaryInlinedExpression extends TernaryMessageNode {
    @Child protected ExpressionNode expression;

//    private final RootCallTarget originalCallTarget;

    TernaryInlinedExpression(final SSymbol selector, final Universe universe,
        final ExpressionNode body, final RootCallTarget callTarget) {
      super(selector, universe);
      this.expression = adoptChild(body);
//      this.originalCallTarget = callTarget;
    }

    @Override
    public Object executeEvaluated(final VirtualFrame frame, final Object receiver, final Object firstArg, final Object secondArg) {
      return expression.executeGeneric(frame);
    }

    @Override
    public Object executeGeneric(final VirtualFrame frame) {
      throw new IllegalStateException("executeGeneric() is not supported for these nodes, they always need to be called from a SendNode.");
    }
//    @Override public RootCallTarget getCallTarget()   { return originalCallTarget; }
    @Override public ExpressionNode getReceiver() { return null; }
    @Override public ExpressionNode getFirstArg() { return null; }
    @Override public ExpressionNode getSecondArg() { return null; }
  }

  private static final class TernaryInlinedMethod extends TernaryInlinedExpression {
    private final FrameDescriptor inlinedFrameDescriptor;

    TernaryInlinedMethod(final SSymbol selector, final Universe universe,
        final ExpressionNode msgBody,
        final RootCallTarget callTarget,
        final FrameDescriptor frameDescriptor) {
      super(selector, universe, msgBody, callTarget);
      inlinedFrameDescriptor = frameDescriptor;
    }

    @Override
    public Object executeEvaluated(final VirtualFrame frame, final Object receiver, final Object arg1, final Object arg2) {
      TernaryArguments args = new TernaryArguments(receiver, arg1, arg2);
      VirtualFrame childFrame = Truffle.getRuntime().createVirtualFrame(frame.pack(),
          args, inlinedFrameDescriptor);
      return expression.executeGeneric(childFrame);
    }
  }

  private static class KeywordInlinedExpression extends KeywordMessageNode {
    @Child protected ExpressionNode expression;

//    private final RootCallTarget originalCallTarget;

    KeywordInlinedExpression(final SSymbol selector, final Universe universe,
        final ExpressionNode body, final RootCallTarget callTarget) {
      super(selector, universe);
      this.expression = adoptChild(body);
//      this.originalCallTarget = callTarget;
    }

    @Override
    public Object executeEvaluated(final VirtualFrame frame, final Object receiver, final Object[] arguments) {
      return expression.executeGeneric(frame);
    }

    @Override
    public Object executeGeneric(final VirtualFrame frame) {
      throw new IllegalStateException("executeGeneric() is not supported for these nodes, they always need to be called from a SendNode.");
    }
//    @Override public RootCallTarget getCallTarget() { return originalCallTarget; }
    @Override public ExpressionNode getReceiver()   { return null; }
    @Override public ArgumentEvaluationNode getArguments() { return null; }
  }

  private static final class KeywordInlinedMethod extends KeywordInlinedExpression {
    private final FrameDescriptor inlinedFrameDescriptor;

    KeywordInlinedMethod(final SSymbol selector, final Universe universe,
        final ExpressionNode msgBody,
        final RootCallTarget callTarget,
        final FrameDescriptor frameDescriptor) {
      super(selector, universe, msgBody, callTarget);
      inlinedFrameDescriptor = frameDescriptor;
    }

    @Override
    public Object executeEvaluated(final VirtualFrame frame,
        final Object receiver, final Object[] arguments) {
      KeywordArguments args = new KeywordArguments(receiver, arguments);
      VirtualFrame childFrame = Truffle.getRuntime().createVirtualFrame(frame.pack(),
          args, inlinedFrameDescriptor);
      return expression.executeGeneric(childFrame);
    }
  }
}
