package som.vmobjects;

import som.interpreter.Types;
import som.vm.Symbols;

import com.oracle.truffle.api.CompilerAsserts;


public abstract class SAbstractObject {

  protected final SAbstractObject enclosingObject;

  public SAbstractObject(final SAbstractObject enclosingObject) {
    this.enclosingObject = enclosingObject;
  }

  public abstract SClass getSOMClass();

  public SAbstractObject getEnclosingObject() {
    return enclosingObject;
  }

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
    SInvokable invokable = Types.getClassOf(arguments[0]).lookupInvokable(selector);

    return invokable.invoke(arguments);
  }

  public static final Object sendEscapedBlock(final Object receiver,
      final SBlock block) {
    Object[] arguments = {receiver, block};
    return send("escapedBlock:", arguments);
  }

}
