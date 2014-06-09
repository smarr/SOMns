package som.primitives.reflection;

import som.interpreter.Types;
import som.interpreter.nodes.nary.BinaryExpressionNode;
import som.vm.Universe;
import som.vmobjects.SInvokable;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;

public abstract class PerformPrim extends BinaryExpressionNode {
  private final Universe universe;
  public PerformPrim() { super(null); this.universe = Universe.current(); }

  @Specialization
  public final Object doObject(final VirtualFrame frame, final Object receiver, final SSymbol selector) {
    SInvokable invokable = Types.getClassOf(receiver, universe).lookupInvokable(selector);
    return invokable.invoke(receiver);
  }
}
