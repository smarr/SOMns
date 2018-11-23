package tools.snapshot.nodes;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.MaterializedFrame;

import som.compiler.Variable.Internal;
import som.interpreter.FrameOnStackMarker;
import som.interpreter.Method;
import som.interpreter.Types;
import som.vm.constants.Classes;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SBlock;
import som.vmobjects.SClass;
import som.vmobjects.SInvokable;
import som.vmobjects.SSymbol;
import tools.snapshot.SnapshotBackend;
import tools.snapshot.SnapshotBuffer;
import tools.snapshot.SnapshotRecord;
import tools.snapshot.deserialization.DeserializationBuffer;
import tools.snapshot.deserialization.FixupInformation;


@GenerateNodeFactory
public abstract class BlockSerializationNode extends AbstractSerializationNode {

  private static final int SINVOKABLE_SIZE = Short.BYTES;

  public BlockSerializationNode(final SClass clazz) {
    super(clazz);
  }

  // TODO specialize on different blocks
  @Specialization
  public void serialize(final SBlock block, final SnapshotBuffer sb) {

    MaterializedFrame mf = block.getContextOrNull();

    if (mf == null) {
      int base = sb.addObject(block, clazz, SINVOKABLE_SIZE + 2);
      SInvokable meth = block.getMethod();
      // System.out.println("Block without frame " + meth.getIdentifier());
      sb.putShortAt(base, meth.getIdentifier().getSymbolId());
      sb.putShortAt(base + 2, (short) 0);
    } else {
      FrameDescriptor fd = mf.getFrameDescriptor();

      Object[] args = mf.getArguments();

      int start = sb.addObject(block, clazz,
          SINVOKABLE_SIZE + ((args.length + fd.getSlots().size()) * Long.BYTES) + 2);
      int base = start;

      SInvokable meth = block.getMethod();
      // System.out.println("Block " + meth.getIdentifier());
      sb.putShortAt(base, meth.getIdentifier().getSymbolId());
      // System.out.println(
      // "with " + args.length + " args at: " + base + " in " + sb.getOwner().getThreadId());
      // for (Object o : args) {
      // System.out.println("\t" + o);
      // }

      sb.putByteAt(base + 2, (byte) args.length);
      base += 3;

      SnapshotRecord record = sb.getRecord();
      for (int i = 0; i < args.length; i++) {
        // TODO optimization: cache argument serialization
        Types.getClassOf(args[i]).serialize(args[i], sb);
        sb.putLongAt(base + (i * Long.BYTES), record.getObjectPointer(args[i]));
      }

      base += (args.length * Long.BYTES);

      int j = 0;

      sb.putByteAt(base, (byte) fd.getSlots().size());
      base++;
      for (FrameSlot slot : fd.getSlots()) {
        // assume this is ordered by index
        assert slot.getIndex() == j;

        // TODO optimization: MaterializedFrameSerialization Nodes that are associated with the
        // Invokables Frame Descriptor. Possibly use Local Var Read Nodes.
        Object value = mf.getValue(slot);
        // System.out.println("Slot" + slot + value);
        switch (fd.getFrameSlotKind(slot)) {
          case Boolean:
            Classes.booleanClass.serialize(value, sb);
            break;
          case Double:
            Classes.doubleClass.serialize(value, sb);
            break;
          case Long:
            Classes.integerClass.serialize(value, sb);
            break;
          case Object:
            // We are going to represent this as a boolean, the slot will handled in replay
            if (value instanceof FrameOnStackMarker) {
              value = ((FrameOnStackMarker) value).isOnStack();
              Classes.booleanClass.serialize(value, sb);
            } else {
              assert value instanceof SAbstractObject;
              Types.getClassOf(value).serialize(value, sb);
            }
            break;
          case Illegal:
            // Uninitialized variables
            Types.getClassOf(fd.getDefaultValue()).serialize(fd.getDefaultValue(), sb);
            break;
          default:
            throw new IllegalArgumentException("Unexpected SlotKind");
        }

        sb.putLongAt(base + (j * Long.BYTES), sb.getRecord().getObjectPointer(value));
        j++;
        // dont redo frame!
        // just serialize locals and arguments ordered by their slotnumber
        // we can get the frame from the invokables root node
      }

      base += j * Long.BYTES;
      assert base == start + SINVOKABLE_SIZE
          + ((args.length + fd.getSlots().size()) * Long.BYTES) + 2;
    }
  }

