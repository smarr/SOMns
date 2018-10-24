package tools.snapshot;

import org.graalvm.collections.EconomicMap;

import som.interpreter.actors.Actor;
import som.interpreter.actors.EventualMessage;
import som.vm.VmSettings;
import som.vmobjects.SClass;
import som.vmobjects.SInvokable;
import som.vmobjects.SSymbol;
import tools.concurrency.TracingActors.ReplayActor;
import tools.language.StructuralProbe;


public class SnapshotBackend {
  private static byte snapshotVersion = 0;

  private static final EconomicMap<Short, SSymbol>  symbolDictionary;
  private static final EconomicMap<SSymbol, SClass> classDictionary;
  private static final StructuralProbe              probe;

  static {
    if (VmSettings.TRACK_SNAPSHOT_ENTITIES) {
      classDictionary = EconomicMap.create();
      symbolDictionary = EconomicMap.create();
      probe = new StructuralProbe();
    } else {
      classDictionary = null;
      symbolDictionary = null;
      probe = null;
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
    return probe.lookupMethod(sym);
  }

  public static SInvokable lookupInvokable(final short sym) {
    assert VmSettings.TRACK_SNAPSHOT_ENTITIES;
    return probe.lookupMethod(getSymbolForId(sym));
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

  public static Actor lookupActor(final int actorId) {
    if (VmSettings.REPLAY) {
      return ReplayActor.getActorWithId(actorId);
    } else {
      // For testing with snaphsotClone:
      return EventualMessage.getActorCurrentMessageIsExecutionOn();
    }
  }

  public static StructuralProbe getProbe() {
    assert probe != null;
    return probe;
  }
}
