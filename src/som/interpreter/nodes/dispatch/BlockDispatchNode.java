package som.interpreter.nodes.dispatch;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
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

  @CompilationFinal private boolean initialized;
  @CompilationFinal private boolean isAtomic;

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

  protected CallTarget getCallTarget(final Object[] arguments) {
    SInvokable blockMethod = getMethod(arguments);
    if (forAtomic()) {
      return blockMethod.getAtomicCallTarget();
    } else {
      return blockMethod.getCallTarget();
    }
  }

  protected final DirectCallNode createCallNode(final Object[] arguments) {
    CallTarget ct = getCallTarget(arguments);
    return Truffle.getRuntime().createDirectCallNode(ct);
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
    return indirect.call(getCallTarget(arguments), arguments);
  }

  protected boolean forAtomic() {
    if (initialized) {
      return isAtomic;
    }

    CompilerDirectives.transferToInterpreterAndInvalidate();

    // TODO: seems a bit expensive,
    // might want to optimize for interpreter first iteration speed
    RootNode root = getRootNode();
    if (root instanceof Invokable) {
      isAtomic = ((Invokable) root).isAtomic();
    } else {
      // TODO: need to think about integration with actors, but, that's a
      // later research project
      return false;
    }
    initialized = true;
    return isAtomic;
  }
}
