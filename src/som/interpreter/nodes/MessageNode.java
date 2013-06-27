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

import com.oracle.truffle.api.frame.VirtualFrame;

import som.interpreter.nodes.VariableNode.SuperReadNode;
import som.vm.Universe;
import som.vmobjects.Invokable;
import som.vmobjects.Object;
import som.vmobjects.Symbol;

// @NodeChildren({
//  @NodeChild(value = "receiver",  type = ExpressionNode.class),
//  @NodeChild(value = "arguments", type = ExpressionNode[].class)})
public class MessageNode extends ExpressionNode {

  @Child private final ExpressionNode   receiver;
  @Child private final ExpressionNode[] arguments;

  private final Symbol   selector;
  private final Universe universe;

  public MessageNode(final ExpressionNode receiver,
      final ExpressionNode[] arguments,
      final Symbol selector,
      final Universe universe) {
    this.receiver  = adoptChild(receiver);
    this.arguments = adoptChildren(arguments);
    this.selector  = selector;
    this.universe  = universe;
  }

  @Override
  public Object executeGeneric(VirtualFrame frame) {
    // evaluate all the expressions: first determine receiver
    Object rcvr = receiver.executeGeneric(frame);
    int numArgs = (arguments == null) ? 0 : arguments.length;

    // then determine the arguments
    Object[] args = new Object[numArgs];

    for (int i = 0; i < numArgs; i++) {
      args[i] = arguments[i].executeGeneric(frame);
    }

    // now start lookup
    som.vmobjects.Class rcvrClass = rcvr.getSOMClass();

    // first determine whether it is a normal, or super send
    if (receiver instanceof SuperReadNode) {
      rcvrClass = rcvrClass.getSuperClass();
    }

    // now lookup selector
    Invokable invokable = rcvrClass.lookupInvokable(selector);

    if (invokable != null) {
      return invokable.invoke(frame, rcvr, args);
    } else {
      return rcvr.sendDoesNotUnderstand(selector, args, universe, frame);
    }
  }
}
