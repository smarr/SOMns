package som.interpreter.nodes.specialized;

import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.MessageNode;
import som.vm.Universe;
import som.vmobjects.SClass;
import som.vmobjects.SInvokable;
import som.vmobjects.SObject;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;

public class PolymorpicMessageNode extends MessageNode {
  private static final int CACHE_SIZE = 8;

  private final SClass[]      rcvrClasses;
  private final SInvokable[]  invokables;

  private int cacheEntries;

  public PolymorpicMessageNode(final ExpressionNode receiver,
      final ExpressionNode[] arguments, final SSymbol selector,
      final Universe universe, final SClass firstRcvrClass,
      final SInvokable firstInvokable,
      final SClass secondRcvrClass) {
    super(receiver, arguments, selector, universe);
    rcvrClasses = new SClass[CACHE_SIZE];
    invokables  = new SInvokable[CACHE_SIZE];

    rcvrClasses[0] = firstRcvrClass;
    invokables[0]  = firstInvokable;
    rcvrClasses[1] = secondRcvrClass;
    invokables[1]  = secondRcvrClass.lookupInvokable(selector);
    cacheEntries   = 2;
  }

  public PolymorpicMessageNode(final ExpressionNode receiver,
      final ExpressionNode[] arguments, final SSymbol selector,
      final Universe universe,
      final SClass currentRcvrClass) {
    super(receiver, arguments, selector, universe);
    rcvrClasses = new SClass[CACHE_SIZE];
    invokables  = new SInvokable[CACHE_SIZE];

    rcvrClasses[0] = currentRcvrClass;
    invokables[0]  = currentRcvrClass.lookupInvokable(selector);
    cacheEntries   = 1;
  }

  @Override
  public SObject executeGeneric(final VirtualFrame frame) {
    // evaluate all the expressions: first determine receiver
    SObject rcvr = receiver.executeGeneric(frame);

    // then determine the arguments
    SObject[] args = determineArguments(frame);

    SClass currentRcvrClass = classOfReceiver(rcvr, receiver);

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
