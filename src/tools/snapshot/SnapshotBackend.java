package tools.snapshot;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.MapCursor;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

import som.VM;
import som.compiler.MixinDefinition;
import som.interpreter.actors.Actor;
import som.interpreter.actors.Actor.ActorProcessingThread;
import som.interpreter.actors.EventualMessage;
import som.interpreter.actors.SPromise;
import som.interpreter.nodes.InstantiationNode.ClassInstantiationNode;
import som.interpreter.objectstorage.ClassFactory;
import som.primitives.ObjectPrims.ClassPrim;
import som.primitives.ObjectPrimsFactory.ClassPrimFactory;
import som.vm.Symbols;
import som.vm.VmSettings;
import som.vmobjects.SClass;
import som.vmobjects.SInvokable;
import som.vmobjects.SObjectWithClass;
import som.vmobjects.SSymbol;
import tools.concurrency.TracingActivityThread;
import tools.concurrency.TracingActors.ReplayActor;
import tools.concurrency.TracingActors.TracingActor;
import tools.concurrency.TracingBackend;
import tools.language.StructuralProbe;
import tools.snapshot.deserialization.DeserializationBuffer;
import tools.snapshot.deserialization.SnapshotParser;


public class SnapshotBackend {
  private static byte snapshotVersion = 0;

  private static final EconomicMap<Short, SSymbol>                symbolDictionary;
  private static final EconomicMap<Integer, Object>               classDictionary;
  private static final StructuralProbe                            probe;
  private static final ConcurrentLinkedQueue<SnapshotBuffer>      buffers;
  private static final ConcurrentLinkedQueue<ArrayList<Long>>     messages;
  private static final EconomicMap<SClass, TracingActor>          classEnclosures;
  private static final ConcurrentHashMap<SnapshotRecord, Integer> deferredSerializations;

  // this is a reference to the list maintained by the objectsystem
  private static EconomicMap<URI, MixinDefinition> loadedModules;
  private static SPromise                          resultPromise;
  @CompilationFinal private static VM              vm;

  static {
    if (VmSettings.TRACK_SNAPSHOT_ENTITIES) {
      classDictionary = EconomicMap.create();
      symbolDictionary = EconomicMap.create();
      probe = new StructuralProbe();
      buffers = new ConcurrentLinkedQueue<>();
      messages = new ConcurrentLinkedQueue<>();
      classEnclosures = EconomicMap.create();
      deferredSerializations = new ConcurrentHashMap<>();
      // identity int, includes mixin info
      // long outer
      // essentially this is about capturing the outer
      // let's do this when the class is stucturally initialized
    } else if (VmSettings.SNAPSHOTS_ENABLED) {
      classDictionary = null;
      symbolDictionary = null;
      probe = null;
      buffers = new ConcurrentLinkedQueue<>();
      messages = new ConcurrentLinkedQueue<>();
      classEnclosures = EconomicMap.create();
      deferredSerializations = new ConcurrentHashMap<>();
    } else {
      classDictionary = null;
      symbolDictionary = null;
      probe = null;
      buffers = null;
      messages = null;
      classEnclosures = null;
      deferredSerializations = null;
    }
  }

  public static void initialize(final VM vm) {
    SnapshotBackend.vm = vm;
  }

  public static SSymbol getSymbolForId(final short id) {
    return symbolDictionary.get(id);
  }

  public static void registerSymbol(final SSymbol sym) {
    assert VmSettings.TRACK_SNAPSHOT_ENTITIES;
    symbolDictionary.put(sym.getSymbolId(), sym);
  }

  public static synchronized void registerClass(final SClass clazz) {
    assert VmSettings.TRACK_SNAPSHOT_ENTITIES;
    classDictionary.put(clazz.getIdentity(), clazz);
  }

  public static void registerLoadedModules(final EconomicMap<URI, MixinDefinition> loaded) {
    loadedModules = loaded;
  }

  public static SClass lookupClass(final int id) {
    assert VmSettings.TRACK_SNAPSHOT_ENTITIES;
    if (classDictionary.containsKey(id)) {
      if (!(classDictionary.get(id) instanceof SClass)) {
        return null;
      }

      return (SClass) classDictionary.get(id);
    }

    // doesn't exist yet
    return createSClass(id);
  }

  @SuppressWarnings("unchecked")
  public static SClass lookupClass(final int id, final long ref) {
    if (classDictionary.containsKey(id)) {
      Object entry = classDictionary.get(id);
      if (entry instanceof SClass) {
        return (SClass) entry;
      }

      LinkedList<Long> todo = null;
      if (entry == null) {
        todo = new LinkedList<Long>();
        classDictionary.put(id, todo);
      } else if (entry instanceof LinkedList<?>) {
        todo = (LinkedList<Long>) entry;
      }
      todo.add(ref);
      return null;
    }

    // doesn't exist yet
    return createSClass(id);
  }

