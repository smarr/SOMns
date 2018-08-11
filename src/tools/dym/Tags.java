package tools.dym;

import com.oracle.truffle.api.instrumentation.Tag;


public abstract class Tags {
  private Tags() {}

  // this is some form of invoke in the source, unclear what it is during program execution
  public final class UnspecifiedInvoke extends Tag {
    private UnspecifiedInvoke() {}
  }

  // a virtual invoke where the lookup was cached
  public final class CachedVirtualInvoke extends Tag {
    private CachedVirtualInvoke() {}
  }

  // a closure invoke where the closure method was cached
  public final class CachedClosureInvoke extends Tag {
    private CachedClosureInvoke() {}
  }

  // the lexical site of a virtual invoke
  public final class VirtualInvoke extends Tag {
    private VirtualInvoke() {}
  }

  // the lexical site of a virtual invoke
  public final class VirtualInvokeReceiver extends Tag {
    private VirtualInvokeReceiver() {}
  }

  public final class NewObject extends Tag {
    private NewObject() {}
  }

  public final class NewArray extends Tag {
    private NewArray() {}
  }

  // a condition expression that results in a control-flow change
  public final class ControlFlowCondition extends Tag {
    private ControlFlowCondition() {}
  }

  public final class FieldRead extends Tag {
    private FieldRead() {}
  }

  public final class FieldWrite extends Tag {
    private FieldWrite() {}
  }

  // lexical access/reference to a class
  public final class ClassRead extends Tag {
    private ClassRead() {}
  }

  public final class LocalVarRead extends Tag {
    private LocalVarRead() {}
  }

  public final class LocalVarWrite extends Tag {
    private LocalVarWrite() {}
  }

  public final class LocalArgRead extends Tag {
    private LocalArgRead() {}
  }

  public final class ArrayRead extends Tag {
    private ArrayRead() {}
  }

  public final class ArrayWrite extends Tag {
    private ArrayWrite() {}
  }

  public final class LoopNode extends Tag {
    private LoopNode() {}
  }

  public final class LoopBody extends Tag {
    private LoopBody() {}
  }

  public final class BasicPrimitiveOperation extends Tag {
    private BasicPrimitiveOperation() {}
  }

  public final class ComplexPrimitiveOperation extends Tag {
    private ComplexPrimitiveOperation() {}
  }

  /**
   * Tags expressions that are arguments to message sends.
   */
  public final class ArgumentExpr extends Tag {
    private ArgumentExpr() {}
  }

  // some operation that somehow accesses a string
  public final class StringAccess extends Tag {
    private StringAccess() {}
  }

  public final class OpClosureApplication extends Tag {
    private OpClosureApplication() {}
  }

  public final class OpArithmetic extends Tag {
    private OpArithmetic() {}
  }

  public final class OpComparison extends Tag {
    private OpComparison() {}
  }

  public final class OpLength extends Tag {
    private OpLength() {}
  }

  public final class AnyNode extends Tag {
    private AnyNode() {}
  }
}
