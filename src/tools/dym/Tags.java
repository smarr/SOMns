package tools.dym;

public abstract class Tags {
  private Tags() {}

  // this is some form of invoke in the source, unclear what it is during program execution
  public final class UnspecifiedInvoke extends Tags {
    private UnspecifiedInvoke() {}
  }

  // a virtual invoke where the lookup was cached
  public final class CachedVirtualInvoke extends Tags {
    private CachedVirtualInvoke() {}
  }

  // a closure invoke where the closure method was cached
  public final class CachedClosureInvoke extends Tags {
    private CachedClosureInvoke() {}
  }

  // the lexical site of a virtual invoke
  public final class VirtualInvoke extends Tags {
    private VirtualInvoke() {}
  }

  // the lexical site of a virtual invoke
  public final class VirtualInvokeReceiver extends Tags {
    private VirtualInvokeReceiver() {}
  }

  public final class NewObject extends Tags {
    private NewObject() {}
  }

  public final class NewArray extends Tags {
    private NewArray() {}
  }

  // a condition expression that results in a control-flow change
  public final class ControlFlowCondition extends Tags {
    private ControlFlowCondition() {}
  }

  public final class FieldRead extends Tags {
    private FieldRead() {}
  }

  public final class FieldWrite extends Tags {
    private FieldWrite() {}
  }

  // lexical access/reference to a class
  public final class ClassRead extends Tags {
    private ClassRead() {}
  }

  public final class LocalVarRead extends Tags {
    private LocalVarRead() {}
  }

  public final class LocalVarWrite extends Tags {
    private LocalVarWrite() {}
  }

  public final class LocalArgRead extends Tags {
    private LocalArgRead() {}
  }

  public final class ArrayRead extends Tags {
    private ArrayRead() {}
  }

  public final class ArrayWrite extends Tags {
    private ArrayWrite() {}
  }

  public final class LoopNode extends Tags {
    private LoopNode() {}
  }

  public final class LoopBody extends Tags {
    private LoopBody() {}
  }

  public final class BasicPrimitiveOperation extends Tags {
    private BasicPrimitiveOperation() {}
  }

  public final class ComplexPrimitiveOperation extends Tags {
    private ComplexPrimitiveOperation() {}
  }

  public final class PrimitiveArgument extends Tags {
    private PrimitiveArgument() {}
  }

  // some operation that somehow accesses a string
  public final class StringAccess extends Tags {
    private StringAccess() {}
  }

  public final class OpClosureApplication extends Tags {
    private OpClosureApplication() {}
  }

  public final class OpArithmetic extends Tags {
    private OpArithmetic() {}
  }

  public final class OpComparison extends Tags {
    private OpComparison() {}
  }

  public final class OpLength extends Tags {
    private OpLength() {}
  }
}
