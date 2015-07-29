package som.vmobjects;

import som.compiler.AccessModifier;
import som.interpreter.Types;
import som.interpreter.nodes.dispatch.Dispatchable;
import som.vm.Symbols;

import com.oracle.truffle.api.CompilerAsserts;


public abstract class SAbstractObject {

  public abstract SClass getSOMClass();
  public abstract boolean isValue();

  @Override
  public String toString() {
    CompilerAsserts.neverPartOfCompilation();
    SClass clazz = getSOMClass();
    if (clazz == null) {
      return "an Object(clazz==null)";
    }
    return "a " + clazz.getName().getString();
  }

  public static final Object send(
      final String selectorString,
      final Object[] arguments) {
    CompilerAsserts.neverPartOfCompilation("SAbstractObject.send()");
    SSymbol selector = Symbols.symbolFor(selectorString);

    // Lookup the invokable
    Dispatchable invokable = Types.getClassOf(arguments[0]).lookupMessage(
        selector, AccessModifier.PROTECTED);

    return invokable.invoke(arguments);
  }

  public static final Object sendEscapedBlock(final Object receiver,
      final SBlock block) {
    Object[] arguments = {receiver, block};
    return send("escapedBlock:", arguments);
  }
}
