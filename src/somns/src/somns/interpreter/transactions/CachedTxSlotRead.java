package somns.interpreter.transactions;

import somns.interpreter.nodes.dispatch.AbstractDispatchNode;
import somns.interpreter.nodes.dispatch.CachedSlotRead;
import somns.interpreter.nodes.dispatch.DispatchGuard.CheckSObject;
import somns.vmobjects.SObject;
import somns.vmobjects.SObject.SMutableObject;


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
