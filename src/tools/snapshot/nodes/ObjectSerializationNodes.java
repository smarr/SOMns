package tools.snapshot.nodes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.ExplodeLoop;

import som.compiler.MixinDefinition.SlotDefinition;
import som.interpreter.nodes.dispatch.AbstractDispatchNode;
import som.interpreter.nodes.dispatch.CachedSlotRead;
import som.interpreter.nodes.dispatch.CachedSlotRead.SlotAccess;
import som.interpreter.nodes.dispatch.CachedSlotWrite;
import som.interpreter.nodes.dispatch.DispatchGuard;
import som.interpreter.nodes.dispatch.UninitializedDispatchNode;
import som.interpreter.objectstorage.ClassFactory;
import som.interpreter.objectstorage.ObjectLayout;
import som.interpreter.objectstorage.ObjectTransitionSafepoint;
import som.interpreter.objectstorage.StorageLocation;
import som.primitives.ObjectPrims.ClassPrim;
import som.primitives.ObjectPrimsFactory.ClassPrimFactory;
import som.vmobjects.SClass;
import som.vmobjects.SObject;
import som.vmobjects.SObject.SImmutableObject;
import som.vmobjects.SObject.SMutableObject;
import som.vmobjects.SObjectWithClass.SObjectWithoutFields;
import tools.snapshot.SnapshotBuffer;
import tools.snapshot.deserialization.DeserializationBuffer;
import tools.snapshot.deserialization.FixupInformation;
import tools.snapshot.nodes.ObjectSerializationNodesFactory.SObjectSerializationNodeFactory;
import tools.snapshot.nodes.ObjectSerializationNodesFactory.SObjectWithoutFieldsSerializationNodeFactory;


public abstract class ObjectSerializationNodes {
  public static final int FIELD_SIZE    = 8;
  public static final int MAX_FIELD_CNT = Byte.MAX_VALUE;

  public abstract static class ObjectSerializationNode extends AbstractSerializationNode {

    protected static class SlotDefinitionSorter implements Comparator<SlotDefinition> {
      @Override
      public int compare(final SlotDefinition o1, final SlotDefinition o2) {
        return o1.getName().getString().compareTo(o2.getName().getString());
      }
    }

    protected final ClassFactory classFact;

    protected final int depth;

    protected ObjectSerializationNode(final ClassFactory instanceFactory) {
      this.classFact = instanceFactory;
      this.depth = 0;
    }

    protected ObjectSerializationNode(final ClassFactory instanceFactory, final int depth) {
      this.classFact = instanceFactory;
      this.depth = depth;
    }

    public static AbstractSerializationNode create(final ClassFactory instanceFactory,
        final int depth) {
      if (instanceFactory.hasSlots()) {
        return SObjectSerializationNodeFactory.create(instanceFactory,
            createReadNodes(instanceFactory), depth);
      } else {
        return SObjectWithoutFieldsSerializationNodeFactory.create();
      }
    }

    public static NodeFactory<? extends AbstractSerializationNode> getNodeFactory(
        final ClassFactory instanceFactory) {
      if (instanceFactory.hasSlots()) {
        return SObjectSerializationNodeFactory.getInstance();
      } else {
        return SObjectWithoutFieldsSerializationNodeFactory.getInstance();
      }
    }

    protected final CachedSlotWrite[] createWriteNodes(final SObject o) {
      CompilerDirectives.transferToInterpreter();

      // sort slots so we get the right order
      ObjectLayout layout = classFact.getInstanceLayout();
      ArrayList<SlotDefinition> definitions = new ArrayList<>();
      for (SlotDefinition sd : layout.getStorageLocations().getKeys()) {
        definitions.add(sd);
      }
      CachedSlotWrite[] writes = new CachedSlotWrite[definitions.size()];

      Collections.sort(definitions, new SlotDefinitionSorter());

      for (int i = 0; i < writes.length; i++) {
        StorageLocation loc =
            layout.getStorageLocation(definitions.get(i));

        AbstractDispatchNode next =
            UninitializedDispatchNode.createLexicallyBound(loc.getSlot().getSourceSection(),
                loc.getSlot().getName(), classFact.getMixinDefinition().getMixinId());

        writes[i] =
            loc.getWriteNode(loc.getSlot(), DispatchGuard.createSObjectCheck(o),
                next,
                false);
      }
      return writes;
    }

    protected static final CachedSlotRead[] createReadNodes(final ClassFactory factory) {
      CompilerDirectives.transferToInterpreter();

      ArrayList<SlotDefinition> definitions = new ArrayList<>();
      ObjectLayout layout = factory.getInstanceLayout();
      for (SlotDefinition sd : layout.getStorageLocations().getKeys()) {
        definitions.add(sd);
      }
      CachedSlotRead[] reads = new CachedSlotRead[definitions.size()];

      // Reads are ordered by the name of the slots
      // this way the entry order is deterministic and independent of layout changes
      // same order in replay when reading
      Collections.sort(definitions, new SlotDefinitionSorter());

      for (int i = 0; i < reads.length; i++) {
        StorageLocation loc = layout.getStorageLocation(definitions.get(i));

        AbstractDispatchNode next =
            UninitializedDispatchNode.createLexicallyBound(loc.getSlot().getSourceSection(),
                loc.getSlot().getName(), factory.getMixinDefinition().getMixinId());

        reads[i] = loc.getReadNode(
            SlotAccess.FIELD_READ, DispatchGuard.createSObjectCheck(factory), next, false);
      }

      return reads;
    }
  }

