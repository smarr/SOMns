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

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.nodes.LoopNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.source.SourceSection;

import bd.inlining.ScopeAdaptationVisitor;
import som.compiler.MethodBuilder;
import som.interpreter.LexicalScope.MethodScope;
import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.SOMNode;
import som.interpreter.nodes.dispatch.BackCacheCallNode;
import som.vmobjects.SInvokable;


public final class Method extends Invokable {

  private final MethodScope     methodScope;
  private final SourceSection[] definition;
  private final boolean         block;
  private BackCacheCallNode     uniqueCaller;

  public Method(final String name, final SourceSection sourceSection,
      final SourceSection[] definition,
      final ExpressionNode expressions,
      final MethodScope methodScope,
      final ExpressionNode uninitialized, final boolean block,
      final boolean isAtomic,
      final SomLanguage lang) {
    super(name, sourceSection, methodScope.getFrameDescriptor(),
        expressions, uninitialized, isAtomic, lang);
    this.definition = definition;
    this.block = block;
    this.methodScope = methodScope;
    assert methodScope.isFinalized();
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof Method)) {
      return false;
    }

    Method m = (Method) o;

    if (name.equals(m.name) && getSourceSection() == m.getSourceSection()) {
      return true;
    }

    assert !getSourceSection().equals(
        m.getSourceSection())
        : "If that triggers, something with the source sections is wrong.";
    return false;
  }

  public void setNewCaller(final BackCacheCallNode caller) {
    if (uniqueCaller == null) {
      uniqueCaller = caller;
      caller.uniqueCaller();
    } else {
      uniqueCaller.multipleCaller();
      caller.multipleCaller();
    }
  }

  public BackCacheCallNode getUniqueCaller() {
    return uniqueCaller;
  }

  public SourceSection[] getDefinition() {
    return definition;
  }

  @Override
  public String toString() {
    return getName() + "\t@" + Integer.toHexString(hashCode());
  }

  @Override
  public ExpressionNode inline(final MethodBuilder builder, final SInvokable outer) {
    builder.mergeIntoScope(methodScope, outer);
    return ScopeAdaptationVisitor.adapt(
        uninitializedBody, builder.getScope(), this.methodScope, 0, true,
        SomLanguage.getLanguage(this));
  }

  @Override
  public void propagateLoopCountThroughoutMethodScope(final long count) {
    assert count >= 0;
    methodScope.propagateLoopCountThroughoutMethodScope(count);
    LoopNode.reportLoopCount(expressionOrSequence,
        (count > Integer.MAX_VALUE) ? Integer.MAX_VALUE : (int) count);
  }

  public Method cloneAndAdaptAfterScopeChange(final MethodScope adaptedScope,
      final int appliesTo, final boolean cloneAdaptedAsUninitialized,
      final boolean someOuterScopeIsMerged) {
    SomLanguage lang = SomLanguage.getLanguage(this);
    ExpressionNode adaptedBody = ScopeAdaptationVisitor.adapt(
        uninitializedBody, adaptedScope, methodScope,
        appliesTo, someOuterScopeIsMerged, lang);

    ExpressionNode uninit;
    if (cloneAdaptedAsUninitialized) {
      uninit = NodeUtil.cloneNode(adaptedBody);
    } else {
      uninit = uninitializedBody;
    }

    Method clone = new Method(name, getSourceSection(), definition, adaptedBody,
        adaptedScope, uninit, block, isAtomic, lang);
    adaptedScope.setMethod(clone);
    return clone;
  }

  @Override
  public Node deepCopy() {
    MethodScope splitScope = methodScope.split();
    assert methodScope != splitScope;
    assert splitScope.isFinalized();
    return cloneAndAdaptAfterScopeChange(splitScope, 0, false, true);
  }

  @Override
  @TruffleBoundary
  public Invokable createAtomic() {
    assert !isAtomic : "We should only ask non-atomic invokables for their atomic version";
    SomLanguage lang = SomLanguage.getLanguage(this);

    MethodScope splitScope = methodScope.split();
    ExpressionNode body =
        ScopeAdaptationVisitor.adapt(uninitializedBody, splitScope, methodScope, 0, true,
            lang);
    ExpressionNode uninit = NodeUtil.cloneNode(body);

    Method atomic = new Method(name, getSourceSection(), definition, body,
        splitScope, uninit, block, true, lang);
    splitScope.setMethod(atomic);
    return atomic;
  }

  public boolean isBlock() {
    return block;
  }

  public SourceSection getRootNodeSource() {
    ExpressionNode root = SOMNode.unwrapIfNecessary(expressionOrSequence);
    assert root.isMarkedAsRootExpression();

    return root.getSourceSection();
  }

  public MethodScope getLexicalScope() {
    return methodScope;
  }
}
