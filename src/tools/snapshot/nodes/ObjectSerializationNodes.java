package tools.snapshot.nodes;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;

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
import som.vmobjects.SClass;
import som.vmobjects.SObject;
import som.vmobjects.SObject.SImmutableObject;
import som.vmobjects.SObject.SMutableObject;
import som.vmobjects.SObjectWithClass.SObjectWithoutFields;
import tools.snapshot.SnapshotBuffer;
import tools.snapshot.nodes.ObjectSerializationNodesFactory.SObjectSerializationNodeFactory;
import tools.snapshot.nodes.ObjectSerializationNodesFactory.SObjectWithoutFieldsSerializationNodeFactory;
import tools.snapshot.nodes.ObjectSerializationNodesFactory.UninitializedObjectSerializationNodeFactory;


public abstract class ObjectSerializationNodes {

  public abstract static class ObjectSerializationNode extends AbstractSerializationNode {

    protected ObjectSerializationNode(final SClass clazz) {
      super(clazz);
    }

    public static ObjectSerializationNode create(final SClass clazz) {
      return UninitializedObjectSerializationNodeFactory.create(clazz);
    }

    protected final CachedSlotWrite[] createWriteNodes(final SObject o) {
      CompilerDirectives.transferToInterpreter();

      // sort slots so we get the right order
      ObjectLayout layout = clazz.getLayoutForInstancesUnsafe();
      ArrayList<SlotDefinition> definitions = new ArrayList<>();
      layout.getStorageLocations().getKeys().forEach(sd -> definitions.add(sd));
      CachedSlotWrite[] writes = new CachedSlotWrite[definitions.size()];

      Collections.sort(definitions, (a, b) -> {
        return a.getName().getString().compareTo(b.getName().getString());
      });

      for (int i = 0; i < writes.length; i++) {
        StorageLocation loc =
            layout.getStorageLocation(definitions.get(i));

        AbstractDispatchNode next =
            UninitializedDispatchNode.createLexicallyBound(loc.getSlot().getSourceSection(),
                loc.getSlot().getName(), clazz.getMixinDefinition().getMixinId());

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
      ObjectLayout layout = clazz.getLayoutForInstancesUnsafe();
      layout.getStorageLocations().getKeys().forEach(sd -> definitions.add(sd));
      CachedSlotRead[] reads = new CachedSlotRead[definitions.size()];

      // Reads are ordered by the name of the slots
      // this way the entry order is deterministic and independent of layout changes
      // same order in replay when reading
      Collections.sort(definitions, (a, b) -> {
        return a.getName().getString().compareTo(b.getName().getString());
      });

      for (int i = 0; i < reads.length; i++) {
        StorageLocation loc = layout.getStorageLocation(definitions.get(i));

        AbstractDispatchNode next =
            UninitializedDispatchNode.createLexicallyBound(loc.getSlot().getSourceSection(),
                loc.getSlot().getName(), clazz.getMixinDefinition().getMixinId());

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
    public void serialize(final Object o, final SnapshotBuffer sb) {
      if (o instanceof SObject) {
        replace(SObjectSerializationNodeFactory.create(clazz,
            createReadNodes((SObject) o))).serialize(o, sb);
      } else if (o instanceof SObjectWithoutFields) {
        replace(SObjectWithoutFieldsSerializationNodeFactory.create(clazz)).serialize(o, sb);
      }
    }

    @Override
    public Object deserialize(final ByteBuffer sb) {
      if (clazz.getFactory().hasSlots()) {
        replace(SObjectSerializationNodeFactory.getInstance().createNode(clazz));
        return clazz.getSerializer().deserialize(sb);
      } else {
        replace(SObjectWithoutFieldsSerializationNodeFactory.create(clazz));
        return clazz.getSerializer().deserialize(sb);
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
      layout = clazz.getLayoutForInstancesUnsafe();
      fieldReads = insert(reads);
      fieldCnt = fieldReads.length;
    }

    @Specialization
    public void serialize(final Object o, final SnapshotBuffer sb) {
      assert o instanceof SObject;

      SObject so = (SObject) o;
      if (!so.isLayoutCurrent()) {
        ObjectTransitionSafepoint.INSTANCE.transitionObject(so);
      }

      if (!layout.isValid()) {
        // replace this with a new node for the new layout
        SObjectSerializationNode replacement =
            SObjectSerializationNodeFactory.create(clazz, createReadNodes(so));
        replace(replacement).serialize(o, sb);
      } else {
        doCached((SObject) o, sb);
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
      int base = sb.addObjectWithFields(o, clazz, fieldCnt);

      if (cachedSerializers == null) {
        cachedSerializers = insert(getSerializers(o));
      }

      for (int i = 0; i < fieldCnt; i++) {
        Object value = fieldReads[i].read(o);
        // TODO type profiles could be an optimization (separate profile for each slot)
        // TODO optimize, maybe it is better to add an integer to the objects (indicating their
        // offset) rather than using a map.

        if (!sb.containsObject(value)) {
          // Referenced Object not yet in snapshot
          cachedSerializers[i].serialize(value, sb);
        }

        sb.putLongAt(base + (8 * i), sb.getObjectPointer(value));
      }
    }

    @Override
    public Object deserialize(final ByteBuffer sb) {
      ClassFactory factory = clazz.getInstanceFactory();
      SObject o;

      if (factory.hasOnlyImmutableFields()) {
        o = new SImmutableObject(clazz, factory,
            clazz.getInstanceFactory().getInstanceLayout());
      } else {
        o = new SMutableObject(clazz, factory,
            clazz.getInstanceFactory().getInstanceLayout());
      }

      if (fieldWrites == null) {
        fieldWrites = insert(createWriteNodes(o));
      }

      for (int i = 0; i < fieldWrites.length; i++) {
        Object ref = deserializeReference(sb);
        fieldWrites[i].doWrite(o, ref);
      }

      return o;
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
    public void serialize(final Object o, final SnapshotBuffer sb) {
      assert o instanceof SObjectWithoutFields;
      sb.addObject(o, clazz, 0);
    }

    @Override
    public Object deserialize(final ByteBuffer sb) {
      ClassFactory factory = clazz.getInstanceFactory();
      return new SObjectWithoutFields(clazz, factory);
    }
  }
}