  // These Nodes may need to be replaced in the class when object layouts change

  @GenerateNodeFactory
  public abstract static class SObjectSerializationNode
      extends ObjectSerializationNode {

    protected final int                         fieldCnt;
    @Children private CachedSlotRead[]          fieldReads;
    @Children private CachedSlotWrite[]         fieldWrites;
    @Children private CachedSerializationNode[] cachedSerializers;
    @Child ClassPrim                            classPrim = ClassPrimFactory.create(null);
    protected final ObjectLayout                layout;

    protected SObjectSerializationNode(final ClassFactory instanceFactory,
        final CachedSlotRead[] reads, final int depth) {
      super(instanceFactory);
      layout = classFact.getInstanceLayout();
      fieldReads = insert(reads);
      fieldCnt = fieldReads.length;

      cachedSerializers = new CachedSerializationNode[fieldCnt];
      for (int i = 0; i < fieldCnt; i++) {
        cachedSerializers[i] = CachedSerializationNodeFactory.create(depth);
      }
    }

    protected SObjectSerializationNode(final ClassFactory instanceFactory, final int depth) {
      this(instanceFactory, createReadNodes(instanceFactory), depth);
    }

    @Specialization
    public long serialize(final SObject so, final SnapshotBuffer sb) {
      long location = getObjectLocation(so, sb.getSnapshotVersion());
      if (location != -1) {
        return location;
      }

      if (!so.isLayoutCurrent()) {
        CompilerDirectives.transferToInterpreter();
        ObjectTransitionSafepoint.INSTANCE.transitionObject(so);
      }

      if (!layout.isValid()) {
        // replace this with a new node for the new layout
        SObjectSerializationNode replacement =
            SObjectSerializationNodeFactory.create(classFact,
                createReadNodes(so.getFactory()), depth);
        return replace(replacement).execute(so, sb);
      } else {
        return doCached(so, sb);
      }
    }

    @ExplodeLoop
    public long doCached(final SObject o, final SnapshotBuffer sb) {
      int start = sb.addObject(o, o.getSOMClass(), FIELD_SIZE * fieldCnt);
      int base = start;

      assert fieldCnt < MAX_FIELD_CNT;

      for (int i = 0; i < fieldCnt; i++) {
        Object value = fieldReads[i].read(o);
        // TODO type profiles could be an optimization (separate profile for each slot)
        // TODO optimize, maybe it is better to add an integer to the objects (indicating their
        // offset) rather than using a map.
        sb.putLongAt(base + (8 * i), cachedSerializers[i].execute(value, sb));
      }
      return sb.calculateReferenceB(start);
    }

    @Override
    public Object deserialize(final DeserializationBuffer sb) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Object deserialize(final DeserializationBuffer sb, final SClass clazz) {
      SObject o;

      if (classFact.hasOnlyImmutableFields()) {
        o = new SImmutableObject(
            clazz,
            classFact,
            classFact.getInstanceLayout());
      } else {
        o = new SMutableObject(clazz,
            classFact,
            classFact.getInstanceLayout());
      }

      if (fieldWrites == null) {
        fieldWrites = insert(createWriteNodes(o));
      }

      sb.putObject(o);

      for (int i = 0; i < fieldWrites.length; i++) {
        sb.installObjectFixup(o, fieldWrites[i]);
      }

      return o;
    }

    public static class SlotFixup extends FixupInformation {
      final CachedSlotWrite csw;
      final SObject         obj;

      public SlotFixup(final SObject obj, final CachedSlotWrite csw) {
        this.csw = csw;
        this.obj = obj;
      }

      @Override
      public void fixUp(final Object res) {
        csw.doWrite(obj, res);
      }
    }
  }

  @GenerateNodeFactory
  public abstract static class SObjectWithoutFieldsSerializationNode
      extends AbstractSerializationNode {

    @ExplodeLoop
    @Specialization
    public long serialize(final SObjectWithoutFields o, final SnapshotBuffer sb) {
      long location = getObjectLocation(o, sb.getSnapshotVersion());
      if (location != -1) {
        return location;
      }
      return sb.calculateReferenceB(sb.addObject(o, o.getSOMClass(), 0));
    }

    @Override
    public Object deserialize(final DeserializationBuffer sb) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Object deserialize(final DeserializationBuffer sb, final SClass clazz) {
      return new SObjectWithoutFields(clazz, clazz.getInstanceFactory());
    }
  }
}
