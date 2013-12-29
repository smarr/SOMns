package som.interpreter.nodes;

import static som.interpreter.TruffleCompiler.transferToInterpreterAndInvalidate;
import som.vm.Universe;
import som.vmobjects.SClass;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.frame.VirtualFrame;

public class SelfReadNode extends ContextualNode {
  public SelfReadNode(final int contextLevel) {
    super(contextLevel);
  }

  @Override
  public Object executeGeneric(final VirtualFrame frame) {
    return determineOuterSelf(frame);
  }

  public abstract static class AbstractSuperReadNode extends SelfReadNode {
    private AbstractSuperReadNode(final int contextLevel) { super(contextLevel); }
  }

  public static class UninitializedSuperReadNode extends SelfReadNode {
    private final SSymbol holderClass;
    private final boolean classSide;

    public UninitializedSuperReadNode(final int contextLevel,
        final SSymbol holderClass, final boolean classSide) {
      super(contextLevel);
      this.holderClass = holderClass;
      this.classSide   = classSide;
    }

    @Override
    public Object executeGeneric(final VirtualFrame frame) {
      transferToInterpreterAndInvalidate("UninitializedSuperReadNode");
      SClass clazz = (SClass) Universe.current().getGlobal(holderClass);
      if (classSide) {
        clazz = clazz.getSOMClass(Universe.current());
      }
      SuperReadNode node = new SuperReadNode(contextLevel, (SClass) clazz.getSuperClass());
      return replace(node).executeGeneric(frame);
    }
  }

  public static class SuperReadNode extends SelfReadNode {
    private final SClass superClass;

    public SuperReadNode(final int contextLevel, final SClass superClass) {
      super(contextLevel);
      this.superClass = superClass;
    }

    public SClass getSuperClass() {
      return superClass;
    }
  }
}
