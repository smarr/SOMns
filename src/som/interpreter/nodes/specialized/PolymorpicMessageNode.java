package som.interpreter.nodes.specialized;

import som.interpreter.nodes.AbstractMessageNode;
import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.NodeFactory;
import som.vm.Universe;
import som.vmobjects.SClass;
import som.vmobjects.SMethod;
import som.vmobjects.SObject;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;

public abstract class PolymorpicMessageNode extends AbstractMessageNode {
  private static final int CACHE_SIZE = 8;

  private final SClass[]   rcvrClasses;
  private final SMethod[]  invokables;

  private int cacheEntries;

  public PolymorpicMessageNode(final SSymbol selector,
      final Universe universe, final SClass firstRcvrClass,
      final SMethod firstInvokable,
      final SClass secondRcvrClass) {
    super(selector, universe);
    rcvrClasses = new SClass[CACHE_SIZE];
    invokables  = new SMethod[CACHE_SIZE];

    rcvrClasses[0] = firstRcvrClass;
    invokables[0]  = firstInvokable;
    rcvrClasses[1] = secondRcvrClass;
    invokables[1]  = secondRcvrClass.lookupInvokable(selector);
    cacheEntries   = 2;
  }

  public PolymorpicMessageNode(final SSymbol selector,
      final Universe universe,
      final SClass currentRcvrClass) {
    super(selector, universe);

    rcvrClasses = new SClass[CACHE_SIZE];
    invokables  = new SMethod[CACHE_SIZE];

    rcvrClasses[0] = currentRcvrClass;
    invokables[0]  = currentRcvrClass.lookupInvokable(selector);
    cacheEntries   = 1;
  }

  public PolymorpicMessageNode(final PolymorpicMessageNode node) {
    this(node.selector, node.universe, node.rcvrClasses[0],
        node.invokables[0], node.rcvrClasses[1]);
  }

  @Specialization
  public SObject doGeneric(final VirtualFrame frame, final SObject receiver,
      final Object arguments) {
    SClass currentRcvrClass = classOfReceiver(receiver, getReceiver());
    SObject[] args = (SObject[]) arguments;

    int i;
    for (i = 0; i < cacheEntries; i++) {
      if (rcvrClasses[i] == currentRcvrClass) {
        return invokables[i].invoke(frame.pack(), receiver, args);
      }
    }

    if (i < CACHE_SIZE) { // we got still room in this polymorphic inline cache
      rcvrClasses[cacheEntries] = currentRcvrClass;
      invokables[cacheEntries]  = currentRcvrClass.lookupInvokable(selector);
      return invokables[i].invoke(frame.pack(), receiver, args);
    } else {
      return generalizeToMegamorphicNode().
          doGeneric(frame, receiver, arguments);
    }
  }

  public MegamorphicMessageNode generalizeToMegamorphicNode() {
    CompilerDirectives.transferToInterpreter();
    // So, it might just be a megamorphic send site.
    MegamorphicMessageNode mega = MegamorphicMessageNodeFactory.create(selector,
        universe, getReceiver(), getArguments());
    return replace(mega, "It is not a polymorpic send.");
  }

  @Override
  public ExpressionNode cloneForInlining() {
    return NodeFactory.createMessageNode(selector, universe,
        getReceiver().cloneForInlining(),
        getArguments().cloneForInlining());
  }
}
