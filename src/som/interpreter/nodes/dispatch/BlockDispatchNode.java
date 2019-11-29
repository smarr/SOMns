package som.interpreter.nodes.dispatch;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;

import som.interpreter.Invokable;
import som.interpreter.TruffleCompiler;
import som.interpreter.nodes.dispatch.BlockDispatchNodeFactory.AtomicBlockDispatchNodeGen;
import som.interpreter.nodes.dispatch.BlockDispatchNodeFactory.GenericBlockDispatchNodeGen;
import som.vmobjects.SBlock;
import som.vmobjects.SInvokable;


public abstract class BlockDispatchNode extends Node {
  public abstract Object executeDispatch(Object[] arguments);

  public static BlockDispatchNode create() {
    return new UninitializedBlockDispatchNode();
  }

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

  @ImportStatic(BlockDispatchNode.class)
  public abstract static class GenericBlockDispatchNode extends BlockDispatchNode {
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

  @ImportStatic(BlockDispatchNode.class)
  public abstract static class AtomicBlockDispatchNode extends BlockDispatchNode {

    protected static final DirectCallNode createAtomicCallNode(final Object[] arguments) {
      return Truffle.getRuntime().createDirectCallNode(
          getMethod(arguments).getAtomicCallTarget());
    }

    @Specialization(guards = "isSameMethod(arguments, cached)")
    public Object activateCachedAtomicBlock(final Object[] arguments,
        @Cached("getMethod(arguments)") final SInvokable cached,
        @Cached("createAtomicCallNode(arguments)") final DirectCallNode call) {
      return call.call(arguments);
    }

    @Specialization(replaces = "activateCachedAtomicBlock")
    public Object activateBlockAtomic(final Object[] arguments,
        @Cached("createIndirectCall()") final IndirectCallNode indirect) {
      return indirect.call(getMethod(arguments).getAtomicCallTarget(), arguments);
    }
  }

  public static class UninitializedBlockDispatchNode
      extends BlockDispatchNode {
    protected static final DirectCallNode createAtomicCallNode(final Object[] arguments) {
      return Truffle.getRuntime().createDirectCallNode(
          getMethod(arguments).getAtomicCallTarget());
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

    @Override
    public Object executeDispatch(final Object[] arguments) {
      TruffleCompiler.transferToInterpreterAndInvalidate("Initialize a dispatch node.");
      BlockDispatchNode replacement = null;
      if (forAtomic()) {
        replacement = AtomicBlockDispatchNodeGen.create();
      } else {
        replacement = GenericBlockDispatchNodeGen.create();
      }
      return replace(replacement).executeDispatch(arguments);
    }
  }

}
