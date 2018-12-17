package tools.snapshot.deserialization;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.ArrayList;

import org.graalvm.collections.EconomicMap;

import som.VM;
import som.interpreter.actors.EventualMessage;
import som.interpreter.actors.SPromise;
import som.vm.Symbols;
import som.vm.VmSettings;
import som.vm.constants.Nil;
import som.vmobjects.SObjectWithClass;
import tools.concurrency.TracingActors.ReplayActor;
import tools.snapshot.SnapshotBackend;
import tools.snapshot.SnapshotBuffer;
import tools.snapshot.deserialization.DeserializationBuffer.FileDeserializationBuffer;


public final class SnapshotParser {

  private static SnapshotParser parser;

  private final EconomicMap<Long, Long>               heapOffsets;
  private final EconomicMap<Integer, ArrayList<Long>> messageLocations;
  private SPromise                                    resultPromise;
  private ReplayActor                                 currentActor;
  private VM                                          vm;
  private EconomicMap<Integer, Long>                  outerMap;
  private DeserializationBuffer                       db;

  private SnapshotParser(final VM vm) {
    this.vm = vm;
    this.heapOffsets = EconomicMap.create();
    this.messageLocations = EconomicMap.create();
    this.outerMap = EconomicMap.create();
  }

  // preparations to be done before anything else
  public static void preparations() {
    parseSymbols();
  }

  public static void inflate(final VM vm) {
    if (parser == null) {
      parser = new SnapshotParser(vm);
    }
    parser.parseMetaData();
  }

  /**
   * Read the Method Pointers, their actorIds, and most importantly the start addresses for the
   * thread areas.
   */
  private void parseMetaData() {
    ByteBuffer b = ByteBuffer.allocate(VmSettings.BUFFER_SIZE).order(ByteOrder.LITTLE_ENDIAN);
    String fileName = VmSettings.TRACE_FILE + ".1.snap";
    File traceFile = new File(fileName);
    try (FileInputStream fis = new FileInputStream(traceFile);
        FileChannel channel = fis.getChannel()) {
      channel.read(b);
      b.flip(); // prepare for reading from buffer

      long numMessages = b.getLong() / 2;
      for (int i = 0; i < numMessages; i++) {
        ensureRemaining(Long.BYTES * 2, b, channel);
        int actorId = (int) b.getLong();
        long location = b.getLong();

        if (!messageLocations.containsKey(actorId)) {
          messageLocations.put(actorId, new ArrayList<>());
        }
        messageLocations.get(actorId).add(location);
      }

      long numOuters = b.getLong();
      for (int i = 0; i < numOuters; i++) {
        ensureRemaining(Long.BYTES * 2, b, channel);
        int identity = (int) b.getLong();
        long outer = b.getLong();
        outerMap.put(identity, outer);
      }

      // ensureRemaining(Short.BYTES, b, channel);
      // short numModules = b.getShort();
      // for (int i = 0; i < numModules; i++) {
      // ensureRemaining(Short.BYTES, b, channel);
      // short symId = b.getShort();
      // SSymbol sym = SnapshotBackend.getSymbolForId(symId);
      // URI uri = new URI(sym.getString());
      // String[] components = sym.getString().split(":");
      // assert components.length == 2;
      // String path = components[0];
      //
      // // I think extension modules are not added to the list we use...
      // // for now this is not an issue but later we may need to solve this for acme
      // // My understanding is that there is exactly one SClass object, which is returned when
      // // loading the module.
      // // By loading the module multiple times you would get a new SClass every time.
      // if (path.endsWith(SystemPrims.EXTENSION_EXT)) {
      // vm.loadExtensionModule(path);
      // } else {
      // MixinDefinition module;
      // module = vm.loadModule(path);
      // // TODO looks like the module paths we try to load are problematic.
      // // probably a hashtag too many.
      // }
      // }

      ensureRemaining(Long.BYTES * 2, b, channel);
      long resultPromiseLocation = b.getLong();
      long numHeaps = b.getLong();
      for (int i = 0; i < numHeaps; i++) {
        ensureRemaining(Long.BYTES * 2, b, channel);
        long threadId = b.getLong();
        long offset = b.getLong();
        heapOffsets.put(threadId, offset);
      }

      // At this point we now have read all of the metadata and can begin the process of
      // inflating the snapshot.

      // EconomicMap<Integer, ReplayActor> actors = EconomicMap.create();

      // lets create the Actor objects first, and add them to our list
      for (int id : messageLocations.getKeys()) {
        SnapshotBackend.lookupActor(id);
        // actors.put(id, ra);
      }

      db = new FileDeserializationBuffer(channel);

      // now let's go through the message list actor by actor, deserialize each message, and
      // add it to the actors mailbox.
      for (int id : messageLocations.getKeys()) {
        ArrayList<Long> locations = messageLocations.get(id);
        for (long location : locations) {
          // Deserialilze message
          currentActor = ReplayActor.getActorWithId(id);
          // System.out.println(
          // "Message at: " + (((int) location) + getFileOffset(location)));
          EventualMessage em = (EventualMessage) db.deserializeWithoutContext(location);
          currentActor.sendSnapshotMessage(em);
        }
      }

      resultPromise = (SPromise) db.getReference(resultPromiseLocation);
      if (resultPromise == null) {
        resultPromise = (SPromise) db.deserialize(resultPromiseLocation);
      }

      assert resultPromise != null : "The result promise was not found";
    } catch (FileNotFoundException e) {
      e.printStackTrace();
      throw new RuntimeException(e);
    } catch (IOException e) {
      throw new RuntimeException(e);
    } finally {
      // prevent usage after closing
      db = null;
    }
  }

