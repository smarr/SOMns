package som.primitives;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import som.vm.Primitives.Specializer;


@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
@Repeatable(Primitive.Container.class)
public @interface Primitive {

  /** Name of the selector, for which the primitive is to be installed. */
  String primitive() default "";
  // TODO: additional hints for instantiation

  /** Selector for eager replacement. */
  String selector() default "";

  /** Expected type of receiver for eager replacement. */
  Class<?>[] receiverType() default {};

  /**
   * The specializer is used to check when eager specialization is to be
   * applied and to construct the node. */
  Class<? extends Specializer> specializer() default Specializer.class;

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
}
