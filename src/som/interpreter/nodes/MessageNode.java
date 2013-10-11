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
package som.interpreter.nodes;

import som.interpreter.nodes.literals.BlockNode;
import som.interpreter.nodes.specialized.IfTrueAndIfFalseMessageNode;
import som.interpreter.nodes.specialized.IfTrueAndIfFalseMessageNodeFactory;
import som.interpreter.nodes.specialized.IfTrueAndIfFalseWithExpMessageNode;
import som.interpreter.nodes.specialized.IfTrueAndIfFalseWithExpMessageNodeFactory;
import som.interpreter.nodes.specialized.MonomorpicMessageNode;
import som.interpreter.nodes.specialized.MonomorpicMessageNodeFactory;
import som.vm.Universe;
import som.vmobjects.SBlock;
import som.vmobjects.SClass;
import som.vmobjects.SInvokable;
import som.vmobjects.SObject;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.dsl.Generic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;

public abstract class MessageNode extends AbstractMessageNode {

  public MessageNode(final SSymbol selector, final Universe universe) {
    super(selector, universe);
  }

  public MessageNode(final MessageNode node) {
    this(node.selector, node.universe);
  }

  protected boolean isIfTrueOrIfFalse() {
    return selector.getString().equals("ifTrue:") ||
         selector.getString().equals("ifFalse:");
  }

  protected boolean hasBlockArgument() {
    return getArguments().getArgument(0) instanceof BlockNode;
  }

  protected boolean hasExpressionArgument() {
    return !hasBlockArgument();
  }

  @Specialization(order = 10,
      guards = {"isBooleanReceiver", "hasOneArgument",
      "isIfTrueOrIfFalse", "hasBlockArgument"})
  public SObject doIfTrueOrIfFalse(final VirtualFrame frame,
      final SObject receiver, final Object arguments) {
    SObject[] args = (SObject[]) arguments;
    SBlock   block = (SBlock)    args[0];

    // This specialization goes into a separate specialization hierarchy
    IfTrueAndIfFalseMessageNode node = IfTrueAndIfFalseMessageNodeFactory.create(
        selector, universe, block, selector.getString().equals("ifTrue:"),
        getReceiver(), getArguments());
    return replace(node, "Specialize for #ifTrue: or #ifFalse").doGeneric(frame, receiver, arguments);
  }

  @Specialization(order = 20,
      guards = {"isBooleanReceiver", "hasOneArgument",
      "isIfTrueOrIfFalse", "hasExpressionArgument"})
  public SObject doIfTrueOrIfFalseWithExp(final VirtualFrame frame,
      final SObject receiver, final Object arguments) {

    // This specialization goes into a separate specialization hierarchy
    IfTrueAndIfFalseWithExpMessageNode node =
        IfTrueAndIfFalseWithExpMessageNodeFactory.create(
        selector, universe, selector.getString().equals("ifTrue:"),
        getReceiver(), getArguments());
    return replace(node, "Specialize for #ifTrue: or #ifFalse with Expression").doGeneric(frame, receiver, arguments);
  }

  @Generic
  public SObject doGeneric(final VirtualFrame frame, final SObject receiver,
      final Object arguments) {
    SClass rcvrClass = classOfReceiver(receiver, getReceiver());
    SInvokable invokable = rcvrClass.lookupInvokable(selector);

    MonomorpicMessageNode node = MonomorpicMessageNodeFactory.create(selector,
        universe, rcvrClass, invokable, getReceiver(), getArguments());
    return replace(node, "Be optimisitic and do a monomorphic lookup cache.").doMonomorphic(frame, receiver, arguments);
  }

  @Override
  public ExpressionNode cloneForInlining() {
    return NodeFactory.createMessageNode(selector, universe, getReceiver(), getArguments());
  }
}
