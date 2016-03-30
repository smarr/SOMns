package som.primitives;

import java.math.BigInteger;

import som.VM;
import som.interpreter.Types;
import som.interpreter.actors.SFarReference;
import som.interpreter.actors.SPromise;
import som.interpreter.actors.SPromise.SResolver;
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

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.source.SourceSection;


public final class ObjectPrims {

  @GenerateNodeFactory
  @Primitive("objClassName:")
  public abstract static class ObjectClassNamePrim extends UnaryExpressionNode {
    public ObjectClassNamePrim(final SourceSection source) { super(source); }

    @Specialization
    public final SSymbol getName(final Object obj) {
      VM.thisMethodNeedsToBeOptimized("Not yet optimized, need add specializations to remove Types.getClassOf");
      return Types.getClassOf(obj).getName();
    }
  }

  @GenerateNodeFactory
  @Primitive("halt:")
  public abstract static class HaltPrim extends UnaryExpressionNode {
    public HaltPrim(final SourceSection source) { super(source); }

    @Specialization
    public final Object doSAbstractObject(final Object receiver) {
      VM.errorPrintln("BREAKPOINT");
      return receiver;
    }
  }

  @GenerateNodeFactory
  @Primitive("objClass:")
  public abstract static class ClassPrim extends UnaryExpressionNode {
    public ClassPrim(final SourceSection source) { super(source); }

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

  public abstract static class IsNilNode extends UnaryExpressionNode {
    public IsNilNode(final SourceSection source) { super(source); }

    @Specialization
    public final boolean isNil(final Object receiver) {
      return receiver == Nil.nilObject;
    }
  }

  public abstract static class NotNilNode extends UnaryExpressionNode {
    public NotNilNode(final SourceSection source) { super(source); }

    @Specialization
    public final boolean isNotNil(final Object receiver) {
      return receiver != Nil.nilObject;
    }
  }

  /**
   * A node that checks whether a given object is a Value.
   */
  @GenerateNodeFactory
  @Primitive("objIsValue:")
  @ImportStatic(Nil.class)
  public abstract static class IsValue extends UnaryExpressionNode {
    public IsValue(final SourceSection source) { super(source); }

    public abstract boolean executeEvaluated(Object rcvr);

    public static IsValue createSubNode() {
      return IsValueFactory.create(null, null);
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
      VM.callerNeedsToBeOptimized("This should only be used for prototyping, and then removed, because it is slow and duplicates code");
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
