package som.interpreter.nodes;

import som.compiler.ClassBuilder.ClassDefinitionId;
import som.vm.constants.KernelObj;
import som.vmobjects.SClass;
import som.vmobjects.SObjectWithoutFields;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.utilities.ValueProfile;

@NodeChild(value = "receiver", type = ExpressionNode.class)
public abstract class OuterObjectRead
    extends ExpressionNode implements ISpecialSend {

  private final int contextLevel;
  private final ClassDefinitionId classDefId;
  private final ClassDefinitionId enclosingLexicalClassId;

  private final ValueProfile enclosingObj;

  public OuterObjectRead(final int contextLevel,
      final ClassDefinitionId classDefId,
      final ClassDefinitionId enclosingLexicalClassId,
      final SourceSection sourceSection) {
    super(sourceSection);
    this.contextLevel = contextLevel;
    this.classDefId = classDefId;
    this.enclosingLexicalClassId = enclosingLexicalClassId;
    this.enclosingObj = ValueProfile.createIdentityProfile();
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
  public Object doSObject(final SObjectWithoutFields receiver) {
    return getEnclosingObject(receiver);
  }

  public abstract Object executeEvaluated(Object receiver);

  protected static final boolean notSObjectWithoutFields(final Object receiver) {
    return !(receiver instanceof SObjectWithoutFields);
  }

  @Specialization(guards = "notSObjectWithoutFields(receiver)")
  public Object doObject(final Object receiver) {
    return KernelObj.kernel;
  }

  @ExplodeLoop
  private Object getEnclosingObject(final SObjectWithoutFields receiver) {
    if (contextLevel == 0) {
      return enclosingObj.profile(receiver);
    }

    SClass cls = receiver.getSOMClass().getClassCorrespondingTo(classDefId);
    int ctxLevel = contextLevel - 1;
    SObjectWithoutFields enclosing = cls.getEnclosingObject();

    while (ctxLevel > 0) {
      ctxLevel--;
      enclosing = enclosing.getSOMClass().getEnclosingObject();
    }
    return enclosingObj.profile(enclosing);
  }
}
