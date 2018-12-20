package tools.snapshot.nodes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.ExplodeLoop;

import som.compiler.MixinDefinition.SlotDefinition;
import som.interpreter.TruffleCompiler;
import som.interpreter.nodes.dispatch.AbstractDispatchNode;
import som.interpreter.nodes.dispatch.CachedSlotRead;
import som.interpreter.nodes.dispatch.CachedSlotRead.SlotAccess;
import som.interpreter.nodes.dispatch.CachedSlotWrite;
import som.interpreter.nodes.dispatch.DispatchGuard;
import som.interpreter.nodes.dispatch.UninitializedDispatchNode;
import som.interpreter.objectstorage.ObjectLayout;
import som.interpreter.objectstorage.ObjectTransitionSafepoint;
import som.interpreter.objectstorage.StorageLocation;
import som.vmobjects.SClass;
import som.vmobjects.SObject;
import som.vmobjects.SObject.SImmutableObject;
import som.vmobjects.SObject.SMutableObject;
import som.vmobjects.SObjectWithClass;
import som.vmobjects.SObjectWithClass.SObjectWithoutFields;
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

    protected ObjectSerializationNode(final SClass clazz) {
      super(clazz);
    }

    public static ObjectSerializationNode create(final SClass clazz) {
      return UninitializedObjectSerializationNodeFactory.create(clazz);
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

    protected UninitializedObjectSerializationNode(final SClass clazz) {
      super(clazz);
    }

    @Specialization
    public void serialize(final SObjectWithClass o, final SnapshotBuffer sb) {
      TruffleCompiler.transferToInterpreterAndInvalidate(
          "Initialize ObjectSerializationNode.");
      if (o instanceof SObject) {
        replace(SObjectSerializationNodeFactory.create(clazz,
            createReadNodes((SObject) o))).serialize((SObject) o, sb);
      } else if (o instanceof SObjectWithoutFields) {
        replace(SObjectWithoutFieldsSerializationNodeFactory.create(clazz)).serialize(
            (SObjectWithoutFields) o,
            sb);
      }
    }

    @Override
    public Object deserialize(final DeserializationBuffer sb) {
      if (classFact.hasSlots()) {
        return replace(SObjectSerializationNodeFactory.create(clazz)).deserialize(
            sb);
      } else {
        return replace(
            SObjectWithoutFieldsSerializationNodeFactory.create(clazz)).deserialize(sb);
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

    protected SObjectSerializationNode(final SClass clazz,
        final CachedSlotRead[] reads) {
      super(clazz);
      layout = classFact.getInstanceLayout();
      fieldReads = insert(reads);
      fieldCnt = fieldReads.length;

      cachedSerializers = new CachedSerializationNode[fieldCnt];
      for (int i = 0; i < fieldCnt; i++) {
        cachedSerializers[i] = CachedSerializationNodeFactory.create();
      }
    }

    protected SObjectSerializationNode(final SClass clazz) {
      super(clazz);
      layout = classFact.getInstanceLayout();
      fieldCnt = layout.getNumberOfFields();
    }

    @Specialization
    public void serialize(final SObject so, final SnapshotBuffer sb) {
      if (!so.isLayoutCurrent()) {
        CompilerDirectives.transferToInterpreter();
        ObjectTransitionSafepoint.INSTANCE.transitionObject(so);
      }

      if (!layout.isValid()) {
        // replace this with a new node for the new layout
        SObjectSerializationNode replacement =
            SObjectSerializationNodeFactory.create(clazz, createReadNodes(so));
        replace(replacement).serialize(so, sb);
      } else {
        doCached(so, sb);
      }
    }

    @ExplodeLoop
    public void doCached(final SObject o, final SnapshotBuffer sb) {
      int base = sb.addObjectWithFields(o, clazz, fieldCnt);

      for (int i = 0; i < fieldCnt; i++) {
        Object value = fieldReads[i].read(o);
        // TODO type profiles could be an optimization (separate profile for each slot)
        // TODO optimize, maybe it is better to add an integer to the objects (indicating their
        // offset) rather than using a map.

        if (!sb.getRecord().containsObjectUnsync(value)) {
          // Referenced Object not yet in snapshot
          cachedSerializers[i].execute(value, sb);
        }

        sb.putLongAt(base + (8 * i), sb.getRecord().getObjectPointer(value));
      }
    }

    @Override
    public Object deserialize(final DeserializationBuffer sb) {
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
      extends ObjectSerializationNode {

    protected SObjectWithoutFieldsSerializationNode(final SClass clazz) {
      super(clazz);
    }

    @ExplodeLoop
    @Specialization
    public void serialize(final SObjectWithoutFields o, final SnapshotBuffer sb) {
      sb.addObject(o, clazz, 0);
    }

    @Override
    public Object deserialize(final DeserializationBuffer sb) {
      return new SObjectWithoutFields(clazz,
          classFact);
    }
  }
}
