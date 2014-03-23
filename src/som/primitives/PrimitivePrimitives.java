package som.primitives;

import som.primitives.MethodPrimsFactory.HolderPrimFactory;
import som.primitives.MethodPrimsFactory.SignaturePrimFactory;
import som.vm.Universe;


public final class PrimitivePrimitives extends Primitives {
  public PrimitivePrimitives(final Universe universe) {
    super(universe);
  }

  @Override
  public void installPrimitives() {
    installInstancePrimitive("signature", SignaturePrimFactory.getInstance());
    installInstancePrimitive("holder", HolderPrimFactory.getInstance());
  }
}
