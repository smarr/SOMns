package tools.snapshot;

import org.graalvm.collections.EconomicMap;

import som.vm.VmSettings;
import som.vmobjects.SClass;
import som.vmobjects.SInvokable;
import som.vmobjects.SSymbol;


public class SnapshotBackend {
  private static byte snapshotVersion = 0;

  private static EconomicMap<Short, SSymbol>      symbolDictionary;
  private static EconomicMap<SSymbol, SClass>     classDictionary;
  private static EconomicMap<SSymbol, SInvokable> invokableDictionary;

  static {
    if (VmSettings.TRACK_SNAPSHOT_ENTITIES) {
      classDictionary = EconomicMap.create();
      invokableDictionary = EconomicMap.create();
      symbolDictionary = EconomicMap.create();
    }
  }

  public static SSymbol getSymbolForId(final short id) {
    return symbolDictionary.get(id);
  }

  public static void registerSymbol(final SSymbol sym) {
    assert VmSettings.TRACK_SNAPSHOT_ENTITIES;
    symbolDictionary.put(sym.getSymbolId(), sym);
  }

  public static void registerClass(final SSymbol sym, final SClass clazz) {
    assert VmSettings.TRACK_SNAPSHOT_ENTITIES;
    classDictionary.put(sym, clazz);
  }

  public static void registerInvokable(final SSymbol sym, final SInvokable invokable) {
    assert VmSettings.TRACK_SNAPSHOT_ENTITIES;
    invokableDictionary.put(sym, invokable);
  }

  public static SClass lookupClass(final SSymbol sym) {
    assert VmSettings.TRACK_SNAPSHOT_ENTITIES;
    return classDictionary.get(sym);
  }

  public static SClass lookupClass(final short sym) {
    assert VmSettings.TRACK_SNAPSHOT_ENTITIES;
    return classDictionary.get(getSymbolForId(sym));
  }

  public static SInvokable lookupInvokable(final SSymbol sym) {
    assert VmSettings.TRACK_SNAPSHOT_ENTITIES;
    return invokableDictionary.get(sym);
  }

  public static SInvokable lookupInvokable(final short sym) {
    assert VmSettings.TRACK_SNAPSHOT_ENTITIES;
    return invokableDictionary.get(getSymbolForId(sym));
  }

  public static synchronized void startSnapshot() {
    assert VmSettings.SNAPSHOTS_ENABLED;
    snapshotVersion++;
  }

  public static byte getSnapshotVersion() {
    assert VmSettings.SNAPSHOTS_ENABLED;
    // intentionally unsynchronized, as a result the line between snapshots will be a bit
    // fuzzy.
    return snapshotVersion;
  }
}
