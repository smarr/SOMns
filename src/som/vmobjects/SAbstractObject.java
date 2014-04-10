package som.vmobjects;

import som.interpreter.Types;
import som.vm.Universe;


public abstract class SAbstractObject {

  public abstract SClass getSOMClass(final Universe universe);

  @Override
  public String toString() {
    return "a " + getSOMClass(Universe.current()).getName().getString();
  }

  public static final Object send(
      final String selectorString,
      final Object[] arguments,
      final Universe universe) {
    SSymbol selector = universe.symbolFor(selectorString);

    // Lookup the invokable
    SInvokable invokable = Types.getClassOf(arguments[0], universe).lookupInvokable(selector);

    return invokable.invoke(arguments);
  }

  public static final Object sendDoesNotUnderstand(final SSymbol selector,
      final Object[] arguments,
      final Universe universe) {
    // Allocate an array with enough room to hold all arguments
    int numArgs = ((arguments == null) ? 0 : arguments.length) - 1;

    SArray argumentsArray = universe.newArray(numArgs);
    for (int i = 0; i < numArgs; i++) {
      argumentsArray.setIndexableField(i, arguments[i + 1]);
    }

    Object[] args = new Object[] {arguments[0], selector, argumentsArray};

    return send("doesNotUnderstand:arguments:", args, universe);
  }

  public static final Object sendUnknownGlobal(final Object receiver,
      final SSymbol globalName, final Universe universe) {
    Object[] arguments = {receiver, globalName};
    return send("unknownGlobal:", arguments, universe);
  }

  public static final Object sendEscapedBlock(final Object receiver,
      final SBlock block, final Universe universe) {
    Object[] arguments = {receiver, block};
    return send("escapedBlock:", arguments, universe);
  }

}
