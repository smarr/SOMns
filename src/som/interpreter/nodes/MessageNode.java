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
import som.interpreter.nodes.specialized.IfTrueIfFalseMessageNode;
import som.interpreter.nodes.specialized.IfTrueIfFalseMessageNodeFactory;
import som.interpreter.nodes.specialized.MonomorpicMessageNode;
import som.interpreter.nodes.specialized.MonomorpicMessageNodeFactory;
import som.interpreter.nodes.specialized.WhileMessageNode;
import som.interpreter.nodes.specialized.WhileMessageNodeFactory;
import som.vm.Universe;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SBlock;
import som.vmobjects.SClass;
import som.vmobjects.SMethod;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.SlowPath;
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

  protected boolean isWhileTrueOrWhileFalse() {
    return selector.getString().equals("whileTrue:") ||
        selector.getString().equals("whileFalse:");
  }

  protected boolean isIfTrueOrIfFalse() {
    return selector.getString().equals("ifTrue:") ||
         selector.getString().equals("ifFalse:");
  }

  protected boolean isIfTrueIfFalse() {
    return selector.getString().equals("ifTrue:ifFalse:");
  }

  protected boolean hasBlockArgument() {
    return getArguments().getArgument(0) instanceof BlockNode;
  }

  protected boolean hasExpressionArgument() {
    return !hasBlockArgument();
  }

  protected boolean hasBlockReceiver() {
    return getReceiver() instanceof BlockNode;
  }

  @SlowPath
  @Specialization(order = 1,
      guards = {"isBooleanReceiver", "hasTwoArguments", "isIfTrueIfFalse"})
  public SAbstractObject doIfTrueIfFalse(final VirtualFrame frame,
      final SAbstractObject receiver, final Object arguments) {
    SAbstractObject[] args = (SAbstractObject[]) arguments;
    SBlock trueBlock  = null;
    SBlock falseBlock = null;

    if (getArguments().getArgument(0) instanceof BlockNode) {
      trueBlock  = (SBlock) args[0];
    }
    if (getArguments().getArgument(1) instanceof BlockNode) {
      falseBlock = (SBlock) args[1];
    }

    IfTrueIfFalseMessageNode node = IfTrueIfFalseMessageNodeFactory.create(
        selector, universe, trueBlock, falseBlock, getReceiver(), getArguments());
    return replace(node, "Specialize for #ifTrue:ifFalse.").
        doIfTrueIfFalse(frame, receiver, arguments);
  }

  @SlowPath
  @Specialization(order = 10,
      guards = {"isBooleanReceiver", "hasOneArgument",
      "isIfTrueOrIfFalse", "hasBlockArgument"})
  public SAbstractObject doIfTrueOrIfFalse(final VirtualFrame frame,
      final SAbstractObject receiver, final Object arguments) {
    SAbstractObject[] args = (SAbstractObject[]) arguments;
    SBlock   block = (SBlock)    args[0];

    // This specialization goes into a separate specialization hierarchy
    IfTrueAndIfFalseMessageNode node = IfTrueAndIfFalseMessageNodeFactory.create(
        selector, universe, block, selector.getString().equals("ifTrue:"),
        getReceiver(), getArguments());
    return replace(node, "Specialize for #ifTrue: or #ifFalse").doGeneric(frame, receiver, arguments);
  }

  @SlowPath
  @Specialization(order = 20,
      guards = {"isBooleanReceiver", "hasOneArgument",
      "isIfTrueOrIfFalse", "hasExpressionArgument"})
  public SAbstractObject doIfTrueOrIfFalseWithExp(final VirtualFrame frame,
      final SAbstractObject receiver, final Object arguments) {

    // This specialization goes into a separate specialization hierarchy
    IfTrueAndIfFalseWithExpMessageNode node =
        IfTrueAndIfFalseWithExpMessageNodeFactory.create(
        selector, universe, selector.getString().equals("ifTrue:"),
        getReceiver(), getArguments());
    return replace(node, "Specialize for #ifTrue: or #ifFalse with Expression").doGeneric(frame, receiver, arguments);
  }

  @SlowPath
  @Specialization(order = 30, guards = { "hasOneArgument",
      "isWhileTrueOrWhileFalse", "hasBlockReceiver" })
  public SAbstractObject doWhileTrueOrWhileFalse(final VirtualFrame frame,
      final SAbstractObject receiver, final Object arguments) {
    SBlock conditionBlock = (SBlock) receiver;
    SAbstractObject[] args = (SAbstractObject[]) arguments;
    SMethod loopBodyMethod = null;
    if (args[0] instanceof SBlock) {
      loopBodyMethod = ((SBlock) args[0]).getMethod();
    }
    WhileMessageNode node = WhileMessageNodeFactory.create(selector, universe,
        conditionBlock.getMethod(), loopBodyMethod, selector.getString().equals("whileTrue:"),
        getReceiver(), getArguments());
    return replace(node, "Specialize for #whileTrue: or #whileFalse:").
        doGeneric(frame, receiver, arguments);
  }

  @SlowPath
  @Generic
  public SAbstractObject doGeneric(final VirtualFrame frame, final SAbstractObject receiver,
      final Object arguments) {
    CompilerDirectives.transferToInterpreter();

    SClass rcvrClass = classOfReceiver(receiver, getReceiver());
    SMethod invokable = rcvrClass.lookupInvokable(selector);

    if (invokable != null) {
      MonomorpicMessageNode node = MonomorpicMessageNodeFactory.create(selector,
          universe, rcvrClass, invokable, getReceiver(), getArguments());
      return replace(node, "Be optimisitic and do a monomorphic lookup cache.").
          doMonomorphic(frame, receiver, arguments);
    } else {
      SAbstractObject[] args = (SAbstractObject[]) arguments;
      return doFullSend(frame, receiver, args, rcvrClass);
    }
  }

  @Override
  public ExpressionNode cloneForInlining() {
    return NodeFactory.createMessageNode(selector, universe, getReceiver(), getArguments());
  }
}
