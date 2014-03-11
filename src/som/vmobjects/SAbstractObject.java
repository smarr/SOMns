package som.vmobjects;

import som.interpreter.Types;
import som.vm.Universe;

import com.oracle.truffle.api.frame.PackedFrame;


public abstract class SAbstractObject {

  public abstract SClass getSOMClass(final Universe universe);

  @Override
  public String toString() {
    return "a " + getSOMClass(Universe.current()).getName().getString();
  }

  public static final Object send(
      final Object receiver,
      final String selectorString,
      final SAbstractObject[] arguments,
      final Universe universe, final PackedFrame frame) {
    // Turn the selector string into a selector
    SSymbol selector = universe.symbolFor(selectorString);

    // Lookup the invokable
    SInvokable invokable = Types.getClassOf(receiver, universe).lookupInvokable(selector);

    // Invoke the invokable
    return invokable.invoke(frame, receiver, arguments, universe);
  }

  public static final Object sendDoesNotUnderstand(final Object receiver,
      final SSymbol selector,
      final SAbstractObject[] arguments,
      final Universe universe,
      final PackedFrame frame) {
    // Allocate an array with enough room to hold all arguments
    int numArgs = (arguments == null) ? 0 : arguments.length;

    SArray argumentsArray = universe.newArray(numArgs);
    for (int i = 0; i < numArgs; i++) {
      argumentsArray.setIndexableField(i, arguments[i]);
    }

    SAbstractObject[] args = new SAbstractObject[] {selector, argumentsArray};

    return send(receiver, "doesNotUnderstand:arguments:", args, universe, frame);
  }

  public static final Object sendUnknownGlobal(final Object receiver,
      final SSymbol globalName, final Universe universe,
      final PackedFrame frame) {
    SAbstractObject[] arguments = {globalName};
    return send(receiver, "unknownGlobal:", arguments, universe, frame);
  }

  public static final Object sendEscapedBlock(final Object receiver,
      final SBlock block, final Universe universe, final PackedFrame frame) {
    SAbstractObject[] arguments = {block};
    return send(receiver, "escapedBlock:", arguments, universe, frame);
  }

}
