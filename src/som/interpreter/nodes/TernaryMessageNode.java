package som.interpreter.nodes;

import som.interpreter.nodes.messages.TernaryMonomorphicNode;
import som.vm.Universe;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SClass;
import som.vmobjects.SMethod;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;

@NodeChildren({
  @NodeChild(value = "receiver",  type = ExpressionNode.class),
  @NodeChild(value = "firstArg",  type = ExpressionNode.class),
  @NodeChild(value = "secondArg", type = ExpressionNode.class)
})
public abstract class TernaryMessageNode extends AbstractMessageNode {
  public TernaryMessageNode(final SSymbol selector, final Universe universe) {
    super(selector, universe);
  }

  public TernaryMessageNode(final TernaryMessageNode node) {
    this(node.selector, node.universe);
  }

  // TODO: document this getter idiom
  public abstract ExpressionNode getReceiver();
  public abstract ExpressionNode getFirstArg();
  public abstract ExpressionNode getSecondArg();
  public abstract Object executeEvaluated(final VirtualFrame frame, final Object receiver, Object firstArg, Object secondArg);

  // TODO: i want to use @Generic here!
  @Specialization
  public Object doGeneric(final VirtualFrame frame,
      final Object rcvr, final Object first, final Object second) {
    CompilerDirectives.transferToInterpreter();

    SAbstractObject receiver  = (SAbstractObject) rcvr;
    SAbstractObject firstArg  = (SAbstractObject) first;
    SAbstractObject secondArg = (SAbstractObject) second;

    SClass rcvrClass = classOfReceiver(receiver, getReceiver());
    SMethod invokable = rcvrClass.lookupInvokable(selector);

    if (invokable != null) {
      TernaryMonomorphicNode node = NodeFactory.createTernaryMonomorphicNode(selector, universe, rcvrClass, invokable, getReceiver(), getFirstArg(), getSecondArg());
      return replace(node, "Be optimisitic and do a monomorphic lookup cache, or a primitive inline.").
          executeEvaluated(frame, receiver, firstArg, secondArg);
    } else {
      SAbstractObject[] args = new SAbstractObject[] {firstArg, secondArg};
      return doFullSend(frame, receiver, args, rcvrClass);
    }
  }

  @Override
  public ExpressionNode cloneForInlining() {
    // TODO: test whether this is problematic
    return (ExpressionNode) this.copy();
    //return NodeFactory.createTernaryMessageNode(selector, universe, getReceiver(),
    //    getFirstArg(), getSecondArg());
  }
}
