package som.interpreter.nodes.enforced;

import static som.interpreter.TruffleCompiler.transferToInterpreterAndInvalidate;
import som.interpreter.SArguments;
import som.interpreter.Types;
import som.interpreter.nodes.dispatch.DispatchChain;
import som.interpreter.nodes.enforced.IntercessionHandlerCache.AbstractIntercessionHandlerDispatch;
import som.vm.constants.Classes;
import som.vm.constants.Domain;
import som.vmobjects.SArray;
import som.vmobjects.SClass;
import som.vmobjects.SDomain;
import som.vmobjects.SObject;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;


public final class DomainAndClassDispatch {
  public static final int INLINE_CACHE_SIZE = 6;

  public static AbstractDomainAndClassDispatch create(
      final String intercessionHandlerSelector, final boolean executesEnforced,
      final SSymbol selector) {
    return new UninitializedDispatch(0, intercessionHandlerSelector,
        executesEnforced, selector);
  }

  public static AbstractDomainAndClassDispatch create(
      final String intercessionHandlerSelector, final boolean executesEnforced,
      final SSymbol selector, final SClass superSendClass) {
    return new UninitializedDispatch(0, intercessionHandlerSelector,
        executesEnforced, selector, superSendClass);
  }

  public abstract static class AbstractDomainAndClassDispatch extends Node
      implements DispatchChain {

    protected final SSymbol selector;
    protected final SClass superSendClass;
    protected final int depth;

    public AbstractDomainAndClassDispatch(final int depth,
        final String intercessionHandlerSelector, final boolean executesEnforced,
        final SSymbol selector, final SClass superSendClass) {
      super();
      this.depth    = depth;
      this.selector = selector;
      this.superSendClass = superSendClass;
    }

    public AbstractDomainAndClassDispatch(final int depth,
        final String intercessionHandlerSelector, final boolean executesEnforced,
        final SSymbol selector) {
      this(depth, intercessionHandlerSelector, executesEnforced, selector, null);
    }

    public abstract Object executeDispatch(final VirtualFrame frame, final Object[] args);
  }

  private static final class UninitializedDispatch
      extends AbstractDomainAndClassDispatch {
    private final String intercessionHandlerSelector;
    private final boolean executesEnforced;

    private UninitializedDispatch(final int depth,
        final String intercessionHandlerSelector, final boolean executesEnforced,
        final SSymbol selector) {
      super(depth, intercessionHandlerSelector, executesEnforced, selector);
      this.intercessionHandlerSelector = intercessionHandlerSelector;
      this.executesEnforced = executesEnforced;
    }

    private UninitializedDispatch(final int depth,
        final String intercessionHandlerSelector, final boolean executesEnforced,
        final SSymbol selector, final SClass superSendClass) {
      super(depth, intercessionHandlerSelector, executesEnforced, selector, superSendClass);
      this.intercessionHandlerSelector = intercessionHandlerSelector;
      this.executesEnforced = executesEnforced;
    }

    @Override
    public Object executeDispatch(final VirtualFrame frame, final Object[] args) {
      return specialize(args).
          executeDispatch(frame, args);
    }

    private AbstractDomainAndClassDispatch specialize(final Object[] args) {
      transferToInterpreterAndInvalidate("Initialize a DomainAndClassDispatch node.");

      Object rcvr = args[0];

      if (depth < INLINE_CACHE_SIZE) {
        // TODO: is it better to cache more aggressively for all cases, like in the primitive case?
        if (rcvr instanceof SObject) {
          return replace(new SObjectDispatch(depth, intercessionHandlerSelector,
              executesEnforced, selector, rcvr.getClass(), superSendClass));
        } else if (rcvr instanceof Object[]) {
          return replace(new ObjectArrayDispatch(depth,
              intercessionHandlerSelector, executesEnforced, selector,
              rcvr.getClass(), superSendClass));
        } else {
          return replace(new PrimitiveDispatch(depth,
              intercessionHandlerSelector, executesEnforced, selector,
              rcvr.getClass(),
              (superSendClass == null) ? Types.getClassOf(rcvr) : superSendClass));
        }
      }

      AbstractDomainAndClassDispatch headNode = determineChainHead();
      GenericDispatch generic = new GenericDispatch(intercessionHandlerSelector,
          executesEnforced, selector);
      return headNode.replace(generic);
    }

    private AbstractDomainAndClassDispatch determineChainHead() {
      Node i = this;
      while (i.getParent() instanceof AbstractDomainAndClassDispatch) {
        i = i.getParent();
      }
      return (AbstractDomainAndClassDispatch) i;
    }

    @Override
    public int lengthOfDispatchChain() {
      return 0;
    }
  }

