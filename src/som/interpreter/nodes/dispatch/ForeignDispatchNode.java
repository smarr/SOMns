package som.interpreter.nodes.dispatch;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.LibraryFactory;

import som.VM;
import som.interop.SomInteropObject;
import som.interpreter.SArguments;


public final class ForeignDispatchNode extends AbstractDispatchNode {
  private final String selector;

  @Child private AbstractDispatchNode nextInCache;

  private final LibraryFactory<InteropLibrary> factory;
  private final int                            numArgs;

  public ForeignDispatchNode(final int numArgs, final String selector,
      final AbstractDispatchNode next) {
    super(next.getSourceSection());
    this.selector = selector.replaceAll(":", "");
    this.nextInCache = next;

    this.numArgs = numArgs;
    this.factory = InteropLibrary.getFactory();
  }

  @Override
  // TODO: needs to be optimized for compilation
  public Object executeDispatch(final VirtualFrame frame, final Object[] arguments) {

    VM.thisMethodNeedsToBeOptimized("");
    CompilerDirectives.transferToInterpreter();
    Object rcvr = arguments[0];
    if (rcvr instanceof TruffleObject && !(rcvr instanceof SomInteropObject)) {
      TruffleObject r = (TruffleObject) rcvr;
      InteropLibrary interop = factory.getUncached(rcvr);
      try {
        return interop.invokeMember(r, selector,
            SArguments.getPlainArgumentWithoutReceiver(arguments));
      } catch (UnsupportedTypeException | ArityException
          | UnknownIdentifierException | UnsupportedMessageException e) {
        if (numArgs == 1) {
          try {
            return interop.readMember(r, selector);
          } catch (UnknownIdentifierException
              | UnsupportedMessageException e1) {
            throw new RuntimeException(e1);
          }
        } else {
          assert numArgs == 2;
          try {
            interop.writeMember(r, selector, arguments[1]);
            return arguments[1];
          } catch (UnknownIdentifierException | UnsupportedTypeException
              | UnsupportedMessageException e1) {
            throw new RuntimeException(e1);
          }
        }
      }
    } else {
      return nextInCache.executeDispatch(frame, arguments);
    }
  }

  @Override
  public int lengthOfDispatchChain() {
    return 1 + nextInCache.lengthOfDispatchChain();
  }
}
