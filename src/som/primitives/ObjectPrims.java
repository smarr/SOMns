package som.primitives;

import som.interpreter.Types;
import som.interpreter.nodes.BinaryMessageNode;
import som.interpreter.nodes.TernaryMessageNode;
import som.interpreter.nodes.UnaryMessageNode;
import som.vm.Universe;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SArray;
import som.vmobjects.SClass;
import som.vmobjects.SMethod;
import som.vmobjects.SObject;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;


public class ObjectPrims {
  public abstract static class EqualsEqualsPrim extends BinaryMessageNode {
    public EqualsEqualsPrim(final SSymbol selector, final Universe universe) { super(selector, universe); }
    public EqualsEqualsPrim(final EqualsEqualsPrim prim) { this(prim.selector, prim.universe); }

//    @Specialization(order = 10)
//    public SAbstractObject doObject(final boolean receiver, final SObject argument) {
//      if ((receiver == true  && argument == universe.trueObject) ||
//          (receiver == false && argument == universe.falseObject)) {
//        return universe.trueObject;
//      } else {
//        return universe.falseObject;
//      }
//    }
//
//    @Specialization(order = 11)
//    public SAbstractObject doObject(final SObject receiver, final boolean argument) {
//      if ((argument == true  && receiver == universe.trueObject) ||
//          (argument == false && receiver == universe.falseObject)) {
//        return universe.trueObject;
//      } else {
//        return universe.falseObject;
//      }
//    }

    @Specialization(order = 100)
    public SAbstractObject doObject(final Object receiver, final Object argument) {
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
    public Object doObject(final VirtualFrame frame, final Object receiver, final SSymbol selector) {
      SMethod invokable = Types.getClassOf(receiver, universe).lookupInvokable(selector);
      return invokable.invoke(frame.pack(), receiver);
    }
  }

  public abstract static class PerformInSuperclassPrim extends TernaryMessageNode {
    public PerformInSuperclassPrim(final SSymbol selector, final Universe universe) { super(selector, universe); }
    public PerformInSuperclassPrim(final PerformInSuperclassPrim prim) { this(prim.selector, prim.universe); }

    @Specialization
    public Object doSAbstractObject(final VirtualFrame frame,
        final SAbstractObject receiver, final SSymbol selector, final SClass  clazz) {
      SMethod invokable = clazz.lookupInvokable(selector);
      return invokable.invoke(frame.pack(), receiver);
    }
  }

  public abstract static class PerformWithArgumentsPrim extends TernaryMessageNode {
    public PerformWithArgumentsPrim(final SSymbol selector, final Universe universe) { super(selector, universe); }
    public PerformWithArgumentsPrim(final PerformWithArgumentsPrim prim) { this(prim.selector, prim.universe); }

    @Specialization
    public Object doObject(final VirtualFrame frame,
        final Object receiver, final SSymbol selector, final SArray  argsArr) {
      SMethod invokable = Types.getClassOf(receiver, universe).lookupInvokable(selector);
      return invokable.invoke(frame.pack(), receiver, argsArr.indexableFields);
    }
  }

  public abstract static class InstVarAtPrim extends BinaryMessageNode {
    public InstVarAtPrim(final SSymbol selector, final Universe universe) { super(selector, universe); }
    public InstVarAtPrim(final InstVarAtPrim prim) { this(prim.selector, prim.universe); }

    @Specialization
    public Object doSObject(final SObject receiver, final int idx) {
      return receiver.getField(idx - 1);
    }
  }

  public abstract static class InstVarAtPutPrim extends TernaryMessageNode {
    public InstVarAtPutPrim(final SSymbol selector, final Universe universe) { super(selector, universe); }
    public InstVarAtPutPrim(final InstVarAtPutPrim prim) { this(prim.selector, prim.universe); }

    @Specialization
    public Object doSObject(final SObject receiver, final int idx, final SAbstractObject val) {
      receiver.setField(idx - 1, val);
      return val;
    }

    @Specialization
    public Object doSObject(final SObject receiver, final int idx, final Object val) {
      SAbstractObject value = Types.asAbstractObject(val, universe);
      receiver.setField(idx - 1, value);
      return value;
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

    @Specialization
    public SAbstractObject doObject(final Object receiver) {
      return Types.getClassOf(receiver, universe);
    }
  }
}
