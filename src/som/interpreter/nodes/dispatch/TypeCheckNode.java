package som.interpreter.nodes.dispatch;

import java.util.HashMap;
import java.util.Map;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Cached;
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
import som.interpreter.nodes.dispatch.TypeCheckNodeFactory.BooleanTypeCheckNodeFactory;
import som.interpreter.nodes.dispatch.TypeCheckNodeFactory.CustomTypeCheckNodeFactory;
import som.interpreter.nodes.dispatch.TypeCheckNodeFactory.DoubleTypeCheckNodeFactory;
import som.interpreter.nodes.dispatch.TypeCheckNodeFactory.LongTypeCheckNodeFactory;
import som.interpreter.nodes.dispatch.TypeCheckNodeFactory.NonPrimitiveTypeCheckNodeFactory;
import som.interpreter.nodes.dispatch.TypeCheckNodeFactory.SArrayTypeCheckNodeFactory;
import som.interpreter.nodes.dispatch.TypeCheckNodeFactory.SBlockTypeCheckNodeFactory;
import som.interpreter.nodes.dispatch.TypeCheckNodeFactory.StringTypeCheckNodeFactory;
import som.interpreter.nodes.dispatch.TypeCheckNodeFactory.UnresolvedTypeCheckNodeFactory;
import som.interpreter.nodes.nary.BinaryExpressionNode;
import som.interpreter.nodes.nary.UnaryExpressionNode;
import som.vm.Symbols;
import som.vm.VmSettings;
import som.vm.constants.Classes;
import som.vm.constants.Nil;
import som.vmobjects.SArray;
import som.vmobjects.SBlock;
import som.vmobjects.SClass;
import som.vmobjects.SInvokable;
import som.vmobjects.SObjectWithClass;
import som.vmobjects.SObjectWithClass.SObjectWithoutFields;
import som.vmobjects.SType;
import som.vmobjects.SType.InterfaceType;


public abstract class TypeCheckNode extends BinaryExpressionNode {
  public static long numTypeCheckExecutions;
  public static long numSubclassChecks;
  public static int  numTypeCheckLocations;
  public static int  nTypes;

  public static final byte MISSING = 0;
  public static final byte SUBTYPE = 1;
  public static final byte FAIL    = 2;

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
  private static final byte[][]                          isSuperclassArray =
      VmSettings.USE_SUBTYPE_TABLE ? new byte[1000][1000] : null;

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

  public interface ATypeCheckNode {}

  protected static ExceptionSignalingNode createExceptionNode(final SourceSection ss) {
    CompilerDirectives.transferToInterpreter();
    return ExceptionSignalingNode.createNode(Symbols.symbolFor("TypeError"), ss);
  }

  private static void throwTypeError(final Object argument, final Object type,
      final Object expected, final SourceSection sourceSection,
      final ExceptionSignalingNode exception) {
    CompilerDirectives.transferToInterpreter();

    int line = sourceSection.getStartLine();
    int column = sourceSection.getStartColumn();
    String[] parts = sourceSection.getSource().getURI().getPath().split("/");
    String suffix = parts[parts.length - 1] + " [" + line + "," + column + "]";
    exception.signal(
        suffix + " \"" + argument + "\" is not a subtype of " + sourceSection.getCharacters()
            + ", because it has the type: \n" + type
            + "\n    when it was expected to have type: \n" + expected);
  }

  protected interface TypeCheckingNode<Expected> {
    default <E> E checkTable(final byte[] isSub,
        final SObjectWithClass expected, final E argument, final SType type,
        final SourceSection sourceSection, final ExceptionSignalingNode exception) {
      byte sub = isSub[type.id];
      if (sub == SUBTYPE) {
        return argument;
      } else if (sub == FAIL) {
        throwTypeError(argument, type, expected, sourceSection, exception);
      }
      return null;
    }

    default boolean isNil(final SObjectWithoutFields obj) {
      return obj == Nil.nilObject;
    }
  }

