package som.interpreter.nodes.messages;

import som.interpreter.nodes.AbstractMessageNode;
import som.interpreter.nodes.ExpressionNode;
import som.vm.Universe;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SClass;
import som.vmobjects.SMethod;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.SlowPath;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.nodes.InlinableCallSite;
import com.oracle.truffle.api.nodes.Node;

@NodeChild(value = "receiver", type = ExpressionNode.class)
public abstract class AbstractMonomorphicMessageNode extends AbstractMessageNode implements InlinableCallSite {
  protected final SClass  rcvrClass;
  @CompilationFinal protected SMethod invokable; // we got a circular dependency with the creation of SMethod objects

  protected int callCount;

  public void setInvokable(final SMethod invokable) {
    if (this.invokable != null) {
      throw new IllegalStateException("Don't allow resetting invokable.");
    }
    this.invokable = invokable;
  }

  public AbstractMonomorphicMessageNode(final SSymbol selector,
      final Universe universe, final SClass rcvrClass, final SMethod invokable) {
    super(selector, universe);
    this.rcvrClass = rcvrClass;
    this.invokable = invokable;

    callCount = 0;
  }

  public abstract ExpressionNode getReceiver();

  public boolean isCachedReceiverClass(final SAbstractObject receiver) {
    SClass currentRcvrClass = classOfReceiver(receiver, getReceiver());
    return currentRcvrClass == rcvrClass;
  }

  @Override
  public int getCallCount() {
    return callCount;
  }

  @Override
  public void resetCallCount() {
    callCount = 0;
  }

  @Override
  public CallTarget getCallTarget() {
    return invokable.getCallTarget();
  }

  @SlowPath
  @Override
  public Node getInlineTree() {
    return invokable.getTruffleInvokable();
//    if (method == null) {
//      return this;
//    }
  }
}
