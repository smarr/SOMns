package som.primitives.reflection;

import som.interpreter.Types;
import som.interpreter.nodes.nary.TernaryExpressionNode;
import som.vm.Universe;
import som.vmobjects.SInvokable;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;

public abstract class PerformWithArgumentsPrim extends TernaryExpressionNode {
  private final Universe universe;
  public PerformWithArgumentsPrim() { this.universe = Universe.current(); }

  @Specialization
  public final Object doObject(final VirtualFrame frame,
      final Object receiver, final SSymbol selector, final Object[]  argsArr) {
    SInvokable invokable = Types.getClassOf(receiver, universe).lookupInvokable(selector);

    // need to unwrap argsArr and create a new Object array including the
    // receiver
    Object[] args = new Object[argsArr.length + 1];
    args[0] = receiver;
    System.arraycopy(argsArr, 0, args, 1, argsArr.length);

    return invokable.invoke(args);
  }
}
