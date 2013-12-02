package som.primitives;

import som.interpreter.nodes.UnaryMessageNode;
import som.vm.Universe;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SClass;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.dsl.Specialization;


public class ClassPrims {

  public abstract static class NamePrim extends UnaryMessageNode {
    public NamePrim(final SSymbol selector, final Universe universe) { super(selector, universe); }
    public NamePrim(final NamePrim prim) { this(prim.selector, prim.universe); }

    @Specialization
    public SAbstractObject doSClass(final SClass receiver) {
      return receiver.getName();
    }
  }

  public abstract static class SuperClassPrim extends UnaryMessageNode {
    public SuperClassPrim(final SSymbol selector, final Universe universe) { super(selector, universe); }
    public SuperClassPrim(final SuperClassPrim prim) { this(prim.selector, prim.universe); }

    @Specialization
    public SAbstractObject doSClass(final SClass receiver) {
      return receiver.getSuperClass();
    }
  }

  public abstract static class InstanceInvokablesPrim extends UnaryMessageNode {
    public InstanceInvokablesPrim(final SSymbol selector, final Universe universe) { super(selector, universe); }
    public InstanceInvokablesPrim(final InstanceInvokablesPrim prim) { this(prim.selector, prim.universe); }

    @Specialization
    public SAbstractObject doSClass(final SClass receiver) {
      return receiver.getInstanceInvokables();
    }
  }

  public abstract static class InstanceFieldsPrim extends UnaryMessageNode {
    public InstanceFieldsPrim(final SSymbol selector, final Universe universe) { super(selector, universe); }
    public InstanceFieldsPrim(final InstanceFieldsPrim prim) { this(prim.selector, prim.universe); }

    @Specialization
    public SAbstractObject doSClass(final SClass receiver) {
      return receiver.getInstanceFields();
    }
  }
}