  @Override
  public Object deserialize(final DeserializationBuffer bb) {
    short sinv = bb.getShort();
    SSymbol inv = SnapshotBackend.getSymbolForId(sinv);

    SInvokable invokable = SnapshotBackend.lookupInvokable(sinv);
    assert invokable != null : "Invokable not found";
    FrameDescriptor fd = ((Method) invokable.getInvokable()).getLexicalScope().getOuterMethod()
                                                            .getMethod().getFrameDescriptor();
    // TODO Nullpointer when getting FD

    // read num args
    int numArgs = bb.get();
    Object[] args = new Object[numArgs];

    // System.out.println("Block with " + inv);
    // read args
    for (int i = 0; i < numArgs; i++) {
      Object arg = bb.getReference();
      if (DeserializationBuffer.needsFixup(arg)) {
        bb.installFixup(new BlockArgumentFixup(args, i));
      } else {
        args[i] = arg;
      }
    }

    // System.out.println(Arrays.toString(args));

    MaterializedFrame frame = Truffle.getRuntime().createMaterializedFrame(args, fd);

    int numSlots = bb.get();
    if (numSlots > 0) {
      assert numSlots == fd.getSlots().size();

      for (int i = 0; i < numSlots; i++) {
        FrameSlot slot = fd.getSlots().get(i);

        Object o = bb.getReference();

        if (DeserializationBuffer.needsFixup(o)) {
          bb.installFixup(new FrameSlotFixup(frame, slot));
        } else {
          if (slot.getIdentifier() instanceof Internal) {
            FrameOnStackMarker fosm = new FrameOnStackMarker();
            if (!(boolean) o) {
              fosm.frameNoLongerOnStack();
            }
            o = fosm;
          }

          boolean illegal = fd.getFrameSlotKind(slot) == FrameSlotKind.Illegal;

          if (o instanceof Boolean) {
            frame.setBoolean(slot, (boolean) o);
            if (illegal) {
              fd.setFrameSlotKind(slot, FrameSlotKind.Boolean);
            }
          } else if (o instanceof Double) {
            frame.setDouble(slot, (double) o);
            if (illegal) {
              fd.setFrameSlotKind(slot, FrameSlotKind.Double);
            }
          } else if (o instanceof Long) {
            frame.setLong(slot, (long) o);
            if (illegal) {
              fd.setFrameSlotKind(slot, FrameSlotKind.Long);
            }
          } else {
            frame.setObject(slot, o);
            if (illegal) {
              fd.setFrameSlotKind(slot, FrameSlotKind.Object);
            }
          }
        }
      }
    }

    frame.materialize();

    return new SBlock(invokable, frame);
  }

  public static class BlockArgumentFixup extends FixupInformation {
    Object[] args;
    int      idx;

    public BlockArgumentFixup(final Object[] args, final int idx) {
      this.args = args;
      this.idx = idx;
    }

    @Override
    public void fixUp(final Object o) {
      args[idx] = o;
    }
  }

  public static class FrameSlotFixup extends FixupInformation {
    FrameSlot         slot;
    MaterializedFrame frame;

    public FrameSlotFixup(final MaterializedFrame frame, final FrameSlot slot) {
      this.frame = frame;
      this.slot = slot;
    }

    @Override
    public void fixUp(final Object o) {
      frame.setObject(slot, o);
    }
  }
}
