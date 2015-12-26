package som.interpreter.nodes.dispatch;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;


public final class TypeConditionExactProfile {

  protected final Class<?> expected;

  public TypeConditionExactProfile(final Class<?> expected) {
    this.expected = expected;
  }

  @CompilationFinal protected Class<?> cachedClass;

  public <T> boolean instanceOf(final T value) {
    // Field needs to be cached in local variable for thread safety and startup
    // speed.
    assert value != null;

    Class<?> clazz = cachedClass;
    if (clazz != Object.class) {
      if (clazz != null && clazz == value.getClass()) {
        return true;
      } else {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        if (clazz == null) {
          if (expected.isInstance(value)) {
            cachedClass = value.getClass();
            return true;
          } else {
            cachedClass = Object.class;
            return false;
          }
        }
      }
    }
    return expected.isInstance(value);
  }
}
