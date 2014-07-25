package som.primitives.reflection;

import static som.interpreter.TruffleCompiler.transferToInterpreterAndInvalidate;
import som.interpreter.nodes.dispatch.DispatchChain;
import som.interpreter.objectstorage.FieldAccessorNode;
import som.interpreter.objectstorage.FieldAccessorNode.AbstractReadFieldNode;
import som.vmobjects.SClass;
import som.vmobjects.SObject;

import com.oracle.truffle.api.nodes.Node;


public abstract class IndexDispatch extends Node implements DispatchChain {
  public static final int INLINE_CACHE_SIZE = 6;

  public static IndexDispatch create() {
    return new UninitializedDispatchNode(0);
  }

  protected final int depth;

  public IndexDispatch(final int depth) {
    this.depth = depth;
  }

  public abstract Object executeDispatch(SObject obj, int index);

  private static final class UninitializedDispatchNode extends IndexDispatch {

    public UninitializedDispatchNode(final int depth) {
      super(depth);
    }

    private IndexDispatch specialize(final SClass clazz, final int index) {
      transferToInterpreterAndInvalidate("Initialize a dispatch node.");

      if (depth < INLINE_CACHE_SIZE) {
        CachedDispatchNode specialized = new CachedDispatchNode(clazz, index,
            new UninitializedDispatchNode(depth + 1), depth);
        return replace(specialized);
      }

      IndexDispatch headNode = determineChainHead();
      return headNode.replace(new GenericDispatchNode());
    }

    @Override
    public Object executeDispatch(final SObject obj, final int index) {
      return specialize(obj.getSOMClass(), index).
          executeDispatch(obj, index);
    }

    private IndexDispatch determineChainHead() {
      Node i = this;
      while (i.getParent() instanceof IndexDispatch) {
        i = i.getParent();
      }
      return (IndexDispatch) i;
    }

    @Override
    public int lengthOfDispatchChain() {
      return 0;
    }
  }

  private static final class CachedDispatchNode extends IndexDispatch {
    private final int index;
    private final SClass clazz;
    @Child private AbstractReadFieldNode access;
    @Child private IndexDispatch next;

    public CachedDispatchNode(final SClass clazz, final int index,
        final IndexDispatch next, final int depth) {
      super(depth);
      this.index = index;
      this.clazz = clazz;
      this.next = next;
      access = FieldAccessorNode.createRead(index);
    }

    @Override
    public Object executeDispatch(final SObject obj, final int index) {
      if (this.index == index && this.clazz == obj.getSOMClass()) {
        return access.read(obj);
      } else {
        return next.executeDispatch(obj, index);
      }
    }

    @Override
    public int lengthOfDispatchChain() {
      return 1 + next.lengthOfDispatchChain();
    }
  }

  private static final class GenericDispatchNode extends IndexDispatch {

    public GenericDispatchNode() {
      super(0);
    }

    @Override
    public Object executeDispatch(final SObject obj, final int index) {
      return obj.getField(index);
    }

    @Override
    public int lengthOfDispatchChain() {
      return 1000;
    }
  }
}
