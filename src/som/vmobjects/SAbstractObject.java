package som.vmobjects;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.TruffleObject;

import som.interop.SAbstractObjectInteropMessagesForeign;
import som.interop.SomInteropObject;
import tools.snapshot.SnapshotBuffer;


public abstract class SAbstractObject implements SomInteropObject {

  public abstract SClass getSOMClass();

  public abstract boolean isValue();

  private long snapshotLocation = -1;
  private byte snapshotVersion;

  @Override
  public ForeignAccess getForeignAccess() {
    return SAbstractObjectInteropMessagesForeign.ACCESS;
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

  /**
   * Used by Truffle interop.
   */
  public static boolean isInstance(final TruffleObject obj) {
    return obj instanceof SAbstractObject;
  }

  public long getSnapshotLocation() {
    return snapshotLocation;
  }

  public long getSnapshotLocationAndUpdate(final SnapshotBuffer sb) {
    if (snapshotLocation == -1 || snapshotVersion != sb.getSnapshotVersion()) {
      snapshotVersion = sb.getSnapshotVersion();
      snapshotLocation = getSOMClass().serialize(this, sb);
    }
    return snapshotLocation;
  }

  public byte getSnapshotVersion() {
    return snapshotVersion;
  }

  public void updateSnapshotLocation(final long snapshotLocation, final byte version) {
    this.snapshotLocation = snapshotLocation;
    this.snapshotVersion = version;
  }
}
