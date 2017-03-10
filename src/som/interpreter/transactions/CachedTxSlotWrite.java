package som.interpreter.transactions;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.nodes.InvalidAssumptionException;

import som.interpreter.nodes.dispatch.AbstractDispatchNode;
import som.interpreter.nodes.dispatch.CachedSlotAccessNode.CachedSlotWrite;
import som.interpreter.nodes.dispatch.DispatchGuard;
import som.interpreter.objectstorage.FieldWriteNode.AbstractFieldWriteNode;
import som.vmobjects.SObject.SMutableObject;


public final class CachedTxSlotWrite extends CachedSlotWrite {
  public CachedTxSlotWrite(final AbstractFieldWriteNode write,
      final DispatchGuard guard,
      final AbstractDispatchNode nextInCache) {
    super(write, guard, nextInCache);

  }

  @Override
  public Object executeDispatch(final Object[] arguments) {
    Object rcvr = arguments[0];
    try {
      if (guard.entryMatches(rcvr)) {
        SMutableObject workingCopy = Transactions.workingCopy((SMutableObject) rcvr);
        return write.write(workingCopy, arguments[1]);
      } else {
        return nextInCache.executeDispatch(arguments);
      }
    } catch (InvalidAssumptionException e) {
      CompilerDirectives.transferToInterpreterAndInvalidate();
      return replace(nextInCache).executeDispatch(arguments);
    }
  }
}
