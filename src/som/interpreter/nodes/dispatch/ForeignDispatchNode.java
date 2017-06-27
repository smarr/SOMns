package som.interpreter.nodes.dispatch;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.nodes.Node;

import som.VM;
import som.interpreter.SArguments;
import som.vmobjects.SAbstractObject;


public final class ForeignDispatchNode extends AbstractDispatchNode {
  private final String selector;

  @Child private AbstractDispatchNode nextInCache;
  @Child private Node invoke;
  @Child private Node read;
  @Child private Node write;

  public ForeignDispatchNode(final int numArgs, final String selector,
      final AbstractDispatchNode next) {
    super(next.getSourceSection());
    this.selector = selector.replaceAll(":", "");
    this.nextInCache = next;
    invoke = Message.createInvoke(numArgs).createNode();

    if (numArgs == 1) {
      read = Message.READ.createNode();
    } else if (numArgs == 2) {
      write = Message.WRITE.createNode();
    }
  }

  @Override
  @TruffleBoundary
  // TODO: needs to be optimized for compilation
  public Object executeDispatch(final Object[] arguments) {
    VM.thisMethodNeedsToBeOptimized("");
    Object rcvr = arguments[0];
    if (rcvr instanceof TruffleObject && !(rcvr instanceof SAbstractObject)) {
      TruffleObject r = (TruffleObject) rcvr;
      try {
        return ForeignAccess.sendInvoke(invoke, r,
            selector, SArguments.getPlainArgumentWithoutReceiver(arguments));
      } catch (UnsupportedTypeException | ArityException
          | UnknownIdentifierException | UnsupportedMessageException e) {
        if (read != null) {
          try {
            return ForeignAccess.sendRead(read, r, selector);
          } catch (UnknownIdentifierException
              | UnsupportedMessageException e1) {
            throw new RuntimeException(e1);
          }
        } else {
          assert write != null;
          try {
            return ForeignAccess.sendWrite(write, r, selector, arguments[1]);
          } catch (UnknownIdentifierException | UnsupportedTypeException
              | UnsupportedMessageException e1) {
            throw new RuntimeException(e1);
          }
        }
      }
    } else {
      return nextInCache.executeDispatch(arguments);
    }
  }

  @Override
  public int lengthOfDispatchChain() {
    return 1 + nextInCache.lengthOfDispatchChain();
  }
}
