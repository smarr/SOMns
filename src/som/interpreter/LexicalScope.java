package som.interpreter;

import java.util.HashMap;

import som.compiler.ClassBuilder.ClassDefinitionId;
import som.compiler.ClassDefinition;
import som.interpreter.LexicalScope.ClassScope.ClassIdAndContextLevel;
import som.interpreter.nodes.dispatch.Dispatchable;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.FrameDescriptor;


public abstract class LexicalScope {

  // TODO: figure out whether we can use this lexical scope also for the
  //       super sends. seems like we currently have two similar ways to solve
  //       similar problems, instead of a single one
  public static final class ClassScope extends LexicalScope {
    private final ClassScope outerClass;
    private HashMap<SSymbol, Dispatchable> slotsClassesAndMethods;

    @CompilationFinal private ClassDefinition classDefinition;

    public ClassScope(final ClassScope outerClass) {
      this.outerClass = outerClass;
    }

    public HashMap<SSymbol, Dispatchable> getDispatchables() {
      return slotsClassesAndMethods;
    }

    @SuppressWarnings("unchecked")
    public void setClassDefinition(final ClassDefinition def, final boolean classSide) {
      assert def != null;
      classDefinition = def;

      if (classSide) {
        HashMap<SSymbol, ? extends Dispatchable> disps = classDefinition.getFactoryMethods();
        slotsClassesAndMethods = (HashMap<SSymbol, Dispatchable>) disps;
      } else {
        slotsClassesAndMethods = classDefinition.getInstanceDispatchables();
      }
    }

    public static final class ClassIdAndContextLevel {
      public final ClassDefinitionId classId;
      public final int contextLevel;
      ClassIdAndContextLevel(final ClassDefinitionId classId, final int contextLevel) {
        this.classId = classId;
        this.contextLevel = contextLevel;
      }

      @Override
      public String toString() {
        return "Class+Ctx[" + classId.toString() + ", " + contextLevel + "]";
      }
    }

    public ClassIdAndContextLevel lookupSlotOrClass(final SSymbol selector, final int contextLevel) {
      assert classDefinition != null;
      if (slotsClassesAndMethods.containsKey(selector)) {
        return new ClassIdAndContextLevel(classDefinition.getClassId(), contextLevel);
      }

      if (outerClass != null) {
        return outerClass.lookupSlotOrClass(selector, contextLevel + 1);
      }
      return null;
    }

    @Override
    public String toString() {
      String clsName = classDefinition != null
          ? classDefinition.getName().getString() : "";
      return "ClassScope(" + clsName + ")";
    }
  }

  public static final class MethodScope extends LexicalScope {
    private final FrameDescriptor frameDescriptor;
    private final MethodScope     outerMethod;
    private final ClassScope      outerClass;

    @CompilationFinal private Method method;

    public MethodScope(final FrameDescriptor frameDescriptor,
        final MethodScope outerMethod, final ClassScope outerClass) {
      this.frameDescriptor = frameDescriptor;
      this.outerMethod     = outerMethod;
      this.outerClass      = outerClass;
    }

    /** Create new and independent copy. */
    public MethodScope split() {
      FrameDescriptor splitDescriptor = frameDescriptor.copy();
      return new MethodScope(splitDescriptor, outerMethod, outerClass);
    }

    public FrameDescriptor getFrameDescriptor() {
      return frameDescriptor;
    }

    public MethodScope getOuterMethodScopeOrNull() {
      return outerMethod;
    }

    public MethodScope getOuterMethodScope() {
      assert outerMethod != null;
      return outerMethod;
    }

    public void propagateLoopCountThroughoutMethodScope(final long count) {
      if (outerMethod != null) {
        outerMethod.method.propagateLoopCountThroughoutMethodScope(count);
      }
    }

    public Method getMethod() {
      return method;
    }

    public void setMethod(final Method method) {
      CompilerAsserts.neverPartOfCompilation("LexicalContext.sOM()");
      // might be reset when doing inlining/embedded, but should always
      // refer to the same method
      assert this.method == null ||
          this.method.getSourceSection() == method.getSourceSection();
      this.method = method;
    }

    @Override
    public String toString() {
      return "MethodScope(" + frameDescriptor.toString() + ")";
    }

    public ClassIdAndContextLevel lookupSlotOrClass(final SSymbol selector) {
      return getEnclosingClass().lookupSlotOrClass(selector, 0);
    }

    public ClassScope getEnclosingClass() {
      if (outerMethod == null) {
        return outerClass;
      } else {
        return outerMethod.getEnclosingClass();
      }
    }

    public ClassScope getHolderScope() {
      return outerClass;
    }
  }
}
