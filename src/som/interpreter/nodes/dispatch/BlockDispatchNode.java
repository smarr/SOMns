package som.interpreter.nodes.dispatch;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;

import som.interpreter.Invokable;
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

  protected static final DirectCallNode createAtomicCallNode(final Object[] arguments) {
    return Truffle.getRuntime().createDirectCallNode(
        getMethod(arguments).getAtomicCallTarget());
  }

  protected static final IndirectCallNode createIndirectCall() {
    return Truffle.getRuntime().createIndirectCallNode();
  }

  @Specialization(guards = {"isSameMethod(arguments, cached)", "forAtomic()"})
  public Object activateCachedAtomicBlock(final Object[] arguments,
      @Cached("getMethod(arguments)") final SInvokable cached,
      @Cached("createAtomicCallNode(arguments)") final DirectCallNode call) {
    return call.call(arguments);
  }

  @Specialization(guards = {"isSameMethod(arguments, cached)"})
  public Object activateCachedBlock(final Object[] arguments,
      @Cached("getMethod(arguments)") final SInvokable cached,
      @Cached("createCallNode(arguments)") final DirectCallNode call) {
    return call.call(arguments);
  }

  protected boolean forAtomic() {
    // TODO: seems a bit expensive,
    // might want to optimize for interpreter first iteration speed
    RootNode root = getRootNode();
    if (root instanceof Invokable) {
      return ((Invokable) root).isAtomic();
    } else {
      // TODO: need to think about integration with actors, but, that's a
      // later research project
      return false;
    }
  }

  @Specialization(replaces = "activateCachedAtomicBlock", guards = "forAtomic()")
  public Object activateBlockAtomic(final Object[] arguments,
      @Cached("createIndirectCall()") final IndirectCallNode indirect) {
    return indirect.call(getMethod(arguments).getAtomicCallTarget(), arguments);
  }

  @Specialization(replaces = "activateCachedBlock")
  public Object activateBlock(final Object[] arguments,
      @Cached("createIndirectCall()") final IndirectCallNode indirect) {
    return indirect.call(getMethod(arguments).getCallTarget(), arguments);
  }

}