  @GenerateNodeFactory
  public abstract static class UnresolvedTypeCheckNode extends BinaryExpressionNode
      implements ATypeCheckNode {

    protected UnresolvedTypeCheckNode(final SourceSection sourceSection) {
      this.sourceSection = sourceSection;
    }

    @Specialization
    public Object executeEvaluated(final VirtualFrame frame, final SObjectWithClass expected,
        final Object argument) {
      ExpressionNode argumentExpr = null;
      for (Node node : this.getChildren()) {
        argumentExpr = (ExpressionNode) node;
      }

      if (expected instanceof SType) {
        byte[] isSub = null;
        if (VmSettings.USE_SUBTYPE_TABLE) {
          isSub = isSuperclassArray[((SType) expected).id];
        }

        if (expected instanceof InterfaceType
            && ((SType) expected).getSignatures().length == 0) {
          replace(argumentExpr);
          return argument;
        }

        PrimitiveTypeCheckNode node;
        SType type;
        if (argument instanceof Long) {
          node = LongTypeCheckNodeFactory.create((SType) expected,
              isSub, sourceSection, argumentExpr);
          type = Classes.integerClass.type;
        } else if (argument instanceof Boolean) {
          node = BooleanTypeCheckNodeFactory.create((SType) expected,
              isSub, sourceSection, argumentExpr);
          type = ((boolean) argument) ? Classes.trueClass.type : Classes.falseClass.type;
        } else if (argument instanceof Double) {
          node = DoubleTypeCheckNodeFactory.create((SType) expected,
              isSub, sourceSection, argumentExpr);
          type = Classes.doubleClass.type;
        } else if (argument instanceof String) {
          node = StringTypeCheckNodeFactory.create((SType) expected,
              isSub, sourceSection, argumentExpr);
          type = Classes.stringClass.type;
        } else if (argument instanceof SArray) {
          node = SArrayTypeCheckNodeFactory.create((SType) expected,
              isSub, sourceSection, argumentExpr);
          type = Classes.arrayClass.type;
        } else if (argument instanceof SBlock) {
          node = SBlockTypeCheckNodeFactory.create((SType) expected,
              isSub, sourceSection, argumentExpr);
          type = Classes.blockClass.type;
        } else {
          NonPrimitiveTypeCheckNode nonPrimNode =
              NonPrimitiveTypeCheckNodeFactory.create((SType) expected,
                  isSub, sourceSection, argumentExpr);
          replace(nonPrimNode);
          return nonPrimNode.check(argument, ((SObjectWithClass) argument).getSOMClass().type);
        }

        node.check(argument, type);
        replace(node);
        return argument;
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

      Map<Object, Boolean> isSub = null;
      if (VmSettings.USE_SUBTYPE_TABLE) {
        // Using HashMap so don't inline (don't need invalidate since the node will be
        // replaced)
        CompilerDirectives.transferToInterpreter();

        isSub = isSuperclassTable.getOrDefault(expected, null);
        if (isSub == null) {
          isSub = new HashMap<>();
          isSuperclassTable.put(expected, isSub);
        }
      }

      CustomTypeCheckNode node =
          CustomTypeCheckNodeFactory.create(expected, target, isSub, sourceSection,
              argumentExpr);
      replace(node);
      return node.executeEvaluated(frame, argument);
    }
  }

  public abstract static class PrimitiveTypeCheckNode extends UnaryExpressionNode
      implements TypeCheckingNode<SType>, ATypeCheckNode {

    protected final SType  expected;
    protected final byte[] isSub;

    @Child ExceptionSignalingNode exception;

    protected PrimitiveTypeCheckNode(final SType expected, final byte[] isSub,
        final SourceSection sourceSection) {
      this.expected = expected;
      this.isSub = isSub;
      this.sourceSection = sourceSection;
      this.exception = createExceptionNode(sourceSection);
    }

    public <E> E check(final E argument, final SType type) {
      if (VmSettings.COLLECT_TYPE_STATS) {
        ++numSubclassChecks;
      }

      if (VmSettings.USE_SUBTYPE_TABLE) {
        E result = checkTable(isSub, expected, argument, type, sourceSection, exception);
        if (result != null) {
          return result;
        }
      }

      if (VmSettings.COLLECT_TYPE_STATS) {
        ++numTypeCheckExecutions;
      }

      boolean result;
      if (argument == Nil.nilObject) {
        // Force nil object to subtype
        result = true;
      } else {
        result = expected.isSuperTypeOf(type, argument);
      }

      if (isSub != null) {
        isSub[type.id] = result ? SUBTYPE : FAIL;
      }
      if (!result) {
        throwTypeError(argument, type, expected, sourceSection, exception);
      }
      return argument;
    }
  }

  @GenerateNodeFactory
  public abstract static class NonPrimitiveTypeCheckNode extends UnaryExpressionNode
      implements ATypeCheckNode {

    protected final SType  expected;
    protected final byte[] isSub;

    @Child ExceptionSignalingNode exception;

    protected NonPrimitiveTypeCheckNode(final SType expected, final byte[] isSub,
        final SourceSection sourceSection) {
      this.expected = expected;
      this.isSub = isSub;
      this.sourceSection = sourceSection;
    }

    @Specialization
    public boolean checkBoolean(final boolean obj) {
      check(obj, obj ? Classes.trueClass.type : Classes.falseClass.type);
      replace(BooleanTypeCheckNodeFactory.create(expected, isSub, sourceSection,
          (ExpressionNode) this.getChildren().iterator().next()));
      return obj;
    }

    @Specialization
    public long checkLong(final long obj) {
      check(obj, Classes.integerClass.type);
      replace(LongTypeCheckNodeFactory.create(expected, isSub, sourceSection,
          (ExpressionNode) this.getChildren().iterator().next()));
      return obj;
    }

    @Specialization
    public double checkDouble(final double obj) {
      check(obj, Classes.doubleClass.type);
      replace(DoubleTypeCheckNodeFactory.create(expected, isSub, sourceSection,
          (ExpressionNode) this.getChildren().iterator().next()));
      return obj;
    }

    @Specialization
    public String checkString(final String obj) {
      check(obj, Classes.stringClass.type);
      replace(StringTypeCheckNodeFactory.create(expected, isSub, sourceSection,
          (ExpressionNode) this.getChildren().iterator().next()));
      return obj;
    }

    @Specialization
    public SArray checkSArray(final SArray obj) {
      check(obj, Classes.arrayClass.type);
      replace(SArrayTypeCheckNodeFactory.create(expected, isSub, sourceSection,
          (ExpressionNode) this.getChildren().iterator().next()));
      return obj;
    }

    @Specialization
    public SBlock checkSBlock(final SBlock obj) {
      check(obj, Classes.blockClass.type);
      replace(SBlockTypeCheckNodeFactory.create(expected, isSub, sourceSection,
          (ExpressionNode) this.getChildren().iterator().next()));
      return obj;
    }

    @Specialization(guards = "obj.getSOMClass() == clazz")
    public SObjectWithClass checkSObject(final SObjectWithClass obj,
        @Cached("obj.getSOMClass()") final SClass clazz,
        @Cached("check(obj, clazz.type)") final Object initialRcvrUnused) {
      return obj;
    }

    public <E> E check(final E argument, final SType type) {
      if (VmSettings.COLLECT_TYPE_STATS) {
        ++numSubclassChecks;
      }

      if (VmSettings.USE_SUBTYPE_TABLE) {
        byte sub = isSub[type.id];
        if (sub == SUBTYPE) {
          return argument;
        } else if (sub == FAIL) {
          throwTypeError(argument, type, expected, sourceSection, exception);
        }
      }

      if (VmSettings.COLLECT_TYPE_STATS) {
        ++numTypeCheckExecutions;
      }

      boolean result;
      if (argument == Nil.nilObject) {
        // Force nil object to subtype
        result = true;
      } else {
        result = expected.isSuperTypeOf(type, argument);
      }

      if (isSub != null) {
        isSub[type.id] = result ? SUBTYPE : FAIL;
      }
      if (!result) {
        throwTypeError(argument, type, expected, sourceSection, exception);
      }
      return argument;
    }
  }

  @GenerateNodeFactory
  public abstract static class LongTypeCheckNode extends PrimitiveTypeCheckNode {
    protected LongTypeCheckNode(final SType expected, final byte[] isSub,
        final SourceSection sourceSection) {
      super(expected, isSub, sourceSection);
    }

    @Specialization
    public long typeCheckLong(final long argument) {
      return argument;
    }

    @Specialization
    public Object typeCheckFail(final Object argument) {
      return super.check(argument, Types.getClassOf(argument).type);
    }
  }

  @GenerateNodeFactory
  public abstract static class BooleanTypeCheckNode extends PrimitiveTypeCheckNode {
    protected BooleanTypeCheckNode(final SType expected, final byte[] isSub,
        final SourceSection sourceSection) {
      super(expected, isSub, sourceSection);
    }

    @Specialization
    public boolean typeCheckLong(final boolean argument) {
      return argument;
    }

    @Specialization
    public Object typeCheckFail(final Object argument) {
      return super.check(argument, Types.getClassOf(argument).type);
    }
  }

  @GenerateNodeFactory
  public abstract static class DoubleTypeCheckNode extends PrimitiveTypeCheckNode {
    protected DoubleTypeCheckNode(final SType expected, final byte[] isSub,
        final SourceSection sourceSection) {
      super(expected, isSub, sourceSection);
    }

    @Specialization
    public double typeCheckLong(final double argument) {
      return argument;
    }

    @Specialization
    public Object typeCheckFail(final Object argument) {
      return super.check(argument, Types.getClassOf(argument).type);
    }
  }

  @GenerateNodeFactory
  public abstract static class StringTypeCheckNode extends PrimitiveTypeCheckNode {
    protected StringTypeCheckNode(final SType expected, final byte[] isSub,
        final SourceSection sourceSection) {
      super(expected, isSub, sourceSection);
    }

    @Specialization
    public String typeCheckLong(final String argument) {
      return argument;
    }

    @Specialization
    public Object typeCheckFail(final Object argument) {
      return super.check(argument, Types.getClassOf(argument).type);
    }
  }

  @GenerateNodeFactory
  public abstract static class SArrayTypeCheckNode extends PrimitiveTypeCheckNode {
    protected SArrayTypeCheckNode(final SType expected, final byte[] isSub,
        final SourceSection sourceSection) {
      super(expected, isSub, sourceSection);
    }

    @Specialization
    public SArray typeCheckLong(final SArray argument) {
      return argument;
    }

    @Specialization
    public Object typeCheckFail(final Object argument) {
      return super.check(argument, Types.getClassOf(argument).type);
    }
  }

  @GenerateNodeFactory
  public abstract static class SBlockTypeCheckNode extends PrimitiveTypeCheckNode {
    protected SBlockTypeCheckNode(final SType expected, final byte[] isSub,
        final SourceSection sourceSection) {
      super(expected, isSub, sourceSection);
    }

    @Specialization
    public SBlock typeCheckLong(final SBlock argument) {
      return argument;
    }

    @Specialization
    public Object typeCheckFail(final Object argument) {
      return super.check(argument, Types.getClassOf(argument).type);
    }
  }

  @GenerateNodeFactory
  public abstract static class CustomTypeCheckNode extends UnaryExpressionNode
      implements TypeCheckingNode<SObjectWithClass>, ATypeCheckNode {

    protected final SObjectWithClass     expected;
    protected final CallTarget           target;
    protected final Map<Object, Boolean> isSub;

    @Child ExceptionSignalingNode exception;

    protected CustomTypeCheckNode(final SObjectWithClass expected, final CallTarget target,
        final Object isSub_TRUFFLE_REGRESSION, final SourceSection sourceSection) {
      // Truffle is currently failing to generate correct code for this
      @SuppressWarnings("unchecked")
      Map<Object, Boolean> isSub = (Map<Object, Boolean>) isSub_TRUFFLE_REGRESSION;
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
        CompilerDirectives.transferToInterpreterAndInvalidate();
        Object result = null;
        SType argType = Types.getClassOf(argument).type;
        if (isSub.containsKey(argType)) {
          if (isSub.get(argType)) {
            result = argument;
          } else {
            throwTypeError(argument, argType, expected, sourceSection, exception);
          }
        }
        if (result != null) {
          return result;
        }
      }

      if (VmSettings.COLLECT_TYPE_STATS) {
        ++numTypeCheckExecutions;
      }

      try {
        CompilerDirectives.transferToInterpreterAndInvalidate();
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
