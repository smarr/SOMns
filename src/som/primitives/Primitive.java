package som.primitives;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface Primitive {

  /** Name of the selector, for which the primitive is to be installed. */
  String[] value();
  // TODO: additional hints for instantiation

}
