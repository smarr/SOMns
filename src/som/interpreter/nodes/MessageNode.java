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

import som.interpreter.nodes.VariableNode.SuperReadNode;
import som.interpreter.nodes.specialized.IfTrueAndIfFalseMessageNode;
import som.interpreter.nodes.specialized.IfTrueIfFalseMessageNode;
import som.interpreter.nodes.specialized.MonomorpicMessageNode;
import som.vm.Universe;
import som.vmobjects.Block;
import som.vmobjects.Class;
import som.vmobjects.Invokable;
import som.vmobjects.Object;
import som.vmobjects.Symbol;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;

// TODO: I need to add a check that the invokable has not changed

// @NodeChildren({
//  @NodeChild(value = "receiver",  type = ExpressionNode.class),
//  @NodeChild(value = "arguments", type = ExpressionNode[].class)})
public class MessageNode extends ExpressionNode {

  @Child    protected final ExpressionNode   receiver;
  @Children protected final ExpressionNode[] arguments;

  protected final Symbol   selector;
  protected final Universe universe;

  private ExpressionNode specializedVersion;

  public MessageNode(final ExpressionNode receiver,
      final ExpressionNode[] arguments,
      final Symbol selector,
      final Universe universe) {
    this.receiver  = adoptChild(receiver);
    this.arguments = adoptChildren(arguments);
    this.selector  = selector;
    this.universe  = universe;
  }

  /**
   * @return uninitialized node to allow for specialization
   */
  @Override
  public ExpressionNode cloneForInlining() {
    return new MessageNode(receiver, arguments, selector, universe);
  }

  protected Object[] determineArguments(final VirtualFrame frame) {
    int numArgs = (arguments == null) ? 0 : arguments.length;

    Object[] args = new Object[numArgs];

    for (int i = 0; i < numArgs; i++) {
      args[i] = arguments[i].executeGeneric(frame);
    }

    return args;
  }

  protected Object doFullSend(final VirtualFrame frame, final Object rcvr,
      final Object[] args, final Class rcvrClass) {
    // now lookup selector
    Invokable invokable = rcvrClass.lookupInvokable(selector);

    if (invokable != null) {
      return invokable.invoke(frame.pack(), rcvr, args);
    } else {
      return rcvr.sendDoesNotUnderstand(selector, args, universe, frame.pack());
    }
  }

  protected static Class classOfReceiver(final Object rcvr, final ExpressionNode receiver) {
    Class rcvrClass = rcvr.getSOMClass();

    // first determine whether it is a normal, or super send
    if (receiver instanceof SuperReadNode) {
      rcvrClass = rcvrClass.getSuperClass();
    }

    return rcvrClass;
  }

  @Override
  public Object executeGeneric(final VirtualFrame frame) {
    // evaluate all the expressions: first determine receiver
    Object rcvr = receiver.executeGeneric(frame);

    // then determine the arguments
    Object[] args = determineArguments(frame);

    // now start lookup
    Class rcvrClass = classOfReceiver(rcvr, receiver);

    // now lookup selector
    Invokable invokable = rcvrClass.lookupInvokable(selector);

    if (invokable != null) {
      return specializeAndExecute(frame, rcvr, rcvrClass, invokable, args);
    } else {
      return rcvr.sendDoesNotUnderstand(selector, args, universe, frame.pack());
    }
  }

  public final MessageNode replace(MessageNode newNode, String reason) {
    // if we have a recursive method call in the receiver expression, we
    // might not be able to do the specialization after the receiver has
    // been evaluated, because the node already has been specialized, but
    // higher on the stack, we still got the uninitialized node.
    // An example for such a situation can be found in Queens.som:49.
    // As a work-around, we will remember the specialized version of this node
    // and try to use it for the later part of the execution.
    specializedVersion = newNode;
    return super.replace(newNode, reason);
  }


  private Object specializeAndExecute(final VirtualFrame frame, Object rcvr,
      Class rcvrClass, Invokable invokable, Object[] args) {
    CompilerDirectives.transferToInterpreter();

    // first check whether it is a #ifTrue:, #ifFalse, or #ifTrue:ifFalse:
    if ((rcvrClass == universe.trueObject.getSOMClass() ||
         rcvrClass == universe.falseObject.getSOMClass()) &&
         arguments != null &&
         arguments[0] instanceof LiteralNode.BlockNode) {
      boolean isIfTrue = selector.getString().equals("ifTrue:");

      if (isIfTrue || selector.getString().equals("ifFalse:")) {
        // it is #ifTrue: or #ifFalse: with a literal block
        IfTrueAndIfFalseMessageNode node;

        // during evaluating receiver and arguments, we might have already
        // specialized this node
        if (specializedVersion == null) {
          Block block = (Block) args[0];
          node = new
              IfTrueAndIfFalseMessageNode(receiver, arguments, selector,
                  universe, block, isIfTrue);

          replace(node, "Be optimisitc, and assume it's always a simple #ifTrue: or #ifFalse:");
        } else {
          node = (IfTrueAndIfFalseMessageNode) specializedVersion;
        }

        return node.evaluateBody(frame, rcvr);
      } else if (selector.getString().equals("ifTrue:ifFalse:") &&
          arguments.length == 2 &&
          arguments[1] instanceof LiteralNode.BlockNode) {
        // it is #ifTrue:ifFalse: with two literal block arguments
        Block trueBlock  = (Block) args[0];
        Block falseBlock = (Block) args[1];
        IfTrueIfFalseMessageNode node;

        if (specializedVersion == null) {
          node = new IfTrueIfFalseMessageNode(receiver,
              arguments, selector, universe, trueBlock, falseBlock);

          replace(node, "Be optimisitc, and assume it's always a simple #ifTrue:#ifFalse:");
        } else {
          node = (IfTrueIfFalseMessageNode) specializedVersion;
        }

        return node.evaluateBody(frame, rcvr);
      }
    }

    // if it is not one of the special message sends, it is optimistically
    // converted into a monomorphic send site, but only if we haven't
    // specialized it already    
    if (specializedVersion == null) {
      MonomorpicMessageNode mono = new MonomorpicMessageNode(receiver,
          arguments, selector, universe, rcvrClass, invokable);

      replace(mono, "Assume it's goint to be a monomorphic send site.");
    }

    // Then execute the invokable, because it can exit this method with
    // control flow exceptions (non-local returns), which would leave node
    // unspecialized.
    return invokable.invoke(frame.pack(), rcvr, args);
  }
}