  private static SClass createSClass(final int id) {
    MixinDefinition mixin = acquireMixin(id);

    // Step 1: install placeholder
    classDictionary.put(id, null);
    // Output.println("creating Class" + mixin.getIdentifier() + " : " + (short) id);

    // Step 2: get outer object
    SObjectWithClass enclosingObject = SnapshotParser.getOuterForClass(id);
    assert enclosingObject != null;

    // Step 3: create Class
    Object superclassAndMixins =
        mixin.getSuperclassAndMixinResolutionInvokable().createCallTarget()
             .call(new Object[] {enclosingObject});

    ClassFactory factory = mixin.createClassFactory(superclassAndMixins, false, false, false);

    SClass result = new SClass(enclosingObject,
        ClassInstantiationNode.instantiateMetaclassClass(factory, enclosingObject));
    factory.initializeClass(result);

    // Step 4: fixup
    Object current = classDictionary.get(id);
    if (current instanceof LinkedList) {
      @SuppressWarnings("unchecked")
      LinkedList<Long> todo = (LinkedList<Long>) current;
      DeserializationBuffer db = SnapshotParser.getDeserializationBuffer();
      for (long ref : todo) {
        db.fixUpIfNecessary(ref, result);
      }
    }

    classDictionary.put(id, result);
    return result;
  }

