package som.interpreter.nodes.dispatch;

import java.util.HashMap;
import java.util.Map;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.SourceSection;

import som.Output;
import som.interpreter.SomException;
import som.interpreter.Types;
import som.interpreter.nodes.ExceptionSignalingNode;
import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.nary.BinaryExpressionNode;
import som.vm.Symbols;
import som.vm.VmSettings;
import som.vmobjects.SInvokable;
import som.vmobjects.SObjectWithClass;


@NodeChildren({
    @NodeChild(value = "receiver", type = ExpressionNode.class),
    @NodeChild(value = "argument", type = ExpressionNode.class)})
@GenerateNodeFactory
public abstract class TypeCheckNode extends BinaryExpressionNode {
  public static long numTypeCheckExecutions;
  public static long numSubclassChecks;
  public static int  numTypeCheckLocations;
  public static int  nTypes;

  public static void reportStats() {
    if (!VmSettings.COLLECT_TYPE_STATS) {
      return;
    }
    Output.println("RESULT-NumberOfTypeCheckExecutions: " + numTypeCheckExecutions);
    Output.println("RESULT-NumberOfSubclassChecks: " + numSubclassChecks);
    Output.println("RESULT-NumberOfTypes: " + nTypes);
  }

  @Child ExceptionSignalingNode exception;

  private static final Map<Object, Map<Object, Boolean>> isSubclassTable =
      VmSettings.USE_SUBTYPE_TABLE ? new HashMap<>() : null;

  public static ExpressionNode create(final ExpressionNode type, final ExpressionNode expr,
      final SourceSection sourceSection) {
    if (VmSettings.USE_TYPE_CHECKING) {
      if (VmSettings.COLLECT_TYPE_STATS) {
        ++numTypeCheckLocations;
      }
      return TypeCheckNodeFactory.create(sourceSection, type, expr);
    }
    return expr;
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
    SObjectWithClass expected = (SObjectWithClass) receiver;

    if (VmSettings.COLLECT_TYPE_STATS) {
      ++numSubclassChecks;
    }

    if (!VmSettings.USE_SUBTYPE_TABLE) {
      return check(expected, argument, null);
    }

    Object type = Types.getClassOf(argument).getFactory().type;
    Map<Object, Boolean> isSuper = isSubclassTable.getOrDefault(type, null);
    if (isSuper == null) {
      isSuper = new HashMap<>();
      isSubclassTable.put(type, isSuper);
    } else if (isSuper.containsKey(receiver)) {
      if (isSuper.get(receiver)) {
        return argument;
      } else {
        throwTypeError(argument, type);
      }
    }
    if (VmSettings.COLLECT_TYPE_STATS) {
      ++numTypeCheckExecutions;
    }
    return check(expected, argument, isSuper);
  }

  protected final Object check(final SObjectWithClass expected, final Object argument,
      final Map<Object, Boolean> isSuper) {
    CallTarget target = null;

    for (SInvokable invoke : expected.getSOMClass().getMethods()) {
      if (invoke.getSignature().getString().equals("checkOrError:")) {
        target = invoke.getCallTarget();
        break;
      }
    }

    if (target == null) {
      ensureExceptionNode();

      if (isSuper != null) {
        isSuper.put(expected, false);
      }
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
             .call(new Object[] {expected, argument});
      if (isSuper != null) {
        isSuper.put(expected, true);
      }
    } catch (SomException e) {
      if (isSuper != null) {
        isSuper.put(expected, false);
      }
      throwTypeError(argument, Types.getClassOf(argument).getFactory().type);
      throw e;
    }
    return argument;
  }

  protected final void throwTypeError(final Object argument, final Object type) {
    ensureExceptionNode();

    int line = sourceSection.getStartLine();
    int column = sourceSection.getStartColumn();
    String[] parts = sourceSection.getSource().getURI().getPath().split("/");
    String suffix = parts[parts.length - 1] + " [" + line + "," + column + "]";
    exception.signal(
        suffix + " " + argument + " is not a subtype of " + sourceSection.getCharacters()
            + ", because it has the type " + type);
  }
}
