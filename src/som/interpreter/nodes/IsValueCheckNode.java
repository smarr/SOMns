package som.interpreter.nodes;

import java.util.ArrayList;

import org.graalvm.collections.EconomicMap;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.ExplodeLoop.LoopExplosionKind;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.SourceSection;

import som.VM;
import som.compiler.MixinDefinition.SlotDefinition;
import som.interpreter.TruffleCompiler;
import som.interpreter.nodes.IsValueCheckNodeFactory.ValueCheckNodeGen;
import som.interpreter.nodes.nary.UnaryExpressionNode;
import som.interpreter.objectstorage.ObjectLayout;
import som.interpreter.objectstorage.StorageAccessor.AbstractObjectAccessor;
import som.interpreter.objectstorage.StorageLocation;
import som.primitives.ObjectPrims.IsValue;
import som.vmobjects.SObject.SImmutableObject;


/**
 * This node is used for the instantiation of objects to check whether all
 * fields have been initialized to values, in case, the object is declared
 * as a Value.
 */
public abstract class IsValueCheckNode extends UnaryExpressionNode {

  public static IsValueCheckNode create(final SourceSection source,
      final ExpressionNode self) {
    return new UninitializedNode(self).initialize(source);
  }

  private static final class UninitializedNode extends IsValueCheckNode {
    @Child protected ExpressionNode self;

    UninitializedNode(final ExpressionNode self) {
      this.self = self;
    }

    @Override
    public Object executeGeneric(final VirtualFrame frame) {
      TruffleCompiler.transferToInterpreterAndInvalidate("Need to specialize node");
      return executeEvaluated(frame, self.executeGeneric(frame));
    }

    @Override
    public Object executeEvaluated(final VirtualFrame frame, final Object receiver) {
      CompilerDirectives.transferToInterpreterAndInvalidate();
      return specialize(frame, receiver);
    }

    private Object specialize(final VirtualFrame frame, final Object receiver) {
      if (!(receiver instanceof SImmutableObject)) {
        // can remove ourselves, this node is only used in initializers,
        // which are by definition monomorphic
        dropCheckNode();
        return receiver;
      }

      SImmutableObject rcvr = (SImmutableObject) receiver;

      if (rcvr.isValue()) {
        ValueCheckNode node = ValueCheckNodeGen.create(self).initialize(sourceSection);
        return replace(node).executeEvaluated(frame, receiver);
      } else {
        // neither transfer object nor value, so nothing to check
        dropCheckNode();
        return receiver;
      }
    }

    private void dropCheckNode() {
      if (getParent() instanceof WrapperNode) {
        // in case there is a wrapper, get rid of it first
        Node wrapper = getParent();
        wrapper.replace(self);
        notifyInserted(self);
      } else {
        replace(self);
      }
    }
  }

  protected abstract static class ValueCheckNode extends IsValueCheckNode {

    @Child protected ExceptionSignalingNode notAValue;

    @Override
    @SuppressWarnings("unchecked")
    public ValueCheckNode initialize(final SourceSection sourceSection) {
      super.initialize(sourceSection);
      notAValue = insert(ExceptionSignalingNode.createNotAValueNode(sourceSection));
      return this;
    }

    protected FieldTester createTester(final ObjectLayout objectLayout) {
      ArrayList<AbstractObjectAccessor> accessors = new ArrayList<>();

      EconomicMap<SlotDefinition, StorageLocation> fields = objectLayout.getStorageLocations();
      for (StorageLocation location : fields.getValues()) {
        if (location.isObjectLocation()) {
          AbstractObjectAccessor accessor = (AbstractObjectAccessor) location.getAccessor();
          accessors.add(accessor);
        }
      }

      return new FieldTester(accessors.toArray(new AbstractObjectAccessor[0]));
    }

    @Specialization(guards = "rcvr.getObjectLayout() == cachedObjectLayout")
    public Object allFieldsAreValues(final VirtualFrame frame, final SImmutableObject rcvr,
        @Cached("rcvr.getObjectLayout()") final ObjectLayout cachedObjectLayout,
        @Cached("createTester(cachedObjectLayout)") final FieldTester cachedTester) {
      if (cachedTester.allFieldsContainValues(rcvr)) {
        return rcvr;
      } else {
        return notAValue.signal(rcvr);
      }
    }

    @Specialization
    public Object fallback(final Object receiver) {
      SImmutableObject rcvr = (SImmutableObject) receiver;
      boolean allFieldsContainValues = allFieldsContainValues(rcvr);
      if (allFieldsContainValues) {
        return rcvr;
      }
      return notAValue.signal(frame, rcvr);
    }

    protected static final class FieldTester extends Node {
      @Children private final IsValue[] isValueNodes;

      @CompilationFinal(dimensions = 1) private final AbstractObjectAccessor[] fieldAccessors;

      protected FieldTester(final AbstractObjectAccessor[] accessors) {
        fieldAccessors = accessors;
        isValueNodes = new IsValue[accessors.length];

        for (int i = 0; i < isValueNodes.length; i++) {
          isValueNodes[i] = IsValue.createSubNode();
        }
      }

      @ExplodeLoop(kind = LoopExplosionKind.FULL_UNROLL)
      public boolean allFieldsContainValues(final SImmutableObject obj) {
        for (int i = 0; i < isValueNodes.length; i++) {
          Object value = fieldAccessors[i].read(obj);
          if (!isValueNodes[i].executeBoolean(null, value)) {
            return false;
          }
        }
        return true;
      }
    }

    private boolean allFieldsContainValues(final SImmutableObject rcvr) {
      VM.thisMethodNeedsToBeOptimized("Should be optimized or on slowpath");

      if (rcvr.field1 == null) {
        return true;
      }

      boolean result = IsValue.isObjectValue(rcvr.field1);
      if (rcvr.field2 == null || !result) {
        return result;
      }

      result = result && IsValue.isObjectValue(rcvr.field2);
      if (rcvr.field3 == null || !result) {
        return result;
      }

      result = result && IsValue.isObjectValue(rcvr.field3);
      if (rcvr.field4 == null || !result) {
        return result;
      }

      result = result && IsValue.isObjectValue(rcvr.field4);
      if (rcvr.field5 == null || !result) {
        return result;
      }

      result = result && IsValue.isObjectValue(rcvr.field5);
      if (rcvr.getExtensionObjFields() == null || !result) {
        return result;
      }

      Object[] ext = rcvr.getExtensionObjFields();
      for (Object o : ext) {
        if (!IsValue.isObjectValue(o)) {
          return false;
        }
      }
      return true;
    }
  }
}
