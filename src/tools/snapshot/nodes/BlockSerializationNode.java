package tools.snapshot.nodes;

import java.nio.ByteBuffer;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.MaterializedFrame;

import som.compiler.Variable.Internal;
import som.interpreter.FrameOnStackMarker;
import som.interpreter.Types;
import som.vm.Symbols;
import som.vm.constants.Classes;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SBlock;
import som.vmobjects.SInvokable;
import tools.snapshot.SnapshotBackend;
import tools.snapshot.SnapshotBuffer;


public class BlockSerializationNode extends AbstractSerializationNode {
  private static final int SINVOKABLE_SIZE = Short.BYTES;

  @Override
  public void serialize(final Object o, final SnapshotBuffer sb) {
    assert o instanceof SBlock;
    SBlock block = (SBlock) o;

    MaterializedFrame mf = block.getContextOrNull();

    if (mf == null) {
      int base = sb.addObject(block, Classes.blockClass, SINVOKABLE_SIZE + 2);
      SInvokable meth = block.getMethod();
      String sid = meth.getIdentifier();
      sb.putShortAt(base, Symbols.symbolFor(sid).getSymbolId());
      sb.putShortAt(base + 2, (short) 0);
    } else {
      FrameDescriptor fd = mf.getFrameDescriptor();

      Object[] args = mf.getArguments();

      int start = sb.addObject(block, Classes.blockClass,
          SINVOKABLE_SIZE + ((args.length + fd.getSlots().size()) * Long.BYTES) + 2);
      int base = start;

      SInvokable meth = block.getMethod();
      String sid = meth.getIdentifier();
      sb.putShortAt(base, Symbols.symbolFor(sid).getSymbolId());
      sb.putByteAt(base + 2, (byte) args.length);
      base += 3;

      for (int i = 0; i < args.length; i++) {
        Types.getClassOf(args[i]).getSerializer().serialize(args[i], sb);
        sb.putLongAt(base + (i * Long.BYTES), sb.getObjectPointer(args[i]));
      }

      base += (args.length * Long.BYTES);

      int j = 0;

      sb.putByteAt(base, (byte) fd.getSlots().size());
      base++;
      for (FrameSlot slot : fd.getSlots()) {
        // assume this is ordered by index
        assert slot.getIndex() == j;

        Object value = mf.getValue(slot);
        switch (fd.getFrameSlotKind(slot)) {
          case Boolean:
            Classes.booleanClass.getSerializer().serialize(value, sb);
            break;
          case Byte:
          case Double:
          case Float:
            Classes.doubleClass.getSerializer().serialize(value, sb);
            break;
          case Int:
          case Long:
            Classes.integerClass.getSerializer().serialize(value, sb);
            break;
          case Object:
            // We are going to represent this as a boolean, the slot will handled in replay
            if (value instanceof FrameOnStackMarker) {
              value = ((FrameOnStackMarker) value).isOnStack();
              Classes.booleanClass.getSerializer().serialize(value, sb);
            } else {
              assert value instanceof SAbstractObject;
              Types.getClassOf(value).getSerializer().serialize(value, sb);
            }
            break;
          case Illegal:
          default:
            throw new IllegalStateException("We don't handle illegal frame slots");

        }

        sb.putLongAt(base + (j * Long.BYTES), sb.getObjectPointer(value));
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
  public Object deserialize(final ByteBuffer bb) {
    short sinv = bb.getShort();

    SInvokable invokable = SnapshotBackend.lookupInvokable(sinv);
    FrameDescriptor fd = invokable.getInvokable().getFrameDescriptor();

    // read num args
    int numArgs = bb.get();
    Object[] args = new Object[numArgs];

    // read args
    for (int i = 0; i < numArgs; i++) {
      args[i] = deserializeReference(bb);
    }

    MaterializedFrame frame = Truffle.getRuntime().createMaterializedFrame(args, fd);

    int numSlots = bb.get();
    assert numSlots == fd.getSlots().size();

    for (int i = 0; i < numSlots; i++) {
      FrameSlot slot = fd.getSlots().get(i);

      Object o = deserializeReference(bb);

      switch (fd.getFrameSlotKind(slot)) {
        case Boolean:
          frame.setBoolean(slot, (boolean) o);
          break;
        case Byte:
        case Double:
        case Float:
          frame.setDouble(slot, (double) o);
          break;
        case Int:
        case Long:
          frame.setLong(slot, (long) o);
          break;
        case Object:
          if (slot.getIdentifier() instanceof Internal) {
            FrameOnStackMarker fosm = new FrameOnStackMarker();
            if (!(boolean) o) {
              fosm.frameNoLongerOnStack();
            }
            o = fosm;
          }
          frame.setObject(slot, o);
          break;
        case Illegal:
        default:
          throw new IllegalStateException("We don't handle illegal frame slots");
      }
    }

    return new SBlock(invokable, frame);
  }
}
