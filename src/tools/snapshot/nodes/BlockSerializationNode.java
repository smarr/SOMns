package tools.snapshot.nodes;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.MaterializedFrame;

import som.compiler.Variable.Internal;
import som.interpreter.FrameOnStackMarker;
import som.primitives.ObjectPrims.ClassPrim;
import som.primitives.ObjectPrimsFactory.ClassPrimFactory;
import som.vm.constants.Classes;
import som.vm.constants.Nil;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SBlock;
import som.vmobjects.SInvokable;
import tools.snapshot.SnapshotBackend;
import tools.snapshot.SnapshotBuffer;
import tools.snapshot.deserialization.DeserializationBuffer;
import tools.snapshot.deserialization.FixupInformation;


@GenerateNodeFactory
public abstract class BlockSerializationNode extends AbstractSerializationNode {

  private static final int SINVOKABLE_SIZE = Short.BYTES;

  // TODO specialize on different blocks
  @Specialization
  public long serialize(final SBlock block, final SnapshotBuffer sb) {
    long location = getObjectLocation(block, sb.getSnapshotVersion());
    if (location != -1) {
      return location;
    }

    MaterializedFrame mf = block.getContextOrNull();

    if (mf == null) {
      int start = sb.addObject(block, Classes.blockClass, SINVOKABLE_SIZE + 1);
      int base = start;
      SInvokable meth = block.getMethod();
      sb.putShortAt(base, meth.getIdentifier().getSymbolId());
      sb.putByteAt(base + 2, (byte) 0);
      return sb.calculateReferenceB(start);
    } else {
      int start = sb.addObject(block, Classes.blockClass, SINVOKABLE_SIZE + 1 + Long.BYTES);
      int base = start;
      SInvokable meth = block.getMethod();
      sb.putShortAt(base, meth.getIdentifier().getSymbolId());
      sb.putByteAt(base + 2, (byte) 1);

      long framelocation = meth.getFrameSerializer().execute(block, sb);

      sb.putLongAt(base + 3, framelocation);
      return sb.calculateReferenceB(start);
    }
  }

  @Override
  public Object deserialize(final DeserializationBuffer bb) {
    short sinv = bb.getShort();
    byte framePresent = bb.get();

    SInvokable invokable = SnapshotBackend.lookupInvokable(sinv);
    assert invokable != null : "Invokable not found";

    MaterializedFrame frame = null;
    if (framePresent == 1) {
      Object result = bb.getMaterializedFrame(invokable);
      if (DeserializationBuffer.needsFixup(result)) {
        SBlock block = new SBlock(invokable, null);
        bb.installFixup(new BlockFrameFixup(block));
        return block;
      } else {
        frame = (MaterializedFrame) result;
      }
    }
    return new SBlock(invokable, frame);
  }

  public static class BlockFrameFixup extends FixupInformation {
    SBlock block;

    public BlockFrameFixup(final SBlock block) {
      this.block = block;
    }