  private static MixinDefinition acquireMixin(final int id) {
    short symId = (short) (id >> 16);

    SSymbol location = getSymbolForId(symId);
    String[] parts = location.getString().split(":");
    if (parts.length != 2) {
      assert false;
    }

    Path path = Paths.get(VmSettings.BASE_DIRECTORY, parts[0]);
    MixinDefinition mixin = loadedModules.get(path.toUri());

    if (mixin == null) {
      // need to load module
      try {
        mixin = vm.loadModule(path.toString());
        SClass module = mixin.instantiateModuleClass();
        classDictionary.put(module.getIdentity(), module);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

    String[] nestings = parts[1].split("\\.");

    for (String sub : nestings) {
      MixinDefinition temp = mixin.getNestedMixinDefinition(sub);
      if (temp != null) {
        mixin = temp;
      }
    }
    return mixin;
  }

  public static SInvokable lookupInvokable(final SSymbol sym) {
    assert VmSettings.TRACK_SNAPSHOT_ENTITIES;
    SInvokable result = probe.lookupMethod(sym);

    if (result == null) {
      // Module probably not loaded, attempt to do that.
      String[] parts = sym.getString().split(":");
      Path path = Paths.get(VmSettings.BASE_DIRECTORY, parts[0]);

      MixinDefinition mixin;
      mixin = loadedModules.get(path.toUri());
      if (mixin == null) {
        // need to load module
        try {
          mixin = vm.loadModule(path.toString());
          SClass module = mixin.instantiateModuleClass();
          classDictionary.put(module.getIdentity(), module);
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
    }

    result = probe.lookupMethod(sym);
    return result;
  }

  public static SInvokable lookupInvokable(final short sym) {
    return lookupInvokable(getSymbolForId(sym));
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
      ReplayActor ra = ReplayActor.getActorWithId(actorId);
      if (ra == null) {
        ra = new ReplayActor(vm, actorId);
      }
      return ra;
    } else {
      // For testing with snaphsotClone:
      return EventualMessage.getActorCurrentMessageIsExecutionOn();
    }
  }

  public static void registerSnapshotBuffer(final SnapshotBuffer sb,
      final ArrayList<Long> messageLocations) {
    if (VmSettings.TEST_SERIALIZE_ALL) {
      return;
    }

    assert sb != null;
    buffers.add(sb);
    messages.add(messageLocations);
  }

  /**
   * Serialization of objects referenced from far references need to be deferred to the owning
   * actor.
   */
  @TruffleBoundary
  public static void deferSerialization(final SnapshotRecord sr) {
    deferredSerializations.put(sr, 0);
  }

  @TruffleBoundary
  public static void completedSerialization(final SnapshotRecord sr) {
    deferredSerializations.remove(sr);
  }

  public static StructuralProbe getProbe() {
    assert probe != null;
    return probe;
  }

  public static TracingActor getCurrentActor() {
    if (VmSettings.REPLAY) {
      return SnapshotParser.getCurrentActor();
    } else {
      return (TracingActor) EventualMessage.getActorCurrentMessageIsExecutionOn();
    }
  }

  /**
   * Persist the current snapshot to a file.
   */
  private static final ClassPrim classPrim = ClassPrimFactory.create(null);

  public static void writeSnapshot() {
    if (buffers.size() == 0) {
      return;
    }

    String name = VmSettings.TRACE_FILE + '.' + snapshotVersion;
    File f = new File(name + ".snap");
    try (FileOutputStream fos = new FileOutputStream(f)) {
      // Write Message Locations
      int offset = writeMessageLocations(fos);
      offset += writeClassEnclosures(fos);

      // handle the unfinished serialization.
      SnapshotBuffer buffer = buffers.peek();

      while (!deferredSerializations.isEmpty()) {
        for (SnapshotRecord sr : deferredSerializations.keySet()) {
          assert sr.owner != null;
          deferredSerializations.remove(sr);
          buffer.owner.setCurrentActorSnapshot(sr.owner);
          sr.handleObjectsReferencedFromFarRefs(buffer, classPrim);
        }
      }

      writeSymbolTable();

      // WriteHeapMap
      writeHeapMap(fos, offset);

      // Write Heap
      while (!buffers.isEmpty()) {
        SnapshotBuffer sb = buffers.poll();
        fos.getChannel().write(ByteBuffer.wrap(sb.getRawBuffer(), 0, sb.position()));
        fos.flush();
      }

    } catch (IOException e1) {
      throw new RuntimeException(e1);
    }
  }

  private static int writeClassEnclosures(final FileOutputStream fos) throws IOException {
    int size = (classEnclosures.size() * 2 + 1) * Long.BYTES;
    ByteBuffer bb = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);

    bb.putLong(classEnclosures.size());
    MapCursor<SClass, TracingActor> cursor = classEnclosures.getEntries();

    // just use the first buffer available; that object isn't used anywhere else
    SnapshotBuffer buffer = buffers.peek();

    while (cursor.advance()) {
      SClass clazz = cursor.getKey();
      TracingActor owner = cursor.getValue();

      bb.putLong(clazz.getIdentity());
      SObjectWithClass outer = clazz.getEnclosingObject();

      if (!owner.getSnapshotRecord().containsObjectUnsync(outer)) {
        buffer.owner.setCurrentActorSnapshot(owner);
        outer.getSOMClass().serialize(outer, buffer);
      }
      bb.putLong(owner.getSnapshotRecord().getObjectPointer(outer));
    }

    bb.rewind();
    fos.getChannel().write(bb);
    fos.flush();
    return size;
  }

  /**
   * This method creates a list that allows us to know where a {@link SnapshotBuffer} starts in
   * the file.
   */
  private static void writeHeapMap(final FileOutputStream fos, final int msgSize)
      throws IOException {
    // need to have a registry of the different heap areas
    int numBuffers = buffers.size();
    int registrySize = ((numBuffers * 2 + 2) * Long.BYTES);
    ByteBuffer bb = ByteBuffer.allocate(registrySize).order(ByteOrder.LITTLE_ENDIAN);
    // get and write location of the promise
    TracingActor ta = (TracingActor) resultPromise.getOwner();
    SPromise.getPromiseClass().serialize(resultPromise, buffers.peek());
    long location = ta.getSnapshotRecord().getObjectPointer(resultPromise);
    bb.putLong(location);

    bb.putLong(numBuffers);

    int bufferStart = msgSize + registrySize;
    for (SnapshotBuffer sb : buffers) {
      long id = sb.owner.getThreadId();
      bb.putLong(id);
      bb.putLong(bufferStart);
      bufferStart += sb.position();
    }

    bb.rewind();
    fos.getChannel().write(bb);
  }

  /**
   * This method persists the locations of messages in mailboxes, i.e. the roots of our object
   * graph
   */
  private static int writeMessageLocations(final FileOutputStream fos)
      throws IOException {
    int entryCount = 0;
    for (ArrayList<Long> al : messages) {
      assert al.size() % 2 == 0;
      entryCount += al.size();
    }

    int msgSize = ((entryCount + 1) * Long.BYTES);
    ByteBuffer bb =
        ByteBuffer.allocate(msgSize).order(ByteOrder.LITTLE_ENDIAN);
    bb.putLong(entryCount);
    for (ArrayList<Long> al : messages) {
      for (long l : al) {
        bb.putLong(l);
      }
    }

    bb.rewind();
    fos.getChannel().write(bb);
    return msgSize;
  }

  private static void writeSymbolTable() {
    Collection<SSymbol> symbols = Symbols.getSymbols();

    if (symbols.isEmpty()) {
      return;
    }
    File f = new File(VmSettings.TRACE_FILE + ".sym");

    try (FileOutputStream symbolStream = new FileOutputStream(f);
        BufferedWriter symbolWriter =
            new BufferedWriter(new OutputStreamWriter(symbolStream))) {

      for (SSymbol s : symbols) {
        symbolWriter.write(s.getSymbolId() + ":" + s.getString());
        symbolWriter.newLine();
      }
      symbolWriter.flush();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static void registerResultPromise(final SPromise promise) {
    assert resultPromise == null;
    resultPromise = promise;
  }

  public static void registerClassEnclosure(final SClass clazz) {
    ActorProcessingThread current =
        (ActorProcessingThread) TracingActivityThread.currentThread();
    synchronized (classEnclosures) {
      classEnclosures.put(clazz, (TracingActor) current.getCurrentActor());
    }
  }
}
