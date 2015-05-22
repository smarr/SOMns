package som.interpreter.nodes;

import som.vm.constants.KernelObj;
import som.vmobjects.SAbstractObject;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.source.SourceSection;

@NodeChild(value = "receiver", type = ExpressionNode.class)
public abstract class OuterObjectRead extends ExpressionNode {

  private final int contextLevel;

  public OuterObjectRead(final int contextLevel, final SourceSection sourceSection) {
    super(sourceSection);
    this.contextLevel = contextLevel;
  }

  @Specialization
  public Object doSAbstractObject(final SAbstractObject receiver) {
    return getEnclosingObject(receiver);
  }

  public abstract Object executeEvaluated(Object receiver);

  protected static final boolean notSAbstractObject(final Object receiver) {
    return !(receiver instanceof SAbstractObject);
  }

  @Specialization(guards = "notSAbstractObject(receiver)")
  public Object doObject(final Object receiver) {
    return KernelObj.kernel;
  }

  @ExplodeLoop
  private Object getEnclosingObject(final SAbstractObject receiver) {
    int ctxLevel = contextLevel;
    SAbstractObject enclosing = receiver;
    while (ctxLevel > 0) {
      ctxLevel--;
      enclosing = enclosing.getEnclosingObject();
    }
    return enclosing;
  }

}
