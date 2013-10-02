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
import som.interpreter.nodes.literals.BlockNode;
import som.interpreter.nodes.specialized.IfTrueAndIfFalseMessageNode;
import som.interpreter.nodes.specialized.IfTrueIfFalseMessageNode;
import som.interpreter.nodes.specialized.MonomorpicMessageNode;
import som.vm.Universe;
import som.vmobjects.SBlock;
import som.vmobjects.SClass;
import som.vmobjects.SInvokable;
import som.vmobjects.SObject;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;

// TODO: I need to add a check that the invokable has not changed

// @NodeChildren({
//  @NodeChild(value = "receiver",  type = ExpressionNode.class),
//  @NodeChild(value = "arguments", type = ExpressionNode[].class)})
public class MessageNode extends ExpressionNode {

  @Child    protected       ExpressionNode   receiver;
  @Children protected final ExpressionNode[] arguments;

  protected final SSymbol   selector;
  protected final Universe universe;

  private ExpressionNode specializedVersion;

  public MessageNode(final ExpressionNode receiver,
      final ExpressionNode[] arguments,
      final SSymbol selector,
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

  @ExplodeLoop
  protected SObject[] determineArguments(final VirtualFrame frame) {
    int numArgs = (arguments == null) ? 0 : arguments.length;

    SObject[] args = new SObject[numArgs];

    for (int i = 0; i < numArgs; i++) {
      args[i] = arguments[i].executeGeneric(frame);
    }

    return args;
  }

  protected SObject doFullSend(final VirtualFrame frame, final SObject rcvr,
      final SObject[] args, final SClass rcvrClass) {
    // now lookup selector
    SInvokable invokable = rcvrClass.lookupInvokable(selector);

    if (invokable != null) {
      return invokable.invoke(frame.pack(), rcvr, args);
    } else {
      return rcvr.sendDoesNotUnderstand(selector, args, universe, frame.pack());
    }
  }

  protected static SClass classOfReceiver(final SObject rcvr, final ExpressionNode receiver) {
    SClass rcvrClass = rcvr.getSOMClass();

    // first determine whether it is a normal, or super send
    if (receiver instanceof SuperReadNode) {
      rcvrClass = rcvrClass.getSuperClass();
    }

    return rcvrClass;
  }

  @Override
  public SObject executeGeneric(final VirtualFrame frame) {
    // evaluate all the expressions: first determine receiver
    SObject rcvr = receiver.executeGeneric(frame);

    // then determine the arguments
    SObject[] args = determineArguments(frame);

    // now start lookup
    SClass rcvrClass = classOfReceiver(rcvr, receiver);

    // now lookup selector
    SInvokable invokable = rcvrClass.lookupInvokable(selector);

    if (invokable != null) {
      return specializeAndExecute(frame, rcvr, rcvrClass, invokable, args);
    } else {
      return rcvr.sendDoesNotUnderstand(selector, args, universe, frame.pack());
    }
  }

  public final MessageNode replace(final MessageNode newNode, final String reason) {
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


  private SObject specializeAndExecute(final VirtualFrame frame, final SObject rcvr,
      final SClass rcvrClass, final SInvokable invokable, final SObject[] args) {
    CompilerDirectives.transferToInterpreter();

    // first check whether it is a #ifTrue:, #ifFalse, or #ifTrue:ifFalse:
    if ((rcvrClass == universe.trueObject.getSOMClass() ||
         rcvrClass == universe.falseObject.getSOMClass()) &&
         arguments != null &&
         arguments[0] instanceof BlockNode) {
      boolean isIfTrue = selector.getString().equals("ifTrue:");

      if (isIfTrue || selector.getString().equals("ifFalse:")) {
        // it is #ifTrue: or #ifFalse: with a literal block
        IfTrueAndIfFalseMessageNode node;

        // during evaluating receiver and arguments, we might have already
        // specialized this node
        if (specializedVersion == null) {
          SBlock block = (SBlock) args[0];
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
          arguments[1] instanceof BlockNode) {
        // it is #ifTrue:ifFalse: with two literal block arguments
        SBlock trueBlock  = (SBlock) args[0];
        SBlock falseBlock = (SBlock) args[1];
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
