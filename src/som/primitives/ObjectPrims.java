package som.primitives;

import java.math.BigInteger;

import com.oracle.truffle.api.debug.DebuggerTags.AlwaysHalt;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.GenerateWrapper;
import com.oracle.truffle.api.instrumentation.ProbeNode;
import com.oracle.truffle.api.instrumentation.Tag;

import bd.primitives.Primitive;
import bd.tools.nodes.Operation;
import som.Output;
import som.VM;
import som.interpreter.Types;
import som.interpreter.actors.SFarReference;
import som.interpreter.actors.SPromise;
import som.interpreter.actors.SPromise.SResolver;
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
    @Specialization
    public final Object doSAbstractObject(final Object receiver) {
      Output.errorPrintln("BREAKPOINT");
      return receiver;
    }

    @Override
    protected boolean hasTagIgnoringEagerness(final Class<? extends Tag> tag) {
      if (tag == AlwaysHalt.class) {
        return true;
      }
      return super.hasTagIgnoringEagerness(tag);
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "objClass:")
  public abstract static class ClassPrim extends UnaryExpressionNode {
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
  public abstract static class IsNilNode extends UnaryBasicOperation implements Operation {
    @Override
    protected boolean hasTagIgnoringEagerness(final Class<? extends Tag> tag) {
      if (tag == OpComparison.class) {
        return true;
      } else {
        return super.hasTagIgnoringEagerness(tag);
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
      implements Operation {
    @Override
    protected boolean hasTagIgnoringEagerness(final Class<? extends Tag> tag) {
      if (tag == OpComparison.class) {
        return true;
      } else {
        return super.hasTagIgnoringEagerness(tag);
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
  @GenerateWrapper
  public abstract static class IsValue extends UnaryExpressionNode {
    protected IsValue() {}

    protected IsValue(final IsValue node) {}

    @Override
    public abstract Object executeEvaluated(VirtualFrame frame, Object rcvr);

    public abstract boolean executeBoolean(VirtualFrame frame, Object rcvr);

    @Override
    public WrapperNode createWrapper(final ProbeNode probe) {
      return new IsValueWrapper(this, probe);
    }

    public static IsValue createSubNode() {
      return IsValueFactory.create(null);
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
    public final boolean isValue(final VirtualFrame frame, final SClass rcvr,
        @Cached("createSubNode()") final IsValue enclosingObj) {
      return enclosingObj.executeBoolean(frame, rcvr.getEnclosingObject());
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
