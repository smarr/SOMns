package som.primitives;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.oracle.truffle.api.dsl.NodeFactory;

import som.vm.Primitives.Specializer;


@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
@Repeatable(Primitive.Container.class)
public @interface Primitive {

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

  /** Pass VM object, i.e., execution context to node constructor. */
  boolean requiresContext() default false;

  /** Disabled for Dynamic Metrics. */
  boolean disabled() default false;

  /**
   * Disable the eager primitive wrapper.
   *
   * This should only be used for nodes that are specifically designed
   * to handle all possible cases themselves.
   */
  boolean noWrapper() default false;

  @Retention(RetentionPolicy.RUNTIME)
  @Target({ElementType.TYPE})
  public @interface Container {
    Primitive[] value();
  }

  abstract class NoChild implements NodeFactory<Object> {}
}
