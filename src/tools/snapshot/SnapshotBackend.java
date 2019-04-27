package tools.snapshot;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.graalvm.collections.EconomicMap;

import bd.tools.structure.StructuralProbe;
import som.compiler.MixinDefinition;
import som.compiler.MixinDefinition.SlotDefinition;
import som.compiler.Variable;
import som.interpreter.actors.Actor;
import som.interpreter.actors.EventualMessage;
import som.vm.VmSettings;
import som.vmobjects.SClass;
import som.vmobjects.SInvokable;
import som.vmobjects.SSymbol;
import tools.concurrency.TracingActors.ReplayActor;
import tools.concurrency.TracingBackend;


public class SnapshotBackend {
  private static byte snapshotVersion = 0;

  private static final StructuralProbe<SSymbol, MixinDefinition, SInvokable, SlotDefinition, Variable> probe;

  private static final EconomicMap<Short, SSymbol>  symbolDictionary;
  private static final EconomicMap<SSymbol, SClass> classDictionary;

  private static final ConcurrentLinkedQueue<SnapshotBuffer> buffers;

  static {
    if (VmSettings.TRACK_SNAPSHOT_ENTITIES) {
      classDictionary = EconomicMap.create();
      symbolDictionary = EconomicMap.create();
      probe = new StructuralProbe<>();
      buffers = new ConcurrentLinkedQueue<>();
    } else if (VmSettings.SNAPSHOTS_ENABLED) {
      classDictionary = null;
      symbolDictionary = null;
      probe = null;
      buffers = new ConcurrentLinkedQueue<>();
    } else {
      classDictionary = null;
      symbolDictionary = null;
      probe = null;
      buffers = null;
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

    // notify the worker in the tracingbackend about this change.
    TracingBackend.newSnapshot(snapshotVersion);
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

  public static void registerSnapshotBuffer(final SnapshotBuffer sb) {
    if (VmSettings.TEST_SERIALIZE_ALL) {
      return;
    }

    assert sb != null;
    buffers.add(sb);
  }

  public static StructuralProbe<SSymbol, MixinDefinition, SInvokable, SlotDefinition, Variable> getProbe() {
    assert probe != null;
    return probe;
  }

  public static void writeSnapshot() {
    if (buffers.size() == 0) {
      return;
    }

    String name = VmSettings.TRACE_FILE + snapshotVersion;
    File f = new File(name + ".snap");
    try (FileOutputStream fos = new FileOutputStream(f)) {
      while (!buffers.isEmpty()) {
        SnapshotBuffer sb = buffers.poll();
        fos.getChannel().write(ByteBuffer.wrap(sb.getRawBuffer(), 0, sb.position()));
        fos.flush();
      }
    } catch (IOException e1) {
      throw new RuntimeException(e1);
    }
  }
}
