package somns.interpreter.nodes;

import somns.compiler.MixinBuilder.MixinDefinitionId;


public interface ISpecialSend {
  boolean isSuperSend();

  MixinDefinitionId getEnclosingMixinId();
}
