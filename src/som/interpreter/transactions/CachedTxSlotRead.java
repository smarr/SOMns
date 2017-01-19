package som.interpreter.transactions;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.InvalidAssumptionException;

import som.interpreter.nodes.dispatch.AbstractDispatchNode;
import som.interpreter.nodes.dispatch.CachedSlotAccessNode.CachedSlotRead;
import som.interpreter.nodes.dispatch.DispatchGuard;
import som.interpreter.objectstorage.FieldReadNode;
import som.vmobjects.SObject.SMutableObject;


public final class CachedTxSlotRead extends CachedSlotRead {
  public CachedTxSlotRead(final SlotAccess type,
      final FieldReadNode read,
      final DispatchGuard guard, final AbstractDispatchNode nextInCache) {
    super(type, read, guard, nextInCache);
    assert type == SlotAccess.FIELD_READ;
  }

  @Override
  protected Object read(final VirtualFrame frame, final Object rcvr) throws InvalidAssumptionException {
    SMutableObject workingCopy = Transactions.workingCopy((SMutableObject) rcvr);
    return read.read(frame, workingCopy);
  }
}
