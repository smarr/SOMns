package dym.profiles;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import som.interpreter.Types;
import som.vmobjects.SClass;

import com.oracle.truffle.api.source.SourceSection;

public class InvocationProfile extends Counter {

  private final Map<Arguments, Integer> argumentTypes;

  public InvocationProfile(final SourceSection source) {
    super(source);
    argumentTypes = new HashMap<>();
  }

  public void profileArguments(final Object[] args) {
    argumentTypes.merge(
        new Arguments(args), 1, (old, one) -> old + one);
  }

  private static final class Arguments {

    private final Class<?>[] argJavaTypes;

    // TODO: do we need this, or is the first sufficient?
    //       this makes it language specific...
    private final SClass[]   argSomTypes;

    private Arguments(final Object[] arguments) {
      this.argJavaTypes = getJavaTypes(arguments);
      this.argSomTypes  = getSomTypes(arguments);
    }

    private static Class<?>[] getJavaTypes(final Object[] args) {
      return Arrays.stream(args).
          map(e -> e.getClass()).
          toArray(Class[]::new);  // remove the <?> because of checkstyle issue
    }

    private static SClass[] getSomTypes(final Object[] args) {
      return Arrays.stream(args).
          map(e -> Types.getClassOf(e)).
          toArray(SClass[]::new);
    }

    @Override
    public boolean equals(final Object obj) {
      if (super.equals(obj)) {
        return true;
      }
      if (!(obj instanceof Arguments)) {
        return false;
      }

      Arguments o = (Arguments) obj;

      return Arrays.equals(argJavaTypes, o.argJavaTypes)
          || Arrays.equals(argSomTypes,  o.argSomTypes);
    }

    @Override
    public int hashCode() {
      final int prime = 31;
      int result = 1;
      result = prime * result + Arrays.hashCode(argJavaTypes);
      result = prime * result + Arrays.hashCode(argSomTypes);
      return result;
    }
  }
}
