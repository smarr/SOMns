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

import som.interpreter.nodes.ExpressionNode;

import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.SourceSection;


public final class Method extends Invokable {

  private final LexicalContext outerContext;

  public Method(final SourceSection sourceSection,
                final FrameDescriptor frameDescriptor,
                final ExpressionNode expressions,
                final LexicalContext outerContext,
                final ExpressionNode uninitialized) {
    super(sourceSection, frameDescriptor, expressions, uninitialized);
    this.outerContext = outerContext;
  }

  @Override
  public String toString() {
    SourceSection ss = getSourceSection();
    final String id = ss.getIdentifier();
    return "Method " + id + "\t@" + Integer.toHexString(hashCode());
  }

  @Override
  public Invokable cloneWithNewLexicalContext(final LexicalContext outerContext) {
    FrameDescriptor inlinedFrameDescriptor = getFrameDescriptor().copy();
    LexicalContext  inlinedContext = new LexicalContext(inlinedFrameDescriptor,
        outerContext);
    ExpressionNode  inlinedBody = Inliner.doInline(uninitializedBody,
        inlinedContext);
    Method clone = new Method(getSourceSection(), inlinedFrameDescriptor,
        inlinedBody, outerContext, uninitializedBody);
    inlinedContext.setOuterMethod(clone);
    return clone;
  }

  @Override
  public boolean isBlock() {
    return outerContext != null;
  }

  public void setOuterContextMethod(final Method method) {
    outerContext.setOuterMethod(method);
  }

  @Override
  public void propagateLoopCountThroughoutLexicalScope(final long count) {
    assert count >= 0;

    if (outerContext != null) {
      outerContext.getOuterMethod().propagateLoopCountThroughoutLexicalScope(count);
    }
    reportLoopCount((count > Integer.MAX_VALUE) ? Integer.MAX_VALUE : (int) count);
  }


  @Override
  public Node deepCopy() {
    return cloneWithNewLexicalContext(outerContext);
  }
}
