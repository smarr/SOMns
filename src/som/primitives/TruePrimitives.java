package som.primitives;

import som.interpreter.nodes.specialized.NotMessageNodeFactory;


public final class TruePrimitives extends Primitives {
  @Override
  public void installPrimitives() {
    installInstancePrimitive("not", NotMessageNodeFactory.getInstance());
//    installInstancePrimitive("ifTrue:",  IfTrueMessageNodeFactory.getInstance());
//    installInstancePrimitive("ifFalse:", IfFalseMessageNodeFactory.getInstance());
  }
}
