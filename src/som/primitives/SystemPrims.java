package som.primitives;

import som.interpreter.nodes.PrimitiveNode;
import som.vm.Universe;
import som.vmobjects.SClass;
import som.vmobjects.SInteger;
import som.vmobjects.SObject;
import som.vmobjects.SString;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;


public class SystemPrims {
  public abstract static class LoadPrim extends PrimitiveNode {
    public LoadPrim(final SSymbol selector, final Universe universe) {
      super(selector, universe);
    }

    @Specialization
    public SObject doGeneric(final VirtualFrame frame,
        final SObject receiver, final Object arguments) {
      SSymbol argument = (SSymbol) ((SObject[]) arguments)[0];

      SClass result = universe.loadClass(argument);
      return result != null ? result : universe.nilObject;
    }
  }

  public abstract static class ExitPrim extends PrimitiveNode {
    public ExitPrim(final SSymbol selector, final Universe universe) {
      super(selector, universe);
    }

    @Specialization
    public SObject doGeneric(final VirtualFrame frame,
        final SObject receiver, final Object arguments) {
      SInteger error = (SInteger) ((SObject[]) arguments)[0];
      universe.exit(error.getEmbeddedInteger());
      return receiver;
    }
  }

  public abstract static class GlobalPrim extends PrimitiveNode {
    public GlobalPrim(final SSymbol selector, final Universe universe) {
      super(selector, universe);
    }

    @Specialization
    public SObject doGeneric(final VirtualFrame frame,
        final SObject receiver, final Object arguments) {
      SSymbol argument = (SSymbol) ((SObject[]) arguments)[0];

      SObject result = universe.getGlobal(argument);
      return result != null ? result : universe.nilObject;
    }
  }

  public abstract static class GlobalPutPrim extends PrimitiveNode {
    public GlobalPutPrim(final SSymbol selector, final Universe universe) {
      super(selector, universe);
    }

    @Specialization
    public SObject doGeneric(final VirtualFrame frame,
        final SObject receiver, final Object arguments) {
      SObject value    = ((SObject[]) arguments)[1];
      SSymbol argument = (SSymbol) ((SObject[]) arguments)[0];
      universe.setGlobal(argument, value);
      return value;
    }
  }

  public abstract static class PrintStringPrim extends PrimitiveNode {
    public PrintStringPrim(final SSymbol selector, final Universe universe) {
      super(selector, universe);
    }

    @Specialization
    public SObject doGeneric(final VirtualFrame frame,
        final SObject receiver, final Object arguments) {
      SString argument = (SString) ((SObject[]) arguments)[0];
      Universe.print(argument.getEmbeddedString());
      return receiver;
    }
  }

  public abstract static class PrintNewlinePrim extends PrimitiveNode {
    public PrintNewlinePrim(final SSymbol selector, final Universe universe) {
      super(selector, universe);
    }

    @Specialization
    public SObject doGeneric(final VirtualFrame frame,
        final SObject receiver, final Object arguments) {
      Universe.println();
      return receiver;
    }
  }

  public abstract static class FullGCPrim extends PrimitiveNode {
    public FullGCPrim(final SSymbol selector, final Universe universe) {
      super(selector, universe);
    }

    @Specialization
    public SObject doGeneric(final VirtualFrame frame,
        final SObject receiver, final Object arguments) {
      System.gc();
      return universe.trueObject;
    }
  }

  public abstract static class TimePrim extends PrimitiveNode {
    public TimePrim(final SSymbol selector, final Universe universe) {
      super(selector, universe);
    }

    @Specialization
    public SObject doGeneric(final VirtualFrame frame,
        final SObject receiver, final Object arguments) {
      int time = (int) (System.currentTimeMillis() - startTime);
      return universe.newInteger(time);
    }
  }

  public abstract static class TicksPrim extends PrimitiveNode {
    public TicksPrim(final SSymbol selector, final Universe universe) {
      super(selector, universe);
    }

    @Specialization
    public SObject doGeneric(final VirtualFrame frame,
        final SObject receiver, final Object arguments) {
      int time = (int) (System.nanoTime() / 1000L - startMicroTime);
      return universe.newInteger(time);
    }
  }

  {
    startMicroTime = System.nanoTime() / 1000L;
    startTime = startMicroTime / 1000L;
  }
  private static long startTime;
  private static long startMicroTime;
}
