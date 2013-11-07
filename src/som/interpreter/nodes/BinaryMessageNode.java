package som.interpreter.nodes;

import som.interpreter.nodes.messages.BinaryMonomorphicNode;
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
  @NodeChild(value = "receiver", type = ExpressionNode.class),
  @NodeChild(value = "argument", type = ExpressionNode.class)
})
public abstract class BinaryMessageNode extends AbstractMessageNode {

  public BinaryMessageNode(final SSymbol selector, final Universe universe) {
    super(selector, universe);
  }

  public BinaryMessageNode(final BinaryMessageNode node) {
    this(node.selector, node.universe);
  }

  // TODO: document this getter idiom
  public abstract ExpressionNode getReceiver();
  public abstract ExpressionNode getArgument();
  public abstract Object executeEvaluated(final VirtualFrame frame, final Object receiver, Object argument);

  // TODO: want to use @Generic here!
  @Specialization
  public Object doGeneric(final VirtualFrame frame,
      final Object rcvr,
      final Object arg) {
    CompilerDirectives.transferToInterpreter();

    SAbstractObject receiver = (SAbstractObject) rcvr;
    SAbstractObject argument = (SAbstractObject) arg;

    SClass rcvrClass = classOfReceiver(receiver, getReceiver());
    SMethod invokable = rcvrClass.lookupInvokable(selector);

    if (invokable != null) {
      BinaryMonomorphicNode node = NodeFactory.createBinaryMonomorphicNode(selector, universe, rcvrClass, invokable, getReceiver(), getReceiver());
      // BinaryMonomorphicNodeFactory.create(selector, universe, rcvrClass, invokable, getReceiver(), getArgument());
      return replace(node, "Be optimisitic and do a monomorphic lookup cache, or a primitive inline.").executeEvaluated(frame, receiver, argument);
    } else {
      SAbstractObject[] args = new SAbstractObject[] {argument};
      return doFullSend(frame, receiver, args, rcvrClass);
    }
  }

  @Override
  public ExpressionNode cloneForInlining() {
    // TODO: test whether this is problematic
    return (ExpressionNode) this.copy();
  }
}
