package som.primitives;

import java.math.BigInteger;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.frame.FrameInstance.FrameAccess;
import com.oracle.truffle.api.frame.FrameInstanceVisitor;
import com.oracle.truffle.api.instrumentation.Instrumentable;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.SourceSection;

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
  @Primitive("objClassName:")
  public abstract static class ObjectClassNamePrim extends UnaryExpressionNode {
    public ObjectClassNamePrim(final SourceSection source) { super(false, source); }

    @Specialization
    public final SSymbol getName(final Object obj) {
      VM.thisMethodNeedsToBeOptimized("Not yet optimized, need add specializations to remove Types.getClassOf");
      return Types.getClassOf(obj).getName();
    }
  }

  @GenerateNodeFactory
  @Primitive("halt:")
  public abstract static class HaltPrim extends UnaryExpressionNode {
    public HaltPrim(final SourceSection source) { super(false, source); }

    @Specialization
    public final Object doSAbstractObject(final Object receiver) {
      VM.errorPrintln("BREAKPOINT");
      reportBreakpoint();
      return receiver;
    }

    private static void reportBreakpoint() {
      Node[] callNode = new Node[1];
      Frame[] frame = new Frame[1];

      Truffle.getRuntime().iterateFrames(new FrameInstanceVisitor<FrameInstance>() {
        int stackIndex = 0;

        @Override
        public FrameInstance visitFrame(final FrameInstance frameInstance) {
          if (stackIndex == 2) {
            callNode[0] = frameInstance.getCallNode();
            frame[0]    = frameInstance.getFrame(FrameAccess.READ_ONLY, true);
            return frameInstance;
          }
          stackIndex += 1;
          return null;
        }
      });

      VM.getWebDebugger().suspendExecution(callNode[0], frame[0].materialize());
    }
  }

  @GenerateNodeFactory
  @Primitive("objClass:")
  public abstract static class ClassPrim extends UnaryExpressionNode {
    public ClassPrim(final SourceSection source) { super(false, source); }

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

  public abstract static class IsNilNode extends UnaryBasicOperation {
    public IsNilNode(final SourceSection source) { super(false, source); }

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
  }

  public abstract static class NotNilNode extends UnaryBasicOperation {
    public NotNilNode(final SourceSection source) { super(false, source); }

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
  }

  /**
   * A node that checks whether a given object is a Value.
   */
  @GenerateNodeFactory
  @Primitive("objIsValue:")
  @ImportStatic(Nil.class)
  @Instrumentable(factory = IsValueWrapper.class)
  public abstract static class IsValue extends UnaryExpressionNode {
    public IsValue(final SourceSection source) { super(false, source); }

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
