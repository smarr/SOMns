package dym.profiles;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import som.interpreter.Types;
import som.vmobjects.SClass;

import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.utilities.JSONHelper;
import com.oracle.truffle.api.utilities.JSONHelper.JSONArrayBuilder;
import com.oracle.truffle.api.utilities.JSONHelper.JSONObjectBuilder;

public class InvocationProfile extends Counter {

  private final Map<Arguments, Integer> argumentTypes;

  public InvocationProfile(final SourceSection source) {
    super(source);
    argumentTypes = new HashMap<>();
  }

  public void profileArguments(final Object[] args) {
    argumentTypes.merge(
        new Arguments(args), 1, Integer::sum);
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

    public JSONObjectBuilder toJson() {
      JSONObjectBuilder result = JSONHelper.object();

      JSONArrayBuilder javaTypes = JSONHelper.array();
      for (Class<?> c : argJavaTypes) {
        javaTypes.add(c.getSimpleName());
      }

      result.add("javaTypes", javaTypes);

      JSONArrayBuilder somTypes = JSONHelper.array();
      for (SClass c : argSomTypes) {
        somTypes.add(c.getName().getString());
      }
      result.add("somTypes", somTypes);
      return result;
    }
  }

  @Override
  public JSONArrayBuilder toJson() {
    JSONArrayBuilder result = JSONHelper.array();
    for (Entry<Arguments, Integer> e : argumentTypes.entrySet()) {
      JSONObjectBuilder invocations = e.getKey().toJson();
      invocations.add("invocations", e.getValue());
      result.add(invocations);
    }
    return result;
  }
}
