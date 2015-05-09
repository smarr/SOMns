package som.primitives;

import som.primitives.MethodPrimsFactory.HolderPrimFactory;
import som.primitives.MethodPrimsFactory.InvokeOnPrimFactory;
import som.primitives.MethodPrimsFactory.SignaturePrimFactory;


public final class MethodPrimitives extends Primitives {
  public MethodPrimitives(final boolean displayWarning) { super(displayWarning); }

  @Override
  public void installPrimitives() {
    installInstancePrimitive("signature",      SignaturePrimFactory.getInstance());
    installInstancePrimitive("holder",         HolderPrimFactory.getInstance());
    installInstancePrimitive("invokeOn:with:", InvokeOnPrimFactory.getInstance());
  }
}
