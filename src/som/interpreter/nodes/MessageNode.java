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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.nodes.NodeInfo.Kind;

import som.interpreter.nodes.VariableNode.SuperReadNode;
import som.vm.Universe;
import som.vmobjects.Class;
import som.vmobjects.Invokable;
import som.vmobjects.Object;
import som.vmobjects.Symbol;

// @NodeChildren({
//  @NodeChild(value = "receiver",  type = ExpressionNode.class),
//  @NodeChild(value = "arguments", type = ExpressionNode[].class)})
@NodeInfo(kind = Kind.UNINITIALIZED)
public class MessageNode extends ExpressionNode {

  @Child    protected final ExpressionNode   receiver;
  @Children protected final ExpressionNode[] arguments;

  protected final Symbol   selector;
  protected final Universe universe;

  public MessageNode(final ExpressionNode receiver,
      final ExpressionNode[] arguments,
      final Symbol selector,
      final Universe universe) {
    this.receiver  = adoptChild(receiver);
    this.arguments = adoptChildren(arguments);
    this.selector  = selector;
    this.universe  = universe;
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
  public Object executeGeneric(VirtualFrame frame) {
    // evaluate all the expressions: first determine receiver
    Object rcvr = receiver.executeGeneric(frame);

    // then determine the arguments
    Object[] args = determineArguments(frame);

    // now start lookup
    Class rcvrClass = classOfReceiver(rcvr, receiver);

    // now lookup selector
    Invokable invokable = rcvrClass.lookupInvokable(selector);

    if (invokable != null) {
      CompilerDirectives.transferToInterpreter();

      // First let's rewrite this node
      MonomorpicMessageNode mono = new MonomorpicMessageNode(receiver, arguments, selector, universe, rcvrClass, invokable);
      this.replace(mono, "Let's assume it is a monomorphic send site.");

      // Then execute the invokable, because it can exit this method with
      // control flow exceptions (non-local returns), which would leave node
      // unspecialized.
      return invokable.invoke(frame.pack(), rcvr, args);
    } else {
      return rcvr.sendDoesNotUnderstand(selector, args, universe, frame.pack());
    }
  }

  @NodeInfo(kind = Kind.SPECIALIZED)
  public static class MonomorpicMessageNode extends MessageNode {

    private final Class      rcvrClass;
    private final Invokable  invokable;

    public MonomorpicMessageNode(final ExpressionNode receiver,
        final ExpressionNode[] arguments, final Symbol selector,
        final Universe universe, final Class rcvrClass,
        final Invokable invokable) {
      super(receiver, arguments, selector, universe);
      this.rcvrClass = rcvrClass;
      this.invokable = invokable;
    }

    @Override
    public Object executeGeneric(VirtualFrame frame) {
      // evaluate all the expressions: first determine receiver
      Object rcvr = receiver.executeGeneric(frame);

      // then determine the arguments
      Object[] args = determineArguments(frame);

      Class currentRcvrClass = classOfReceiver(rcvr, receiver);

      if (currentRcvrClass == rcvrClass) {
        return invokable.invoke(frame.pack(), rcvr, args);
      } else {
        CompilerDirectives.transferToInterpreter();
        // So, it might just be a polymorphic send site.
        PolymorpicMessageNode poly = new PolymorpicMessageNode(receiver,
            arguments, selector, universe, rcvrClass, invokable, currentRcvrClass);
        this.replace(poly, "It is not a monomorpic send.");
        return doFullSend(frame, rcvr, args, currentRcvrClass);
      }
    }
  }

  @NodeInfo(kind = Kind.SPECIALIZED)
  public static class PolymorpicMessageNode extends MessageNode {
    private static final int CACHE_SIZE = 8;

    private final Class[]      rcvrClasses;
    private final Invokable[]  invokables;

    private int cacheEntries;

    public PolymorpicMessageNode(final ExpressionNode receiver,
        final ExpressionNode[] arguments, final Symbol selector,
        final Universe universe, final Class firstRcvrClass,
        final Invokable firstInvokable,
        final Class secondRcvrClass) {
      super(receiver, arguments, selector, universe);
      rcvrClasses = new Class[CACHE_SIZE];
      invokables  = new Invokable[CACHE_SIZE];

      rcvrClasses[0] = firstRcvrClass;
      invokables[0]  = firstInvokable;
      rcvrClasses[1] = secondRcvrClass;
      invokables[1]  = secondRcvrClass.lookupInvokable(selector);
      cacheEntries   = 2;
    }

    @Override
    public Object executeGeneric(VirtualFrame frame) {
      // evaluate all the expressions: first determine receiver
      Object rcvr = receiver.executeGeneric(frame);

      // then determine the arguments
      Object[] args = determineArguments(frame);

      Class currentRcvrClass = classOfReceiver(rcvr, receiver);

      int i;
      for (i = 0; i < cacheEntries; i++) {
        if (rcvrClasses[i] == currentRcvrClass) {
          return invokables[i].invoke(frame.pack(), rcvr, args);
        }
      }

      if (i < CACHE_SIZE) { // we got still room in this polymorphic inline cache 
        rcvrClasses[cacheEntries] = currentRcvrClass;
        invokables[cacheEntries]  = currentRcvrClass.lookupInvokable(selector);
        return invokables[i].invoke(frame.pack(), rcvr, args);
      } else {
        CompilerDirectives.transferToInterpreter();
        // So, it might just be a megamorphic send site.
        MegamorphicMessageNode mega = new MegamorphicMessageNode(receiver, arguments, selector, universe);
        this.replace(mega, "It is not a monomorpic send.");
        return doFullSend(frame, rcvr, args, currentRcvrClass);
      }
    }
  }

  @NodeInfo(kind = Kind.GENERIC)
  public static class MegamorphicMessageNode extends MessageNode {

    public MegamorphicMessageNode(final ExpressionNode receiver,
        final ExpressionNode[] arguments, final Symbol selector,
        final Universe universe) {
      super(receiver, arguments, selector, universe);
    }

    @Override
    public Object executeGeneric(VirtualFrame frame) {
      // evaluate all the expressions: first determine receiver
      Object rcvr = receiver.executeGeneric(frame);

      // then determine the arguments
      Object[] args = determineArguments(frame);

      // now start lookup
      Class rcvrClass = classOfReceiver(rcvr, receiver);

      return doFullSend(frame, rcvr, args, rcvrClass);
    }
  }
}
