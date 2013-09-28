package som.interpreter.nodes.specialized;

import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.MessageNode;
import som.vm.Universe;
import som.vmobjects.Class;
import som.vmobjects.Invokable;
import som.vmobjects.Object;
import som.vmobjects.Symbol;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;

public class PolymorpicMessageNode extends MessageNode {
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

  public PolymorpicMessageNode(final ExpressionNode receiver,
      final ExpressionNode[] arguments, final Symbol selector,
      final Universe universe,
      final Class currentRcvrClass) {
    super(receiver, arguments, selector, universe);
    rcvrClasses = new Class[CACHE_SIZE];
    invokables  = new Invokable[CACHE_SIZE];

    rcvrClasses[0] = currentRcvrClass;
    invokables[0]  = currentRcvrClass.lookupInvokable(selector);
    cacheEntries   = 1;
  }

  @Override
  public Object executeGeneric(final VirtualFrame frame) {
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

      replace(mega, "It is not a polymorpic send.");
      return doFullSend(frame, rcvr, args, currentRcvrClass);
    }
  }
}
