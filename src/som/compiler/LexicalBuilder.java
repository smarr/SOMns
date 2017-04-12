package som.compiler;

import som.interpreter.LexicalScope;

public class LexicalBuilder {

  private final LexicalBuilder outerBuilder;
  private int                  levelsToNextMixin = -1;
  private int                  levelsToNextMethod = -1;

  public LexicalBuilder(final LexicalBuilder outerBuilder) {
    this.outerBuilder = outerBuilder;
  }

  public LexicalScope getScope() {
    return null;
  }

  public LexicalBuilder getContextualSelf() {
    return outerBuilder;
  }

  private MixinBuilder bubbleToNextMixinOrNull() {
    LexicalBuilder builder = getContextualSelf();
    levelsToNextMixin = 0;

    while (true) {

      if (builder == null) {
        return null;
      }

      if (builder instanceof MixinBuilder) {
        return (MixinBuilder) builder;
      }
      builder = builder.getContextualSelf();

      levelsToNextMixin += 1;
    }
  }

  private MethodBuilder bubbleToNextMethodOrNull() {
    LexicalBuilder builder = getContextualSelf();
    levelsToNextMethod = 1;

    while (true) {

      if (builder == null) {
        return null;
      }

      if (builder instanceof MethodBuilder) {
        return (MethodBuilder) builder;
      }

      builder = builder.getContextualSelf();
      levelsToNextMethod += 1;
    }
  }

  public MixinBuilder getOuterSelf() {
    return bubbleToNextMixinOrNull();
  }

  public MethodBuilder getNextMethod() {
    return bubbleToNextMethodOrNull();
  }

  public int countLevelsToNextMixin() {
    if (levelsToNextMixin == -1) {
      bubbleToNextMixinOrNull();
    }
    return levelsToNextMixin;
  }

  public int countLevelsToNextMethod() {
    if (levelsToNextMethod == -1) {
      getNextMethod();
    }
    return levelsToNextMethod;
  }
}
