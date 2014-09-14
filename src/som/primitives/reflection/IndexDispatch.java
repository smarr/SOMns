package som.primitives.reflection;

import static som.interpreter.TruffleCompiler.transferToInterpreterAndInvalidate;
import som.interpreter.nodes.dispatch.DispatchChain;
import som.interpreter.objectstorage.FieldAccessorNode;
import som.interpreter.objectstorage.FieldAccessorNode.AbstractReadFieldNode;
import som.interpreter.objectstorage.FieldAccessorNode.AbstractWriteFieldNode;
import som.vmobjects.SClass;
import som.vmobjects.SObject;

import com.oracle.truffle.api.CompilerAsserts;
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
  public abstract Object executeDispatch(SObject obj, int index, Object value);

  private static final class UninitializedDispatchNode extends IndexDispatch {

    public UninitializedDispatchNode(final int depth) {
      super(depth);
    }

    private IndexDispatch specialize(final SClass clazz, final int index, final boolean read) {
      transferToInterpreterAndInvalidate("Initialize a dispatch node.");

      if (depth < INLINE_CACHE_SIZE) {
        IndexDispatch specialized;
        if (read) {
          specialized = new CachedReadDispatchNode(clazz, index,
            new UninitializedDispatchNode(depth + 1), depth);
        } else {
          specialized = new CachedWriteDispatchNode(clazz, index,
              new UninitializedDispatchNode(depth + 1), depth);
        }
        return replace(specialized);
      }

      IndexDispatch headNode = determineChainHead();
      return headNode.replace(new GenericDispatchNode());
    }

    @Override
    public Object executeDispatch(final SObject obj, final int index) {
      return specialize(obj.getSOMClass(), index, true).
          executeDispatch(obj, index);
    }

    @Override
    public Object executeDispatch(final SObject obj, final int index, final Object value) {
      return specialize(obj.getSOMClass(), index, false).
          executeDispatch(obj, index, value);
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

  private static final class CachedReadDispatchNode extends IndexDispatch {
    private final int index;
    private final SClass clazz;
    @Child private AbstractReadFieldNode access;
    // TODO: have a second cached class for the writing...
    @Child private IndexDispatch next;

    public CachedReadDispatchNode(final SClass clazz, final int index,
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
    public Object executeDispatch(final SObject obj, final int index, final Object value) {
      CompilerAsserts.neverPartOfCompilation();
      throw new RuntimeException("This should be never reached.");
    }

    @Override
    public int lengthOfDispatchChain() {
      return 1 + next.lengthOfDispatchChain();
    }
  }

  private static final class CachedWriteDispatchNode extends IndexDispatch {
    private final int index;
    private final SClass clazz;
    @Child private AbstractWriteFieldNode access;
    @Child private IndexDispatch next;

    public CachedWriteDispatchNode(final SClass clazz, final int index,
        final IndexDispatch next, final int depth) {
      super(depth);
      this.index = index;
      this.clazz = clazz;
      this.next = next;
      access = FieldAccessorNode.createWrite(index);
    }

    @Override
    public Object executeDispatch(final SObject obj, final int index) {
      CompilerAsserts.neverPartOfCompilation();
      throw new RuntimeException("This should be never reached.");
    }

    @Override
    public Object executeDispatch(final SObject obj, final int index, final Object value) {
      if (this.index == index && this.clazz == obj.getSOMClass()) {
        return access.write(obj, value);
      } else {
        return next.executeDispatch(obj, index, value);
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
    public Object executeDispatch(final SObject obj, final int index, final Object value) {
      obj.setField(index, value);
      return value;
    }

    @Override
    public int lengthOfDispatchChain() {
      return 1000;
    }
  }
}
