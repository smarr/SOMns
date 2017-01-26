package tools.dym.profiles;

import java.util.Arrays;

import com.oracle.truffle.api.utilities.JSONHelper;
import com.oracle.truffle.api.utilities.JSONHelper.JSONArrayBuilder;
import com.oracle.truffle.api.utilities.JSONHelper.JSONObjectBuilder;

import som.interpreter.Types;
import som.interpreter.objectstorage.ClassFactory;


public final class Arguments {

  private final Class<?>[] argJavaTypes;

  // TODO: do we need this, or is the first sufficient?
  //       this makes it language specific...
  private final ClassFactory[]   argSomTypes;

  Arguments(final Object[] arguments) {
    this.argJavaTypes = getJavaTypes(arguments);
    this.argSomTypes  = getSomTypes(arguments);
  }

  private static Class<?>[] getJavaTypes(final Object[] args) {
    return Arrays.stream(args).
        map(e -> e.getClass()).
        toArray(Class[]::new);  // remove the <?> because of checkstyle issue
  }

  private static ClassFactory[] getSomTypes(final Object[] args) {
    return Arrays.stream(args).
        map(e -> Types.getClassOf(e).getInstanceFactory()).
        toArray(ClassFactory[]::new);
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

  public boolean argTypeIs(final int idx, final String name) {
    return argSomTypes[idx].getClassName().getString().equals(name);
  }

  public String getArgType(final int idx) {
    return argSomTypes[idx].getClassName().getString();
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
    for (ClassFactory c : argSomTypes) {
      somTypes.add(c.getClassName().getString());
    }
    result.add("somTypes", somTypes);
    return result;
  }

  @Override
  public String toString() {
    String result = "";
    for (ClassFactory c : argSomTypes) {
      if ("".equals(result)) {
        result = c.getClassName().getString();
      } else {
        result += ", " + c.getClassName().getString();
      }
    }
    return result;
  }
}
