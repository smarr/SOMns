package som.primitives;

import som.interpreter.nodes.messages.UnaryMonomorphicNode;
import som.vm.Universe;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SClass;
import som.vmobjects.SMethod;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.dsl.Specialization;


public class MethodPrims {

  public abstract static class SignaturePrim extends UnaryMonomorphicNode {
    public SignaturePrim(final SSymbol selector, final Universe universe, final SClass rcvrClass, final SMethod invokable) { super(selector, universe, rcvrClass, invokable); }
    public SignaturePrim(final SignaturePrim prim) { this(prim.selector, prim.universe, prim.rcvrClass, prim.invokable); }

    @Specialization
    public SAbstractObject doSMethod(final SMethod receiver) {
      return receiver.getSignature();
    }
  }

  public abstract static class HolderPrim extends UnaryMonomorphicNode {
    public HolderPrim(final SSymbol selector, final Universe universe, final SClass rcvrClass, final SMethod invokable) { super(selector, universe, rcvrClass, invokable); }
    public HolderPrim(final HolderPrim prim) { this(prim.selector, prim.universe, prim.rcvrClass, prim.invokable); }

    @Specialization
    public SAbstractObject doSMethod(final SMethod receiver) {
      return receiver.getHolder();
    }
  }
}
