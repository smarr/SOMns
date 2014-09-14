package som.primitives;

import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.SOMNode;
import som.primitives.SystemPrims.BinarySystemNode;
import som.vm.NotYetImplementedException;
import som.vm.Universe;
import som.vmobjects.SObject;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.dsl.Specialization;

public abstract class HasGlobalPrim extends BinarySystemNode {

  @Child private HasGlobalNode hasGlobal;

  public HasGlobalPrim() {
    super();
    hasGlobal = new UninitializedHasGlobal(0);
  }

  @Specialization(guards = "receiverIsSystemObject")
  public final boolean doSObject(final SObject receiver, final SSymbol argument) {
    return hasGlobal.hasGlobal(argument);
  }

  private abstract static class HasGlobalNode extends SOMNode {
    protected static final int INLINE_CACHE_SIZE = 6;

    private HasGlobalNode() { super(null); }
    public abstract boolean hasGlobal(SSymbol argument);

    @Override
    public ExpressionNode getFirstMethodBodyNode() {
      throw new NotYetImplementedException();
    }
  }

  private static final class UninitializedHasGlobal extends HasGlobalNode {
    private final int depth;
    private final Universe universe;

    public UninitializedHasGlobal(final int depth) {
      this.depth = depth;
      universe = Universe.current();
    }

    @Override
    public boolean hasGlobal(final SSymbol argument) {
      boolean hasGlobal = universe.hasGlobal(argument);

      if (hasGlobal) {
        return specialize(argument).hasGlobal(argument);
      }
      return false;
    }

    private HasGlobalNode specialize(final SSymbol argument) {
      if (depth < INLINE_CACHE_SIZE) {
        return replace(new CachedHasGlobal(argument, depth));
      } else {
        HasGlobalNode head = this;
        while (head.getParent() instanceof HasGlobalNode) {
          head = (HasGlobalNode) head.getParent();
        }
        return head.replace(new HasGlobalFallback());
      }
    }
  }

  private static final class CachedHasGlobal extends HasGlobalNode {
    private final int depth;
    private final SSymbol name;
    @Child private HasGlobalNode next;

    public CachedHasGlobal(final SSymbol name, final int depth) {
      this.depth = depth;
      this.name  = name;
      next = new UninitializedHasGlobal(this.depth + 1);
    }

    @Override
    public boolean hasGlobal(final SSymbol argument) {
      if (name == argument) {
        return true;
      } else {
        return next.hasGlobal(argument);
      }
    }
  }

  private static final class HasGlobalFallback extends HasGlobalNode {
    private final Universe universe = Universe.current();

    @Override
    public boolean hasGlobal(final SSymbol argument) {
      return universe.hasGlobal(argument);
    }
  }
}
