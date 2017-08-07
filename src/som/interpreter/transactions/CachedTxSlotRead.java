package som.interpreter.transactions;

import som.interpreter.nodes.dispatch.AbstractDispatchNode;
import som.interpreter.nodes.dispatch.CachedSlotRead;
import som.interpreter.nodes.dispatch.DispatchGuard.CheckSObject;
import som.vmobjects.SObject;
import som.vmobjects.SObject.SMutableObject;


public final class CachedTxSlotRead extends CachedSlotRead {
  @Child protected CachedSlotRead read;

  public CachedTxSlotRead(final SlotAccess type,
      final CachedSlotRead read,
      final CheckSObject guard, final AbstractDispatchNode nextInCache) {
    super(type, guard, nextInCache);
    assert type == SlotAccess.FIELD_READ;
    this.read = read;
  }

  @Override
  public Object read(final SObject rcvr) {
    SMutableObject workingCopy = Transactions.workingCopy((SMutableObject) rcvr);
    return read.read(workingCopy);
  }
}
