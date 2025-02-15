package bd.basic;

/**
 * An <code>IdProvider</code> establishes a mapping from strings to unique identifies.
 *
 * <p>These identifiers are used for instance to select primitives or inlinable elements based
 * on identifiers in user-language source code.
 *
 * @param <Id> the identifier type used for instance to allow a mapping to primitives or
 *          inlinable elements, typically some form of interned string construct
 */
public interface IdProvider<Id> {
  /**
   * Gets a Java string and needs to return an identifier. Typically, this is some form of
   * symbol or interned string that can be safely compared with reference equality.
   *
   * @param id the string version of the id
   * @return the unique identifier
   */
  Id getId(String id);
}
