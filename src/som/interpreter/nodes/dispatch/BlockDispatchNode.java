package som.interpreter.nodes.dispatch;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.Node;

import som.vmobjects.SBlock;
import som.vmobjects.SInvokable;


public abstract class BlockDispatchNode extends Node {

  public abstract Object executeDispatch(Object[] arguments);

  protected static final boolean isSameMethod(final Object[] arguments,
      final SInvokable cached) {
    if (!(arguments[0] instanceof SBlock)) {
      return false;
    }
    return getMethod(arguments) == cached;
  }

  protected static final SInvokable getMethod(final Object[] arguments) {
    SInvokable method = ((SBlock) arguments[0]).getMethod();
    assert method.getNumberOfArguments() == arguments.length;
    return method;
  }

  protected static final DirectCallNode createCallNode(final Object[] arguments) {
    return Truffle.getRuntime().createDirectCallNode(
        getMethod(arguments).getCallTarget());
  }

  protected static final IndirectCallNode createIndirectCall() {
    return Truffle.getRuntime().createIndirectCallNode();
  }

  @Specialization(guards = "isSameMethod(arguments, cached)")
  public Object activateCachedBlock(final Object[] arguments,
      @Cached("getMethod(arguments)") final SInvokable cached,
      @Cached("createCallNode(arguments)") final DirectCallNode call) {
    return call.call(arguments);
  }

  @Specialization(replaces = "activateCachedBlock")
  public Object activateBlock(final Object[] arguments,
      @Cached("createIndirectCall()") final IndirectCallNode indirect) {
    return indirect.call(getMethod(arguments).getCallTarget(), arguments);
  }
}
