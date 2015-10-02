package som.interpreter.nodes.dispatch;

import som.vmobjects.SBlock;
import som.vmobjects.SInvokable;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.Node;


public abstract class BlockDispatchNode extends Node {

  public abstract Object executeDispatch(
      final VirtualFrame frame, final Object[] arguments);

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

  @Specialization(guards = "isSameMethod(arguments, cached)")
  public Object activateBlock(final VirtualFrame frame, final Object[] arguments,
      @Cached("getMethod(arguments)") final SInvokable cached,
      @Cached("createCallNode(arguments)") final DirectCallNode call) {
    return call.call(frame, arguments);
  }

  @CompilationFinal protected IndirectCallNode indirect;

  @Fallback
  public Object activateBlock(final VirtualFrame frame, final Object[] arguments) {
    if (indirect == null) {
      indirect = Truffle.getRuntime().createIndirectCallNode();
    }
    return indirect.call(frame, getMethod(arguments).getCallTarget(), arguments);
  }
}
