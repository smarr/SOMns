package som.compiler;


public enum AccessModifier {
  NOT_APPLICABLE,

  // NOTE: Order is important, for the lookup in SClass, we rely on it to
  //       define the sets of protected+public vs. public methods
  PRIVATE, PROTECTED, PUBLIC
}