    @Override
    public void fixUp(final Object o) {
      try {
        Field field = SBlock.class.getDeclaredField("context");
        field.setAccessible(true);
        Field modifiersField = Field.class.getDeclaredField("modifiers");
        modifiersField.setAccessible(true);
        modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL);
        field.set(block, o);
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
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

  @GenerateNodeFactory
  public abstract static class FrameSerializationNode extends AbstractSerializationNode {
    private final FrameDescriptor frameDescriptor;
    @Child ClassPrim              classPrim = ClassPrimFactory.create(null);

    protected FrameSerializationNode(final FrameDescriptor frameDescriptor) {
      this.frameDescriptor = frameDescriptor;
    }

    @TruffleBoundary
    private List<? extends FrameSlot> getFrameslots() {
      // TODO can we cache this?
      return frameDescriptor.getSlots();
    }

    // Truffle doesn't seem to like me passing a frame, so we pass the entire block
    @Specialization
    public long serialize(final SBlock block, final SnapshotBuffer sb) {
      MaterializedFrame frame = block.getContext();
      Object[] args = frame.getArguments();

      List<? extends FrameSlot> slots = getFrameslots();

      int slotCnt = slots.size();
      assert slotCnt < 0xFF : "Too many slots";
      assert args.length < 0xFF : "Too many arguments";

      int start =
          sb.reserveSpace(2 + ((args.length + slotCnt) * Long.BYTES));
      int base = start;

      sb.putByteAt(base, (byte) args.length);
      base++;

      for (int i = 0; i < args.length; i++) {
        // TODO optimization: cache argument serialization
        sb.putLongAt(base + (i * Long.BYTES),
            classPrim.executeEvaluated(args[i]).serialize(args[i], sb));
      }

      base += (args.length * Long.BYTES);

      int j = 0;

      sb.putByteAt(base, (byte) slotCnt);
      base++;

      for (FrameSlot slot : slots) {
        // assume this is ordered by index
        assert slot.getIndex() == j;

        // TODO optimization: MaterializedFrameSerialization Nodes that are associated with the
        // Invokables Frame Descriptor. Possibly use Local Var Read Nodes.
        Object value = frame.getValue(slot);
        long valueLocation;
        if (value == Nil.nilObject) {
          valueLocation = Nil.nilObject.getSOMClass().serialize(Nil.nilObject, sb);
        } else {
          switch (frameDescriptor.getFrameSlotKind(slot)) {
            case Boolean:
              valueLocation = Classes.booleanClass.serialize(value, sb);
              break;
            case Double:
              valueLocation = Classes.doubleClass.serialize(value, sb);
              break;
            case Long:
              valueLocation = Classes.integerClass.serialize(value, sb);
              break;
            case Object:
              // We are going to represent this as a boolean, the slot will handled in replay
              if (value instanceof FrameOnStackMarker) {
                value = ((FrameOnStackMarker) value).isOnStack();
                valueLocation = Classes.booleanClass.serialize(value, sb);
              } else {
              assert value instanceof SAbstractObject || value instanceof String : "was"
                  + value.toString();
                valueLocation = classPrim.executeEvaluated(value).serialize(value, sb);
              }
              break;
            case Illegal:
              // Uninitialized variables
              valueLocation = classPrim.executeEvaluated(frameDescriptor.getDefaultValue())
                                       .serialize(frameDescriptor.getDefaultValue(), sb);
              break;
            default:
              throw new IllegalArgumentException("Unexpected SlotKind");
          }
        }

        sb.putLongAt(base + (j * Long.BYTES), valueLocation);
        j++;
        // dont redo frame!
        // just serialize locals and arguments ordered by their slotnumber
        // we can get the frame from the invokables root node
      }
      base += j * Long.BYTES;
      return sb.calculateReferenceB(start);
    }

    @Override
    public Object deserialize(final DeserializationBuffer bb) {
      // read num args
      int numArgs = bb.get();
      Object[] args = new Object[numArgs];

      // read args
      for (int i = 0; i < numArgs; i++) {
        Object arg = bb.getReference();
        if (DeserializationBuffer.needsFixup(arg)) {
          bb.installFixup(new BlockArgumentFixup(args, i));
        } else {
          args[i] = arg;
        }
      }

      MaterializedFrame frame =
          Truffle.getRuntime().createMaterializedFrame(args, frameDescriptor);

      int numSlots = bb.get();
      if (numSlots > 0) {
        assert numSlots == frameDescriptor.getSlots().size();

        for (int i = 0; i < numSlots; i++) {
          FrameSlot slot = frameDescriptor.getSlots().get(i);

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

            boolean illegal = frameDescriptor.getFrameSlotKind(slot) == FrameSlotKind.Illegal;

            if (o instanceof Boolean) {
              frame.setBoolean(slot, (boolean) o);
              if (illegal) {
                frameDescriptor.setFrameSlotKind(slot, FrameSlotKind.Boolean);
              }
            } else if (o instanceof Double) {
              frame.setDouble(slot, (double) o);
              if (illegal) {
                frameDescriptor.setFrameSlotKind(slot, FrameSlotKind.Double);
              }
            } else if (o instanceof Long) {
              frame.setLong(slot, (long) o);
              if (illegal) {
                frameDescriptor.setFrameSlotKind(slot, FrameSlotKind.Long);
              }
            } else {
              frame.setObject(slot, o);
              if (illegal) {
                frameDescriptor.setFrameSlotKind(slot, FrameSlotKind.Object);
              }
            }
          }
        }
      }

      frame.materialize();
      return frame;
    }
  }
}
