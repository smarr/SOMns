package tools.snapshot.nodes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
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
import som.vmobjects.SObject;
import som.vmobjects.SObject.SImmutableObject;
import som.vmobjects.SObject.SMutableObject;
import som.vmobjects.SObjectWithClass;
import som.vmobjects.SObjectWithClass.SObjectWithoutFields;
import tools.snapshot.SnapshotBackend;
import tools.snapshot.SnapshotBuffer;
import tools.snapshot.deserialization.DeserializationBuffer;
import tools.snapshot.deserialization.FixupInformation;
import tools.snapshot.nodes.ObjectSerializationNodesFactory.SObjectSerializationNodeFactory;
import tools.snapshot.nodes.ObjectSerializationNodesFactory.SObjectWithoutFieldsSerializationNodeFactory;
import tools.snapshot.nodes.ObjectSerializationNodesFactory.UninitializedObjectSerializationNodeFactory;


public abstract class ObjectSerializationNodes {

  public abstract static class ObjectSerializationNode extends AbstractSerializationNode {

    protected class SlotDefinitionSorter implements Comparator<SlotDefinition> {
      @Override
      public int compare(final SlotDefinition o1, final SlotDefinition o2) {
        return o1.getName().getString().compareTo(o2.getName().getString());
      }
    }

    protected ObjectSerializationNode(final ClassFactory classFact) {
      super(classFact);
    }

    public static ObjectSerializationNode create(final ClassFactory classFact) {
      return UninitializedObjectSerializationNodeFactory.create(classFact);
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

    protected final CachedSlotRead[] createReadNodes(final SObject o) {
      CompilerDirectives.transferToInterpreter();

      ArrayList<SlotDefinition> definitions = new ArrayList<>();
      ObjectLayout layout = classFact.getInstanceLayout();
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
                loc.getSlot().getName(), classFact.getMixinDefinition().getMixinId());

        reads[i] =
            loc.getReadNode(SlotAccess.FIELD_READ,
                DispatchGuard.createSObjectCheck(o),
                next,
                false);
      }

      return reads;
    }
  }

  @GenerateNodeFactory
  public abstract static class UninitializedObjectSerializationNode
      extends ObjectSerializationNode {

    protected UninitializedObjectSerializationNode(final ClassFactory classFact) {
      super(classFact);
    }

    @Specialization
    public void serialize(final SObjectWithClass o, final SnapshotBuffer sb) {
      if (o instanceof SObject) {
        replace(SObjectSerializationNodeFactory.create(classFact,
            createReadNodes((SObject) o))).serialize((SObject) o, sb);
      } else if (o instanceof SObjectWithoutFields) {
        replace(SObjectWithoutFieldsSerializationNodeFactory.create(classFact)).serialize(
            (SObjectWithoutFields) o,
            sb);
      }
    }

    @Override
    public Object deserialize(final DeserializationBuffer sb) {
      if (classFact.hasSlots()) {
        return replace(SObjectSerializationNodeFactory.create(classFact, null)).deserialize(
            sb);
      } else {
        return replace(
            SObjectWithoutFieldsSerializationNodeFactory.create(classFact)).deserialize(sb);
      }
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
    protected final ObjectLayout                layout;

    protected SObjectSerializationNode(final ClassFactory classFact,
        final CachedSlotRead[] reads) {
      super(classFact);
      layout = classFact.getInstanceLayout();
      fieldReads = insert(reads);
      fieldCnt = fieldReads.length;
    }

    @Specialization
    public void serialize(final SObject so, final SnapshotBuffer sb) {
      if (!so.isLayoutCurrent()) {
        ObjectTransitionSafepoint.INSTANCE.transitionObject(so);
      }

      if (!layout.isValid()) {
        // replace this with a new node for the new layout
        SObjectSerializationNode replacement =
            SObjectSerializationNodeFactory.create(classFact, createReadNodes(so));
        replace(replacement).serialize(so, sb);
      } else {
        doCached(so, sb);
      }
    }

    protected final CachedSerializationNode[] getSerializers(final SObject o) {
      CachedSerializationNode[] nodes = new CachedSerializationNode[fieldCnt];
      for (int i = 0; i < fieldCnt; i++) {
        Object value = fieldReads[i].read(o);
        nodes[i] = CachedSerializationNodeFactory.create(value);
      }
      return nodes;
    }

    @ExplodeLoop
    public void doCached(final SObject o, final SnapshotBuffer sb) {
      int base = sb.addObjectWithFields(o, classFact, fieldCnt);

      if (cachedSerializers == null) {
        cachedSerializers = insert(getSerializers(o));
      }

      for (int i = 0; i < fieldCnt; i++) {
        Object value = fieldReads[i].read(o);
        // TODO type profiles could be an optimization (separate profile for each slot)
        // TODO optimize, maybe it is better to add an integer to the objects (indicating their
        // offset) rather than using a map.

        if (!sb.getRecord().containsObject(value)) {
          // Referenced Object not yet in snapshot
          cachedSerializers[i].serialize(value, sb);
        }

        sb.putLongAt(base + (8 * i), sb.getRecord().getObjectPointer(value));
      }
    }

    @Override
    public Object deserialize(final DeserializationBuffer sb) {
      SObject o;

      if (classFact.hasOnlyImmutableFields()) {
        o = new SImmutableObject(
            SnapshotBackend.lookupClass(classFact.getIdentifier()),
            classFact,
            classFact.getInstanceLayout());
      } else {
        o = new SMutableObject(SnapshotBackend.lookupClass(classFact.getIdentifier()),
            classFact,
            classFact.getInstanceLayout());
      }

      if (fieldWrites == null) {
        fieldWrites = insert(createWriteNodes(o));
      }

      for (int i = 0; i < fieldWrites.length; i++) {
        Object ref = sb.getReference();
        if (DeserializationBuffer.needsFixup(ref)) {
          sb.installFixup(new SlotFixup(o, fieldWrites[i]));
        } else {
          fieldWrites[i].doWrite(o, ref);
        }
      }

      return o;
    }

    private static class SlotFixup extends FixupInformation {
      final CachedSlotWrite csw;
      final SObject         obj;

      SlotFixup(final SObject obj, final CachedSlotWrite csw) {
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
      extends ObjectSerializationNode {

    protected SObjectWithoutFieldsSerializationNode(final ClassFactory classFact) {
      super(classFact);
    }

    @ExplodeLoop
    @Specialization
    public void serialize(final SObjectWithoutFields o, final SnapshotBuffer sb) {
      sb.addObject(o, classFact, 0);
    }

    @Override
    public Object deserialize(final DeserializationBuffer sb) {
      return new SObjectWithoutFields(SnapshotBackend.lookupClass(classFact.getIdentifier()),
          classFact);
    }
  }
}
