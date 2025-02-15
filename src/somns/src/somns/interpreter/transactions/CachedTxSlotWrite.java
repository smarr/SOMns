package somns.interpreter.transactions;

import somns.interpreter.nodes.dispatch.AbstractDispatchNode;
import somns.interpreter.nodes.dispatch.CachedSlotWrite;
import somns.interpreter.nodes.dispatch.DispatchGuard.CheckSObject;
import somns.vmobjects.SObject;
import somns.vmobjects.SObject.SMutableObject;


public final class CachedTxSlotWrite extends CachedSlotWrite {
  @Child protected CachedSlotWrite write;

  public CachedTxSlotWrite(final CachedSlotWrite write,
      final CheckSObject guard, final AbstractDispatchNode nextInCache) {
    super(guard, nextInCache);
    this.write = write;
  }

  @Override
  public void doWrite(final SObject obj, final Object value) {
    SMutableObject workingCopy = Transactions.workingCopy((SMutableObject) obj);
    write.doWrite(workingCopy, value);
  }
}
