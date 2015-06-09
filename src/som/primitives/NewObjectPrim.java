package som.primitives;

import som.compiler.ClassBuilder.ClassDefinitionId;
import som.interpreter.nodes.ISpecialSend;
import som.interpreter.nodes.nary.UnaryExpressionNode;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SClass;
import som.vmobjects.SObject;
import som.vmobjects.SObjectWithoutFields;

import com.oracle.truffle.api.dsl.Specialization;


// This isn't a primitive anymore, because we do all this in the magic of the
//  primary factory methods, which are generated in assemblePrimaryFactoryMethod()
// TODO: figure out where we need to do that, or whether we could actually do it
// in the language
// @GenerateNodeFactory
//    @Primitive("instantiate:")
public abstract class NewObjectPrim extends UnaryExpressionNode implements ISpecialSend {
  private final ClassDefinitionId classId;

  public NewObjectPrim(final ClassDefinitionId classId) {
    this.classId = classId;
  }

  @Override
  public ClassDefinitionId getLexicalClass() {
    return classId;
  }

  @Override
  public boolean isSuperSend() { return false; }

  @Specialization(guards = "receiver.hasFields()")
  public final SAbstractObject doClassWithFields(final SClass receiver) {
    return new SObject(receiver);
  }

  @Specialization(guards = "!receiver.hasFields()")
  public final SAbstractObject doClassWithoutFields(final SClass receiver) {
    return new SObjectWithoutFields(receiver);
  }
}
