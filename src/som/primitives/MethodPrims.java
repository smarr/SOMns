package som.primitives;

import som.interpreter.nodes.UnaryMessageNode;
import som.vm.Universe;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SMethod;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.dsl.Specialization;


public class MethodPrims {

  public abstract static class SignaturePrim extends UnaryMessageNode {
    public SignaturePrim(final SSymbol selector, final Universe universe) { super(selector, universe); }
    public SignaturePrim(final SignaturePrim prim) { this(prim.selector, prim.universe); }

    @Specialization
    public SAbstractObject doSMethod(final SMethod receiver) {
      return receiver.getSignature();
    }
  }

  public abstract static class HolderPrim extends UnaryMessageNode {
    public HolderPrim(final SSymbol selector, final Universe universe) { super(selector, universe); }
    public HolderPrim(final HolderPrim prim) { this(prim.selector, prim.universe); }

    @Specialization
    public SAbstractObject doSMethod(final SMethod receiver) {
      return receiver.getHolder();
    }
  }
}
