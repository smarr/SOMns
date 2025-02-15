package bd.primitives;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.oracle.truffle.api.dsl.NodeFactory;


/**
 * Annotation to be applied on node classes that implement the basic operations of a language.
 * Such basic operations are typically operators or built-in functions of a language.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
@Repeatable(Primitive.Container.class)
public @interface Primitive {

  /** Name of the class, on which the primitive is to be installed. */
  String className() default "";

  /** Name of the selector, for which the primitive is to be installed. */
  String primitive() default "";

  /** Selector for eager replacement. */
  String selector() default "";

  /** Specialize already during parsing. */
  boolean inParser() default true;

  /**
   * Expected type of receiver for eager replacement,
   * if given one of the types needs to match.
   */
  Class<?>[] receiverType() default {};

  /**
   * The specializer is used to check when eager specialization is to be
   * applied and to construct the node.
   */
  @SuppressWarnings("rawtypes")
  Class<? extends Specializer> specializer() default Specializer.class;

  /** A factory for an extra child node that is passed as last argument. */
  @SuppressWarnings("rawtypes")
  Class<? extends NodeFactory> extraChild() default NoChild.class;

  /** Pass array of evaluated arguments to node constructor. */
  boolean requiresArguments() default false;

  /** Disabled for Dynamic Metrics. */
  boolean disabled() default false;

  /**
   * Disable the eager primitive wrapper.
   *
   * This should only be used for nodes that are specifically designed
   * to handle all possible cases themselves.
   */
  boolean noWrapper() default false;

  /**
   * Whether the primitive is to be registered on the class-side of a class, instead of the
   * instance side.
   */
  boolean classSide() default false;

  @Retention(RetentionPolicy.RUNTIME)
  @Target({ElementType.TYPE})
  @interface Container {
    Primitive[] value();
  }

  abstract class NoChild implements NodeFactory<Object> {}
}
