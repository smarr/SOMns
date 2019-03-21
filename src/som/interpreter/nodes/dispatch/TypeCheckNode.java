package som.interpreter.nodes.dispatch;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.SourceSection;

import som.interpreter.SomException;
import som.interpreter.Types;
import som.interpreter.nodes.ExceptionSignalingNode;
import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.nary.BinaryExpressionNode;
import som.vm.SomStructuralType;
import som.vm.Symbols;
import som.vm.VmSettings;
import som.vmobjects.SInvokable;
import som.vmobjects.SObjectWithClass;


@NodeChildren({
    @NodeChild(value = "receiver", type = ExpressionNode.class),
    @NodeChild(value = "argument", type = ExpressionNode.class)})
@GenerateNodeFactory
public abstract class TypeCheckNode extends BinaryExpressionNode {

  @Child ExceptionSignalingNode exception;

  public static TypeCheckNode create(final ExpressionNode type, final ExpressionNode expr,
      final SourceSection sourceSection) {
    return TypeCheckNodeFactory.create(sourceSection, type, expr);
  }

  protected TypeCheckNode(final SourceSection sourceSection) {
    assert VmSettings.USE_TYPE_CHECKING : "Trying to create a TypeCheckNode, while USE_TYPE_CHECKING is disabled";
    this.sourceSection = sourceSection;
  }

  protected void ensureExceptionNode() {
    if (exception == null) {
      CompilerDirectives.transferToInterpreter();
      // now we are outside of PE'ed code, and need to check again
      if (exception == null) {
        exception = insert(
            ExceptionSignalingNode.createNode(Symbols.symbolFor("TypeError"), sourceSection));
      }
    }
  }

  @Override
  @Specialization
  public Object executeEvaluated(final VirtualFrame frame, final Object receiver,
      final Object argument) {
    CallTarget target = null;

    SObjectWithClass expected = (SObjectWithClass) receiver;
    for (SInvokable invoke : expected.getSOMClass().getMethods()) {
      if (invoke.getSignature().getString().equals("checkOrError:")) {
        target = invoke.getCallTarget();
        break;
      }
    }

    if (target == null) {
      ensureExceptionNode();

      int line = sourceSection.getStartLine();
      int column = sourceSection.getStartColumn();
      String[] parts = sourceSection.getSource().getURI().getPath().split("/");
      String suffix = parts[parts.length - 1] + " [" + line + "," + column + "]";

      exception.signal(suffix + " " + expected + " is not a type");
      return null;
    }

    try {
      // TODO: this a performance issue
      Truffle.getRuntime().createDirectCallNode(target)
             .call(new Object[] {receiver, argument});
    } catch (SomException e) {
      ensureExceptionNode();

      // TODO: this a performance issue
      SomStructuralType type = Types.getClassOf(argument).getFactory().type;
      int line = sourceSection.getStartLine();
      int column = sourceSection.getStartColumn();

      // TODO: Get the real source of the type check
      String[] parts = sourceSection.getSource().getURI().getPath().split("/");
      String suffix = parts[parts.length - 1] + " [" + line + "," + column + "]";

      exception.signal(suffix + " " + argument + " is not a subtype of "
          + sourceSection.getCharacters() + ", because it has the type " + type);
      throw e;
    }
    return argument;
  }
}
