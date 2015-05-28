package som.interpreter.nodes;

import som.compiler.ClassBuilder.ClassDefinitionId;
import som.vm.constants.KernelObj;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SClass;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.source.SourceSection;

@NodeChild(value = "receiver", type = ExpressionNode.class)
public abstract class OuterObjectRead
    extends ExpressionNode implements ISpecialSend {

  private final int contextLevel;
  private final ClassDefinitionId classDefId;
  private final ClassDefinitionId enclosingLexicalClassId;

  public OuterObjectRead(final int contextLevel,
      final ClassDefinitionId classDefId,
      final ClassDefinitionId enclosingLexicalClassId,
      final SourceSection sourceSection) {
    super(sourceSection);
    this.contextLevel = contextLevel;
    this.classDefId = classDefId;
    this.enclosingLexicalClassId = enclosingLexicalClassId;
  }

  public ClassDefinitionId getClassId() {
    return classDefId;
  }

  @Override
  public ClassDefinitionId getLexicalClass() {
    return enclosingLexicalClassId;
  }

  @Override
  public boolean isSuperSend() { return false; }

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
    if (contextLevel == 0) {
      return receiver;
    }

    SClass cls = receiver.getSOMClass().getClassCorrespondingTo(classDefId);
    int ctxLevel = contextLevel - 1;
    SAbstractObject enclosing = cls.getEnclosingObject();

    while (ctxLevel > 0) {
      ctxLevel--;
      enclosing = enclosing.getSOMClass().getEnclosingObject();
    }
    return enclosing;
  }
}
