package som.primitives;

import som.interpreter.nodes.BinaryMessageNode;
import som.interpreter.nodes.TernaryMessageNode;
import som.interpreter.nodes.UnaryMessageNode;
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
  public abstract static class EqualsEqualsPrim extends BinaryMessageNode {
    public EqualsEqualsPrim(final SSymbol selector, final Universe universe) { super(selector, universe); }
    public EqualsEqualsPrim(final EqualsEqualsPrim prim) { this(prim.selector, prim.universe); }

    @Specialization
    public SAbstractObject doSAbstractObject(final SAbstractObject receiver,
        final SAbstractObject argument) {
      // TODO: make sure we can still override these messages!! Do use something like a monomorphic node specialization??? Check whether the lookup gets a primitive that has the proper node as its expression?
      if (receiver == argument) {
        return universe.trueObject;
      } else {
        return universe.falseObject;
      }
    }
  }

  public abstract static class PerformPrim extends BinaryMessageNode {
    public PerformPrim(final SSymbol selector, final Universe universe) { super(selector, universe); }
    public PerformPrim(final PerformPrim prim) { this(prim.selector, prim.universe); }

    @Specialization
    public SAbstractObject doSAbstractObject(final VirtualFrame frame, final SAbstractObject receiver, final SSymbol selector) {
      SMethod invokable = receiver.getSOMClass(universe).lookupInvokable(selector);
      return invokable.invoke(frame.pack(), receiver, null);
    }
  }

  public abstract static class PerformInSuperclassPrim extends TernaryMessageNode {
    public PerformInSuperclassPrim(final SSymbol selector, final Universe universe) { super(selector, universe); }
    public PerformInSuperclassPrim(final PerformInSuperclassPrim prim) { this(prim.selector, prim.universe); }

    @Specialization
    public SAbstractObject doSAbstractObject(final VirtualFrame frame,
        final SAbstractObject receiver, final SSymbol selector, final SClass  clazz) {
      SMethod invokable = clazz.lookupInvokable(selector);
      return invokable.invoke(frame.pack(), receiver, null);
    }
  }

  public abstract static class PerformWithArgumentsPrim extends TernaryMessageNode {
    public PerformWithArgumentsPrim(final SSymbol selector, final Universe universe) { super(selector, universe); }
    public PerformWithArgumentsPrim(final PerformWithArgumentsPrim prim) { this(prim.selector, prim.universe); }

    @Specialization
    public SAbstractObject doSAbstractObject(final VirtualFrame frame,
        final SAbstractObject receiver, final SSymbol selector, final SArray  argsArr) {
      SMethod invokable = receiver.getSOMClass(universe).lookupInvokable(selector);
      return invokable.invoke(frame.pack(), receiver, argsArr.indexableFields);
    }
  }

  public abstract static class InstVarAtPrim extends BinaryMessageNode {
    public InstVarAtPrim(final SSymbol selector, final Universe universe) { super(selector, universe); }
    public InstVarAtPrim(final InstVarAtPrim prim) { this(prim.selector, prim.universe); }

    @Specialization
    public SAbstractObject doSObject(final SObject receiver, final SInteger idx) {
      return receiver.getField(idx.getEmbeddedInteger() - 1);
    }
  }

  public abstract static class InstVarAtPutPrim extends TernaryMessageNode {
    public InstVarAtPutPrim(final SSymbol selector, final Universe universe) { super(selector, universe); }
    public InstVarAtPutPrim(final InstVarAtPutPrim prim) { this(prim.selector, prim.universe); }

    @Specialization
    public SAbstractObject doSObject(final SObject receiver, final SInteger idx, final SAbstractObject val) {
      receiver.setField(idx.getEmbeddedInteger() - 1, val);
      return val;
    }
  }

  public abstract static class HaltPrim extends UnaryMessageNode {
    public HaltPrim(final SSymbol selector, final Universe universe) { super(selector, universe); }
    public HaltPrim(final HaltPrim prim) { this(prim.selector, prim.universe); }

    @Specialization
    public SAbstractObject doSAbstractObject(final SAbstractObject receiver) {
      Universe.errorPrintln("BREAKPOINT");
      return receiver;
      // TODO: Make sure overriding still works!!
    }
  }

  public abstract static class ClassPrim extends UnaryMessageNode {
    public ClassPrim(final SSymbol selector, final Universe universe) { super(selector, universe); }
    public ClassPrim(final ClassPrim prim) { this(prim.selector, prim.universe); }

    @Specialization
    public SAbstractObject doSAbstractObject(final SAbstractObject receiver) {
      return receiver.getSOMClass(universe);
    }
  }
}
