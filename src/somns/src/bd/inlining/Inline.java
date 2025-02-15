package bd.inlining;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


/**
 * Annotation that marks nodes as inlined versions for language constructs.
 * More complex nodes/constructs are to be replaced by these nodes in the parser.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
@Repeatable(Inline.Container.class)
public @interface Inline {

  /**
   * Selector, i.e., identifier for a node to determine that inlining applies.
   *
   * @return a string identifying the node
   */
  String selector();

  /**
   * The arguments, by index, which need to be inlinable to make this node applicable.
   *
   * @return indexes of the inlinable argument nodes
   */
  int[] inlineableArgIdx();

  /**
   * The argument nodes might need extra temporary variables when being inlined.
   *
   * @return indexes of the argument nodes that need a temporary variable
   */
  int[] introduceTemps() default {};

  /**
   * Additional values to be provided as arguments to the node constructor.
   *
   * @return array value identifies, currently either {@link True} or {@link False}
   */
  Class<?>[] additionalArgs() default {};

  /**
   * Disabled for Dynamic Metrics.
   *
   * @return true if inlining should not be applied, false otherwise
   */
  boolean disabled() default false;

  /**
   * Represents a true value for an additional argument.
   * Can be used for {@link #additionalArgs()}.
   */
  class True {}

  /**
   * Represents a false value for an additional argument.
   * Can be used for {@link #additionalArgs()}.
   */
  class False {}

  @Retention(RetentionPolicy.RUNTIME)
  @Target({ElementType.TYPE})
  @interface Container {
    Inline[] value();
  }
}
