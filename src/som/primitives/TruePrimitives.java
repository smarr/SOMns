package som.primitives;

import som.interpreter.nodes.specialized.NotMessageNodeFactory;
import som.vm.Universe;


public final class TruePrimitives extends Primitives {
  public TruePrimitives(final Universe universe) {
    super(universe);
  }

  @Override
  public void installPrimitives() {
    installInstancePrimitive("not", NotMessageNodeFactory.getInstance());
//    installInstancePrimitive("ifTrue:",  IfTrueMessageNodeFactory.getInstance());
//    installInstancePrimitive("ifFalse:", IfFalseMessageNodeFactory.getInstance());
  }
}
