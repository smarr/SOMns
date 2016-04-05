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

import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.nodes.LoopNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.source.SourceSection;

import som.interpreter.LexicalScope.MethodScope;
import som.interpreter.nodes.ExpressionNode;


public class Method extends Invokable {

  private final MethodScope currentMethodScope;

  public Method(final SourceSection sourceSection,
                final ExpressionNode expressions,
                final MethodScope currentLexicalScope,
                final ExpressionNode uninitialized) {
    super(sourceSection, currentLexicalScope.getFrameDescriptor(),
        expressions, uninitialized);
    this.currentMethodScope = currentLexicalScope;
    expressions.markAsRootExpression();
  }

  @Override
  public final String toString() {
    SourceSection ss = getSourceSection();
    final String id = ss == null ? "" : ss.getIdentifier();
    return "Method " + id + "\t@" + Integer.toHexString(hashCode());
  }

  @Override
  public final Invokable cloneWithNewLexicalContext(final MethodScope outerMethodScope) {
    FrameDescriptor inlinedFrameDescriptor = getFrameDescriptor().copy();
    MethodScope     inlinedCurrentScope = new MethodScope(
        inlinedFrameDescriptor, outerMethodScope,
        null /* because we got an enclosing method anyway */);
    ExpressionNode  inlinedBody = SplitterForLexicallyEmbeddedCode.doInline(
        uninitializedBody, inlinedCurrentScope);
    Method clone = new Method(getSourceSection(), inlinedBody,
        inlinedCurrentScope, uninitializedBody);
    inlinedCurrentScope.setMethod(clone);
    return clone;
  }

  public final Invokable cloneAndAdaptToEmbeddedOuterContext(
      final InlinerForLexicallyEmbeddedMethods inliner) {
    MethodScope currentAdaptedScope = new MethodScope(
        getFrameDescriptor().copy(), inliner.getCurrentMethodScope(),
        null /* because we got an enclosing method anyway */);
    ExpressionNode adaptedBody = InlinerAdaptToEmbeddedOuterContext.doInline(
        uninitializedBody, inliner, currentAdaptedScope);
    ExpressionNode uninitAdaptedBody = NodeUtil.cloneNode(adaptedBody);

    Method clone = new Method(getSourceSection(), adaptedBody,
        currentAdaptedScope, uninitAdaptedBody);
    currentAdaptedScope.setMethod(clone);
    return clone;
  }

  public final Invokable cloneAndAdaptToSomeOuterContextBeingEmbedded(
      final InlinerAdaptToEmbeddedOuterContext inliner) {
    MethodScope currentAdaptedScope = new MethodScope(
        getFrameDescriptor().copy(), inliner.getCurrentMethodScope(),
        null /* because we got an enclosing method anyway */);
    ExpressionNode adaptedBody = InlinerAdaptToEmbeddedOuterContext.doInline(
        uninitializedBody, inliner, currentAdaptedScope);
    ExpressionNode uninitAdaptedBody = NodeUtil.cloneNode(adaptedBody);

    Method clone = new Method(getSourceSection(),
        adaptedBody, currentAdaptedScope, uninitAdaptedBody);
    currentAdaptedScope.setMethod(clone);
    return clone;
  }

  @Override
  public final void propagateLoopCountThroughoutMethodScope(final long count) {
    assert count >= 0;
    currentMethodScope.propagateLoopCountThroughoutMethodScope(count);
    LoopNode.reportLoopCount(expressionOrSequence,
        (count > Integer.MAX_VALUE) ? Integer.MAX_VALUE : (int) count);
  }

  @Override
  public final Node deepCopy() {
    return cloneWithNewLexicalContext(currentMethodScope.getOuterMethodScopeOrNull());
  }
}
