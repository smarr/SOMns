package som.primitives;

import som.interpreter.nodes.messages.BinaryMonomorphicNode;
import som.interpreter.nodes.messages.TernaryMonomorphicNode;
import som.interpreter.nodes.messages.UnaryMonomorphicNode;
import som.vm.Universe;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SArray;
import som.vmobjects.SClass;
import som.vmobjects.SInteger;
import som.vmobjects.SMethod;
import som.vmobjects.SObject;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;


public class ObjectPrims {
  public abstract static class EqualsEqualsPrim extends BinaryMonomorphicNode {
    public EqualsEqualsPrim(final SSymbol selector, final Universe universe, final SClass rcvrClass, final SMethod invokable) { super(selector, universe, rcvrClass, invokable); }
    public EqualsEqualsPrim(final EqualsEqualsPrim prim) { this(prim.selector, prim.universe, prim.rcvrClass, prim.invokable); }

    @Override
    @Specialization(guards = "isCachedReceiverClass")
    public SAbstractObject doMonomorphic(final VirtualFrame frame,
        final SAbstractObject receiver,
        final SAbstractObject argument) {
      // TODO: make sure we can still override these messages!! Do use something like a monomorphic node specialization??? Check whether the lookup gets a primitive that has the proper node as its expression?
      if (receiver == argument) {
        return universe.trueObject;
      } else {
        return universe.falseObject;
      }
    }
  }

  public abstract static class PerformPrim extends BinaryMonomorphicNode {
    public PerformPrim(final SSymbol selector, final Universe universe, final SClass rcvrClass, final SMethod invokable) { super(selector, universe, rcvrClass, invokable); }
    public PerformPrim(final PerformPrim prim) { this(prim.selector, prim.universe, prim.rcvrClass, prim.invokable); }

    @Specialization(guards = "isCachedReceiverClass")
    public Object doMonomorphic(final VirtualFrame frame,
        final SAbstractObject receiver, final SSymbol selector) {
      SMethod invokable = receiver.getSOMClass(universe).lookupInvokable(selector);
      return invokable.invoke(frame.pack(), receiver, null);
    }
  }

  public abstract static class PerformInSuperclassPrim extends TernaryMonomorphicNode {
    public PerformInSuperclassPrim(final SSymbol selector, final Universe universe, final SClass rcvrClass, final SMethod invokable) { super(selector, universe, rcvrClass, invokable); }
    public PerformInSuperclassPrim(final PerformInSuperclassPrim prim) { this(prim.selector, prim.universe, prim.rcvrClass, prim.invokable); }

    @Specialization(guards = "isCachedReceiverClass")
    public SAbstractObject doMonomorphic(final VirtualFrame frame,
        final SAbstractObject receiver, final SSymbol selector, final SClass  clazz) {
      SMethod invokable = clazz.lookupInvokable(selector);
      return invokable.invoke(frame.pack(), receiver, null);
    }
  }

  public abstract static class PerformWithArgumentsPrim extends TernaryMonomorphicNode {
    public PerformWithArgumentsPrim(final SSymbol selector, final Universe universe, final SClass rcvrClass, final SMethod invokable) { super(selector, universe, rcvrClass, invokable); }
    public PerformWithArgumentsPrim(final PerformWithArgumentsPrim prim) { this(prim.selector, prim.universe, prim.rcvrClass, prim.invokable); }

    @Specialization(guards = "isCachedReceiverClass")
    public SAbstractObject doMonomorphic(final VirtualFrame frame,
        final SAbstractObject receiver, final SSymbol selector, final SArray  argsArr) {
      SMethod invokable = receiver.getSOMClass(universe).lookupInvokable(selector);
      return invokable.invoke(frame.pack(), receiver, argsArr.indexableFields);
    }
  }

  public abstract static class InstVarAtPrim extends BinaryMonomorphicNode {
    public InstVarAtPrim(final SSymbol selector, final Universe universe, final SClass rcvrClass, final SMethod invokable) { super(selector, universe, rcvrClass, invokable); }
    public InstVarAtPrim(final InstVarAtPrim prim) { this(prim.selector, prim.universe, prim.rcvrClass, prim.invokable); }

    @Specialization(guards = "isCachedReceiverClass")
    public SAbstractObject doSObject(final SObject receiver, final SInteger idx) {
      return receiver.getField(idx.getEmbeddedInteger() - 1);
    }
  }

  public abstract static class InstVarAtPutPrim extends TernaryMonomorphicNode {
    public InstVarAtPutPrim(final SSymbol selector, final Universe universe, final SClass rcvrClass, final SMethod invokable) { super(selector, universe, rcvrClass, invokable); }
    public InstVarAtPutPrim(final InstVarAtPutPrim prim) { this(prim.selector, prim.universe, prim.rcvrClass, prim.invokable); }

    @Specialization(guards = "isCachedReceiverClass")
    public SAbstractObject doSObject(final SObject receiver, final SInteger idx, final SAbstractObject val) {

      receiver.setField(idx.getEmbeddedInteger() - 1, val);

      return val;
    }
  }

  public abstract static class HaltPrim extends UnaryMonomorphicNode {
    public HaltPrim(final SSymbol selector, final Universe universe, final SClass rcvrClass, final SMethod invokable) { super(selector, universe, rcvrClass, invokable); }
    public HaltPrim(final HaltPrim prim) { this(prim.selector, prim.universe, prim.rcvrClass, prim.invokable); }

    @Override
    @Specialization(guards = "isCachedReceiverClass", order = 1)
    public SAbstractObject doMonomorphic(final VirtualFrame frame, final SAbstractObject receiver) {
      Universe.errorPrintln("BREAKPOINT");
      return receiver;
      // TODO: Make sure overriding still works!!
    }
  }

  public abstract static class ClassPrim extends UnaryMonomorphicNode {
    public ClassPrim(final SSymbol selector, final Universe universe, final SClass rcvrClass, final SMethod invokable) { super(selector, universe, rcvrClass, invokable); }
    public ClassPrim(final ClassPrim prim) { this(prim.selector, prim.universe, prim.rcvrClass, prim.invokable); }

    @Override
    @Specialization(guards = "isCachedReceiverClass", order = 1)
    public SAbstractObject doMonomorphic(final VirtualFrame frame, final SAbstractObject receiver) {
      return receiver.getSOMClass(universe);
    }
  }
}
