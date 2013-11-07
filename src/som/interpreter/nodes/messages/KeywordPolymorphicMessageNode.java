package som.interpreter.nodes.messages;

import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.KeywordMessageNode;
import som.vm.Universe;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SClass;
import som.vmobjects.SMethod;
import som.vmobjects.SSymbol;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;


public abstract class KeywordPolymorphicMessageNode extends KeywordMessageNode {
  private static final int CACHE_SIZE = 8;

  private final SClass[]   rcvrClasses;
  private final SMethod[]  invokables;

  private int cacheEntries;

  public KeywordPolymorphicMessageNode(final SSymbol selector,
      final Universe universe,
      final SClass firstRcvrClass,  final SMethod firstInvokable,
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

  public KeywordPolymorphicMessageNode(final SSymbol selector,
      final Universe universe, final SClass firstRcvrClass) {
    super(selector, universe);

    rcvrClasses = new SClass[CACHE_SIZE];
    invokables  = new SMethod[CACHE_SIZE];

    rcvrClasses[0] = firstRcvrClass;
    invokables[0]  = firstRcvrClass.lookupInvokable(selector);
    cacheEntries   = 1;
  }

  public KeywordPolymorphicMessageNode(final KeywordPolymorphicMessageNode node) {
    this(node.selector, node.universe, node.rcvrClasses[0], node.invokables[0],
        node.rcvrClasses[1]);
  }

  @Override
  @Specialization
  public Object doGeneric(final VirtualFrame frame, final Object rcvr, final Object arguments) {
    SAbstractObject receiver = (SAbstractObject) rcvr;

    SClass currentRcvrClass = classOfReceiver(receiver, getReceiver());

    int i;
    for (i = 0; i < cacheEntries; i++) {
      if (rcvrClasses[i] == currentRcvrClass) {
        return invokables[i].invoke(frame.pack(), receiver, (SAbstractObject[]) arguments);
      }
    }

    if (i < CACHE_SIZE) { // we got still room in this polymorphic inline cache
      SMethod invkbl = currentRcvrClass.lookupInvokable(selector);

      rcvrClasses[cacheEntries] = currentRcvrClass;
      invokables[cacheEntries]  = invkbl;
      cacheEntries++;
      return invkbl.invoke(frame.pack(), receiver, (SAbstractObject[]) arguments);
    } else {
      return generalizeToMegamorphicNode().executeEvaluated(frame, receiver);
    }
  }

  private UnaryMegamorphicMessageNode generalizeToMegamorphicNode() {
    CompilerDirectives.transferToInterpreter();
    throw new NotImplementedException();
//    // So, it might just be a megamorphic send site.
//    MegamorphicMessageNode mega = MegamorphicMessageNodeFactory.create(selector,
//        universe, getReceiver(), getArguments());
//    return replace(mega, "It is not a polymorpic send.");
  }

  @Override
  public ExpressionNode cloneForInlining() {
    throw new NotImplementedException();
//    return NodeFactory.createMessageNode(selector, universe,
//        getReceiver().cloneForInlining(),
//        getArguments().cloneForInlining());
  }
}
