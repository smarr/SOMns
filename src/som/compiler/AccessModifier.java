package som.compiler;


public enum AccessModifier {
  // Those things also get Access Modifiers, and not yet sure how to handle them
  // so, keep them distinct.
  CLASS_CACHE_SLOT, OBJECT_INSTANTIATION_METHOD, BLOCK_METHOD,

  // NOTE: Order is important, for the lookup in SClass, we rely on it to
  //       define the sets of protected+public vs. public methods
  PRIVATE, PROTECTED, PUBLIC
}
