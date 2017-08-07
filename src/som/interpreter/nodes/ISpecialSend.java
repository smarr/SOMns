package som.interpreter.nodes;

import som.compiler.MixinBuilder.MixinDefinitionId;


public interface ISpecialSend {
  boolean isSuperSend();

  MixinDefinitionId getEnclosingMixinId();
}