  private abstract static class SpecializedDispatch
      extends AbstractDomainAndClassDispatch {
    @Child protected AbstractDomainAndClassDispatch next;
    @Child protected AbstractIntercessionHandlerDispatch dispatch;

    private SpecializedDispatch(final int depth,
        final String intercessionHandlerSelector, final boolean executesEnforced,
        final SSymbol selector, final SClass superSendClass) {
      super(depth, intercessionHandlerSelector, executesEnforced, selector, superSendClass);
      this.next = new UninitializedDispatch(depth + 1,
          intercessionHandlerSelector, executesEnforced, selector);
      this.dispatch = IntercessionHandlerCache.create(intercessionHandlerSelector,
          executesEnforced);
    }

    public abstract SObject getOwnerDomain(final Object obj);
    public abstract SClass  getSOMClass(final Object obj);

    public final Object doDispatch(final VirtualFrame frame, final Object[] args) {
      Object  rcvr = args[0];
      SObject rcvrDomain    =  getOwnerDomain(rcvr);
      SObject currentDomain = SArguments.domain(frame);
      SClass  rcvrClass = getSOMClass(rcvr);

      Object[] arguments = SArguments.createSArgumentsArray(false, currentDomain,
          rcvrDomain, selector,
          SArray.fromArgArrayWithReceiverToSArrayWithoutReceiver(args, currentDomain),
          rcvr, rcvrClass);

      return dispatch.executeDispatch(frame, rcvrDomain, arguments);
    }

    @Override
    public int lengthOfDispatchChain() {
      return 1 + next.lengthOfDispatchChain();
    }
  }

  private abstract static class AbstractCachedDispatch
      extends SpecializedDispatch {

    protected final Class<?> clazz;

    private AbstractCachedDispatch(final int depth,
        final String intercessionHandlerSelector, final boolean executesEnforced,
        final SSymbol selector, final Class<?> clazz, final SClass superSendClass) {
      super(depth, intercessionHandlerSelector, executesEnforced, selector,
          superSendClass);
      this.clazz = clazz;
    }

    @Override
    public final Object executeDispatch(final VirtualFrame frame,
        final Object[] args) {
      if (args[0].getClass() == clazz) {
        return doDispatch(frame, args);
      } else {
        return next.executeDispatch(frame, args);
      }
    }
  }

  private static final class SObjectDispatch
      extends AbstractCachedDispatch {

    private SObjectDispatch(final int depth,
        final String intercessionHandlerSelector, final boolean executesEnforced,
        final SSymbol selector, final Class<?> clazz, final SClass superSendClass) {
      super(depth, intercessionHandlerSelector, executesEnforced, selector,
          clazz, superSendClass);
    }

    @Override
    public SObject getOwnerDomain(final Object obj) {
      SObject o = CompilerDirectives.unsafeCast(obj, SObject.class, true);
      return o.getDomain();
    }

    @Override
    public SClass getSOMClass(final Object obj) {
      if (superSendClass == null) {
        SObject o = CompilerDirectives.unsafeCast(obj, SObject.class, true);
        return o.getSOMClass();
      } else {
        return superSendClass;
      }
    }
  }

  private static final class ObjectArrayDispatch
      extends AbstractCachedDispatch {

    private ObjectArrayDispatch(final int depth,
        final String intercessionHandlerSelector, final boolean executesEnforced,
        final SSymbol selector, final Class<?> clazz, final SClass superSendClass) {
      super(depth, intercessionHandlerSelector, executesEnforced, selector,
          clazz, superSendClass);
    }

    @Override
    public SObject getOwnerDomain(final Object obj) {
      Object[] arr = CompilerDirectives.unsafeCast(obj, Object[].class, true);
      return SArray.getOwner(arr);
    }

    @Override
    public SClass getSOMClass(final Object obj) {
      if (superSendClass == null) {
        return Classes.arrayClass;
      } else {
        return superSendClass;
      }
    }
  }

  private static final class PrimitiveDispatch extends AbstractCachedDispatch {
    private final SClass primitiveClass;

    private PrimitiveDispatch(final int depth,
        final String intercessionHandlerSelector, final boolean executesEnforced,
        final SSymbol selector, final Class<?> clazz,
        final SClass primitiveClass) {
      super(depth, intercessionHandlerSelector, executesEnforced, selector,
          clazz, primitiveClass);
      this.primitiveClass = primitiveClass;
    }

    @Override
    public SObject getOwnerDomain(final Object obj) {
      return Domain.standard;
    }

    @Override
    public SClass getSOMClass(final Object obj) {
      return primitiveClass;
    }
  }

  private static final class GenericDispatch
      extends AbstractDomainAndClassDispatch {
    @Child protected AbstractIntercessionHandlerDispatch dispatch;

    private GenericDispatch(final String intercessionHandlerSelector,
        final boolean executesEnforced, final SSymbol selector) {
      super(0, intercessionHandlerSelector, executesEnforced, selector);
      this.dispatch = IntercessionHandlerCache.create(intercessionHandlerSelector,
          executesEnforced);
    }

    @Override
    public Object executeDispatch(final VirtualFrame frame,
        final Object[] args) {
      // specialization via inlining and splitting needs to make sure that
      // we always have maximally a polymorphic chain, not a megamorphic
      // because getOwner and getClassOf are not supported for compilation.
      // This is mostly to facilitate performance debugging, and avoid errors.
      CompilerAsserts.neverPartOfCompilation(
          "The generic case should never be part of compilation.");

      Object  rcvr = args[0];
      SObject rcvrDomain    = SDomain.getOwner(rcvr);
      SObject currentDomain = SArguments.domain(frame);
      SClass  rcvrClass = Types.getClassOf(rcvr);

      Object[] arguments = SArguments.createSArgumentsArray(false, currentDomain,
          rcvrDomain, selector,
          SArray.fromArgArrayWithReceiverToSArrayWithoutReceiver(args, currentDomain),
          rcvr, rcvrClass);

      return dispatch.executeDispatch(frame, rcvrDomain, arguments);
    }

    @Override
    public int lengthOfDispatchChain() {
      return 1000;
    }
  }
}
