package som.primitives;

import som.interpreter.nodes.messages.UnaryMonomorphicNode;
import som.vm.Universe;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SClass;
import som.vmobjects.SMethod;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.dsl.Specialization;


public class ClassPrims {

  public abstract static class NamePrim extends UnaryMonomorphicNode {
    public NamePrim(final SSymbol selector, final Universe universe, final SClass rcvrClass, final SMethod invokable) { super(selector, universe, rcvrClass, invokable); }
    public NamePrim(final NamePrim prim) { this(prim.selector, prim.universe, prim.rcvrClass, prim.invokable); }

    @Specialization
    public SAbstractObject doSClass(final SClass receiver) {
      return receiver.getName();
    }
  }

  public abstract static class SuperClassPrim extends UnaryMonomorphicNode {
    public SuperClassPrim(final SSymbol selector, final Universe universe, final SClass rcvrClass, final SMethod invokable) { super(selector, universe, rcvrClass, invokable); }
    public SuperClassPrim(final SuperClassPrim prim) { this(prim.selector, prim.universe, prim.rcvrClass, prim.invokable); }

    @Specialization
    public SAbstractObject doSClass(final SClass receiver) {
      return receiver.getSuperClass();
    }
  }

  public abstract static class InstanceInvokablesPrim extends UnaryMonomorphicNode {
    public InstanceInvokablesPrim(final SSymbol selector, final Universe universe, final SClass rcvrClass, final SMethod invokable) { super(selector, universe, rcvrClass, invokable); }
    public InstanceInvokablesPrim(final InstanceInvokablesPrim prim) { this(prim.selector, prim.universe, prim.rcvrClass, prim.invokable); }

    @Specialization
    public SAbstractObject doSClass(final SClass receiver) {
      return receiver.getInstanceInvokables();
    }
  }

  public abstract static class InstanceFieldsPrim extends UnaryMonomorphicNode {
    public InstanceFieldsPrim(final SSymbol selector, final Universe universe, final SClass rcvrClass, final SMethod invokable) { super(selector, universe, rcvrClass, invokable); }
    public InstanceFieldsPrim(final InstanceFieldsPrim prim) { this(prim.selector, prim.universe, prim.rcvrClass, prim.invokable); }

    @Specialization
    public SAbstractObject doSClass(final SClass receiver) {
      return receiver.getInstanceFields();
    }
  }
}
