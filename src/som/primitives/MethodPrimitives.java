package som.primitives;

import som.primitives.MethodPrimsFactory.HolderPrimFactory;
import som.primitives.MethodPrimsFactory.SignaturePrimFactory;
import som.vm.Universe;


public class MethodPrimitives extends Primitives {
  public MethodPrimitives(final Universe universe) {
    super(universe);
  }

  @Override
  public void installPrimitives() {
    installInstancePrimitive("signature", SignaturePrimFactory.getInstance());
    installInstancePrimitive("holder", HolderPrimFactory.getInstance());
  }
}