  private static void parseSymbols() {
    File symbolFile = new File(VmSettings.TRACE_FILE + ".sym");
    // create mapping from old to new symbol ids
    try (FileInputStream fis = new FileInputStream(symbolFile);
        BufferedReader br = new BufferedReader(new InputStreamReader(fis))) {
      String line = br.readLine();
      while (line != null) {
        String[] a = line.split(":", 2);
        Symbols.addSymbolFor(a[1], Short.parseShort(a[0]));
        line = br.readLine();
      }
    } catch (FileNotFoundException e) {
      throw new RuntimeException(e);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static long getFileOffset(final long address) {
    long threadId = address >> SnapshotBuffer.THREAD_SHIFT;
    assert parser.heapOffsets.containsKey(threadId);
    return parser.heapOffsets.get(threadId);
  }

  public static SObjectWithClass getOuterForClass(final int identity) {
    SObjectWithClass result;

    if (parser.outerMap.containsKey(identity)) {
      long reference = parser.outerMap.get(identity);
      Object o = parser.db.getReference(reference);
      long pos = ((int) reference) + SnapshotParser.getFileOffset(reference);
      // System.out.print("Outer - " + pos + " in " + (reference >> 48) + " ");
      if (!parser.db.allreadyDeserialized(reference)) {
        result = (SObjectWithClass) parser.db.deserialize(reference);
      } else if (parser.db.needsFixup(o)) {
        result = null;
      } else {
        result = (SObjectWithClass) o;
      }
    } else {
      result = Nil.nilObject;
    }
    return result;
  }

  private void ensureRemaining(final int bytes, final ByteBuffer b, final FileChannel channel)
      throws IOException {
    if (b.remaining() < bytes) {
      // need to refill buffer
      b.compact();
      channel.read(b);
      b.flip();
      assert b.remaining() >= bytes;
    }
  }

  public static ReplayActor getCurrentActor() {
    assert parser.currentActor != null;
    return parser.currentActor;
  }

  public static void setCurrentActor(final ReplayActor current) {
    parser.currentActor = current;
  }

  public static SPromise getResultPromise() {
    assert parser.resultPromise != null;
    return parser.resultPromise;
  }

  public static DeserializationBuffer getDeserializationBuffer() {
    return parser.db;
  }
}
