package som.interpreter.nodes.dispatch;

import java.util.HashMap;
import java.util.Map;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.SourceSection;

import som.Output;
import som.interpreter.SomException;
import som.interpreter.Types;
import som.interpreter.nodes.ExceptionSignalingNode;
import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.dispatch.TypeCheckNodeFactory.CustomTypeCheckNodeFactory;
import som.interpreter.nodes.dispatch.TypeCheckNodeFactory.PrimitiveTypeCheckNodeFactory;
import som.interpreter.nodes.dispatch.TypeCheckNodeFactory.UnresolvedTypeCheckNodeFactory;
import som.interpreter.nodes.nary.BinaryExpressionNode;
import som.interpreter.nodes.nary.UnaryExpressionNode;
import som.vm.Symbols;
import som.vm.VmSettings;
import som.vm.constants.Nil;
import som.vmobjects.SInvokable;
import som.vmobjects.SObjectWithClass;
import som.vmobjects.SType;


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

  private static final Map<Object, Map<Object, Boolean>> isSuperclassTable =
      VmSettings.USE_SUBTYPE_TABLE ? new HashMap<>() : null;

  public static ExpressionNode create(final ExpressionNode type, final ExpressionNode expr,
      final SourceSection sourceSection) {

    assert VmSettings.USE_TYPE_CHECKING : "Trying to create a TypeCheckNode, while USE_TYPE_CHECKING is disabled";
    if (VmSettings.USE_TYPE_CHECKING) {
      if (VmSettings.COLLECT_TYPE_STATS) {
        ++numTypeCheckLocations;
      }
      return UnresolvedTypeCheckNodeFactory.create(sourceSection, type, expr);
    }
    return expr;
  }

  protected static ExceptionSignalingNode createExceptionNode(final SourceSection ss) {
    CompilerDirectives.transferToInterpreter();
    return ExceptionSignalingNode.createNode(Symbols.symbolFor("TypeError"), ss);
  }

  private static void throwTypeError(final Object argument, final Object type,
      final Object expected, final SourceSection sourceSection,
      final ExceptionSignalingNode exception) {

    int line = sourceSection.getStartLine();
    int column = sourceSection.getStartColumn();
    String[] parts = sourceSection.getSource().getURI().getPath().split("/");
    String suffix = parts[parts.length - 1] + " [" + line + "," + column + "]";
    exception.signal(
        suffix + " " + argument + " is not a subtype of " + sourceSection.getCharacters()
            + ", because it has the type: \n" + type
            + "\n    when it was expected to have type: \n" + expected);
  }

  protected interface TypeCheckingNode<Expected> {
    default Object checkTable(final Map<Object, Boolean> isSub,
        final SObjectWithClass expected, final Object argument,
        final SourceSection sourceSection, final ExceptionSignalingNode exception) {

      SType argType = Types.getClassOf(argument).type;
      if (isSub.containsKey(argType)) {
        if (isSub.get(argType)) {
          return argument;
        } else {
          throwTypeError(argument, argType, expected, sourceSection, exception);
        }
      }
      return null;
    }
  }

  @GenerateNodeFactory
  public abstract static class UnresolvedTypeCheckNode extends BinaryExpressionNode {

    protected UnresolvedTypeCheckNode(final SourceSection sourceSection) {
      this.sourceSection = sourceSection;
    }

    @Specialization
    public Object executeEvaluated(final VirtualFrame frame, final SObjectWithClass expected,
        final Object argument) {
      Map<Object, Boolean> isSub = null;
      if (VmSettings.USE_SUBTYPE_TABLE) {
        isSub = isSuperclassTable.getOrDefault(expected, null);
        if (isSub == null) {
          isSub = new HashMap<>();
          isSuperclassTable.put(expected, isSub);
        }
      }

      ExpressionNode argumentExpr = null;
      for (Node node : this.getChildren()) {
        argumentExpr = (ExpressionNode) node;
      }

      if (expected instanceof SType) {
        PrimitiveTypeCheckNode node = PrimitiveTypeCheckNodeFactory.create((SType) expected,
            isSub, sourceSection, argumentExpr);
        replace(node);
        return node.executeEvaluated(frame, argument);
      }

      CallTarget target = null;
      for (SInvokable invoke : expected.getSOMClass().getMethods()) {
        if (invoke.getSignature().getString().equals("checkOrError:")) {
          target = invoke.getCallTarget();
          break;
        }
      }
      if (target == null) {
        CompilerDirectives.transferToInterpreter();
        ExceptionSignalingNode exception = insert(createExceptionNode(sourceSection));

        // TODO: Support this as yet another node
        // if (isSuper != null) {
        // isSuper.put(expected, false);
        // }
        int line = sourceSection.getStartLine();
        int column = sourceSection.getStartColumn();
        String[] parts = sourceSection.getSource().getURI().getPath().split("/");
        String suffix = parts[parts.length - 1] + " [" + line + "," + column + "]";
        exception.signal(suffix + " " + expected + " is not a type");
        return null;
      }

      CustomTypeCheckNode node =
          CustomTypeCheckNodeFactory.create(expected, target, isSub, sourceSection,
              argumentExpr);
      replace(node);
      return node.executeEvaluated(frame, argument);
    }
  }

  @GenerateNodeFactory
  public abstract static class PrimitiveTypeCheckNode extends UnaryExpressionNode
      implements TypeCheckingNode<SType> {

    protected final SType                expected;
    protected final Map<Object, Boolean> isSub;
    @Child ExceptionSignalingNode        exception;

    protected PrimitiveTypeCheckNode(final SType expected, final Map<Object, Boolean> isSub,
        final SourceSection sourceSection) {
      this.expected = expected;
      this.isSub = isSub;
      this.sourceSection = sourceSection;
      this.exception = createExceptionNode(sourceSection);
    }

    @Specialization
    public Object executeEvaluated(final Object argument) {
      if (VmSettings.COLLECT_TYPE_STATS) {
        ++numSubclassChecks;
      }

      if (VmSettings.USE_SUBTYPE_TABLE) {
        Object result = checkTable(isSub, expected, argument, sourceSection, exception);
        if (result != null) {
          return result;
        }
      }

      if (VmSettings.COLLECT_TYPE_STATS) {
        ++numTypeCheckExecutions;
      }

      SType type = Types.getClassOf(argument).type;
      boolean result;
      if (argument == Nil.nilObject) {
        // Force nil object to subtype
        result = true;
      } else {
        result = expected.isSuperTypeOf(type);
      }

      if (isSub != null) {
        isSub.put(Types.getClassOf(argument).type, result);
      }
      if (!result) {
        throwTypeError(argument, type, expected, sourceSection, exception);
      }
      return argument;
    }
  }

  @GenerateNodeFactory
  public abstract static class CustomTypeCheckNode extends UnaryExpressionNode
      implements TypeCheckingNode<SObjectWithClass> {

    protected final SObjectWithClass     expected;
    protected final CallTarget           target;
    protected final Map<Object, Boolean> isSub;

    @Child ExceptionSignalingNode exception;

    protected CustomTypeCheckNode(final SObjectWithClass expected, final CallTarget target,
        final Map<Object, Boolean> isSub, final SourceSection sourceSection) {
      this.expected = expected;
      this.target = target;
      this.isSub = isSub;
      this.sourceSection = sourceSection;
      this.exception = createExceptionNode(sourceSection);
    }

    @Specialization
    public Object executeEvaluated(final Object argument) {
      if (VmSettings.COLLECT_TYPE_STATS) {
        ++numSubclassChecks;
      }

      if (VmSettings.USE_SUBTYPE_TABLE) {
        Object result = checkTable(isSub, expected, argument, sourceSection, exception);
        if (result != null) {
          return result;
        }
      }

      if (VmSettings.COLLECT_TYPE_STATS) {
        ++numTypeCheckExecutions;
      }

      try {
        Truffle.getRuntime().createDirectCallNode(target)
               .call(new Object[] {expected, argument});
        if (isSub != null) {
          isSub.put(Types.getClassOf(argument).type, true);
        }
      } catch (SomException e) {
        SType argType = Types.getClassOf(argument).type;
        if (isSub != null) {
          isSub.put(argType, false);
        }
        throwTypeError(argument, argType, expected, sourceSection, exception);
        throw e;
      }
      return argument;
    }
  }
}
