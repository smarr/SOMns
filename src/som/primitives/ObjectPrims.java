package som.primitives;

import java.math.BigInteger;

import com.oracle.truffle.api.debug.DebuggerTags.AlwaysHalt;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.instrumentation.Instrumentable;
import com.oracle.truffle.api.source.SourceSection;

import som.VM;
import som.interpreter.Types;
import som.interpreter.actors.SFarReference;
import som.interpreter.actors.SPromise;
import som.interpreter.actors.SPromise.SResolver;
import som.interpreter.nodes.OperationNode;
import som.interpreter.nodes.nary.UnaryBasicOperation;
import som.interpreter.nodes.nary.UnaryExpressionNode;
import som.primitives.ObjectPrimsFactory.IsValueFactory;
import som.vm.constants.Nil;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SArray.SImmutableArray;
import som.vmobjects.SArray.SMutableArray;
import som.vmobjects.SBlock;
import som.vmobjects.SClass;
import som.vmobjects.SObject.SImmutableObject;
import som.vmobjects.SObject.SMutableObject;
import som.vmobjects.SObjectWithClass.SObjectWithoutFields;
import som.vmobjects.SSymbol;
import tools.dym.Tags.OpComparison;


public final class ObjectPrims {

  @GenerateNodeFactory
  @Primitive(primitive = "objClassName:")
  public abstract static class ObjectClassNamePrim extends UnaryExpressionNode {
    public ObjectClassNamePrim(final boolean eagWrap, final SourceSection source) {
      super(eagWrap, source);
    }

    @Specialization
    public final SSymbol getName(final Object obj) {
      VM.thisMethodNeedsToBeOptimized(
          "Not yet optimized, need add specializations to remove Types.getClassOf");
      return Types.getClassOf(obj).getName();
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "halt:")
  public abstract static class HaltPrim extends UnaryExpressionNode {
    public HaltPrim(final boolean eagWrap, final SourceSection source) {
      super(eagWrap, source);
    }

    @Specialization
    public final Object doSAbstractObject(final Object receiver) {
      VM.errorPrintln("BREAKPOINT");
      return receiver;
    }

    @Override
    protected boolean isTaggedWithIgnoringEagerness(final Class<?> tag) {
      if (tag == AlwaysHalt.class) {
        return true;
      }
      return super.isTaggedWithIgnoringEagerness(tag);
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "objClass:")
  public abstract static class ClassPrim extends UnaryExpressionNode {
    public ClassPrim(final boolean eagWrap, final SourceSection source) {
      super(eagWrap, source);
    }

    @Specialization
    public final SClass doSAbstractObject(final SAbstractObject receiver) {
      return receiver.getSOMClass();
    }

    @Specialization
    public final SClass doObject(final Object receiver) {
      VM.thisMethodNeedsToBeOptimized("Should specialize this if performance critical");
      return Types.getClassOf(receiver);
    }
  }

  @GenerateNodeFactory
  @Primitive(selector = "isNil", noWrapper = true)
  public abstract static class IsNilNode extends UnaryBasicOperation implements OperationNode {
    public IsNilNode(final boolean eagWrap, final SourceSection source) {
      super(eagWrap, source);
    }

    @Override
    protected boolean isTaggedWithIgnoringEagerness(final Class<?> tag) {
      if (tag == OpComparison.class) {
        return true;
      } else {
        return super.isTaggedWithIgnoringEagerness(tag);
      }
    }

    @Specialization
    public final boolean isNil(final Object receiver) {
      return receiver == Nil.nilObject;
    }

    @Override
    public String getOperation() {
      return "isNil";
    }

    @Override
    public int getNumArguments() {
      return 1;
    }
  }

  @GenerateNodeFactory
  @Primitive(selector = "notNil", noWrapper = true)
  public abstract static class NotNilNode extends UnaryBasicOperation
      implements OperationNode {
    public NotNilNode(final boolean eagWrap, final SourceSection source) {
      super(eagWrap, source);
    }

    @Override
    protected boolean isTaggedWithIgnoringEagerness(final Class<?> tag) {
      if (tag == OpComparison.class) {
        return true;
      } else {
        return super.isTaggedWithIgnoringEagerness(tag);
      }
    }

    @Specialization
    public final boolean isNotNil(final Object receiver) {
      return receiver != Nil.nilObject;
    }

    @Override
    public String getOperation() {
      return "notNil";
    }

    @Override
    public int getNumArguments() {
      return 1;
    }
  }

  /**
   * A node that checks whether a given object is a Value.
   */
  @GenerateNodeFactory
  @Primitive(primitive = "objIsValue:")
  @ImportStatic(Nil.class)
  @Instrumentable(factory = IsValueWrapper.class)
  public abstract static class IsValue extends UnaryExpressionNode {
    public IsValue(final boolean eagWrap, final SourceSection source) {
      super(eagWrap, source);
    }

    public IsValue(final IsValue node) {
      super(node);
    }

    public abstract boolean executeEvaluated(Object rcvr);

    public static IsValue createSubNode() {
      return IsValueFactory.create(false, null, null);
    }

    @Specialization
    public final boolean isValue(final boolean rcvr) {
      return true;
    }

    @Specialization
    public final boolean isValue(final long rcvr) {
      return true;
    }

    @Specialization
    public final boolean isValue(final BigInteger rcvr) {
      return true;
    }

    @Specialization
    public final boolean isValue(final double rcvr) {
      return true;
    }

    @Specialization
    public final boolean isValue(final String rcvr) {
      return true;
    }

    @Specialization
    public final boolean isValue(final SSymbol rcvr) {
      return true;
    }

    @Specialization
    public final boolean isValue(final SMutableArray rcvr) {
      return false;
    }

    @Specialization
    public final boolean isValue(final SImmutableArray rcvr) {
      return true;
    }

    @Specialization
    public final boolean isValue(final SFarReference rcvr) {
      return true;
    }

    @Specialization(guards = "valueIsNil(rcvr)")
    public final boolean nilIsValue(final Object rcvr) {
      return true;
    }

    @Specialization
    public final boolean isValue(final SClass rcvr,
        @Cached("createSubNode()") final IsValue enclosingObj) {
      return enclosingObj.executeEvaluated(rcvr.getEnclosingObject());
    }

    @Specialization
    public final boolean isValue(final SMutableObject rcvr) {
      return false;
    }

    @Specialization
    public final boolean isValue(final SImmutableObject rcvr) {
      return rcvr.isValue();
    }

    @Specialization
    public final boolean isValue(final SPromise rcvr) {
      return false;
    }

    @Specialization
    public final boolean isValue(final SResolver rcvr) {
      return true;
    }

    @Specialization
    public final boolean isValue(final SObjectWithoutFields rcvr) {
      return rcvr.getSOMClass().declaredAsValue();
    }

    @Specialization
    public final boolean isValue(final SBlock rcvr) {
      return false;
    }

    public static boolean isObjectValue(final Object obj) {
      VM.callerNeedsToBeOptimized(
          "This should only be used for prototyping, and then removed, because it is slow and duplicates code");
      if (obj instanceof Boolean ||
          obj instanceof Long ||
          obj instanceof BigInteger ||
          obj instanceof Double ||
          obj instanceof String) {
        return true;
      }

      if (Nil.valueIsNil(obj)) {
        return true;
      }

      return ((SAbstractObject) obj).isValue();
    }
  }
}
