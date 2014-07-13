package som.vmobjects;

import som.interpreter.Types;
import som.vm.Universe;

import com.oracle.truffle.api.CompilerAsserts;


public abstract class SAbstractObject {

  public abstract SClass getSOMClass();

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
      final Object[] arguments,
      final SObject domain,
      final boolean enforced,
      final Universe universe) {
    CompilerAsserts.neverPartOfCompilation("SAbstractObject.send()");
    SSymbol selector = universe.symbolFor(selectorString);

    // Lookup the invokable
    SInvokable invokable = Types.getClassOf(arguments[0]).lookupInvokable(selector);

    return invokable.invoke(domain, enforced, arguments);
  }

  public static final Object sendDoesNotUnderstand(final SSymbol selector,
      final Object[] arguments,
      final SObject domain,
      final boolean enforced,
      final Universe universe) {
    CompilerAsserts.neverPartOfCompilation("SAbstractObject.sendDNU()");
    assert arguments != null;

    // Allocate an array to hold the arguments, without receiver
    Object[] argumentsArray = SArray.fromArgArrayWithReceiverToSArrayWithoutReceiver(
        arguments, domain);
    Object[] args = new Object[] {arguments[0], selector, argumentsArray};
    return send("doesNotUnderstand:arguments:", args, domain, enforced, universe);
  }

  public static final Object sendUnknownGlobal(final Object receiver,
      final SSymbol globalName,
      final SObject domain,
      final boolean enforced,
      final Universe universe) {
    Object[] arguments = {receiver, globalName};
    return send("unknownGlobal:", arguments, domain, enforced, universe);
  }

  public static final Object sendEscapedBlock(final Object receiver,
      final SBlock block,
      final SObject domain,
      final boolean enforced,
      final Universe universe) {
    Object[] arguments = {receiver, block};
    return send("escapedBlock:", arguments, domain, enforced, universe);
  }

  public abstract SObject getDomain();
}
