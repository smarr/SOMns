package som.interpreter;

import som.compiler.ClassDefinition;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.FrameDescriptor;


public abstract class LexicalScope {

  public static final class ClassScope extends LexicalScope {
    private final ClassScope outerClass;

    public ClassScope(final ClassScope outerClass) {
      this.outerClass = outerClass;
    }

    @CompilationFinal private ClassDefinition classDefinition;

    public void setClassDefinition(final ClassDefinition def) {
      assert def != null;
      classDefinition = def;
    }

    public int lookupContextLevelOfSlotOrClass(final SSymbol selector, final int contextLevel) {
      assert classDefinition != null;
      if (classDefinition.hasSlotOrClass(selector)) {
        return contextLevel;
      }

      if (outerClass != null) {
        return outerClass.lookupContextLevelOfSlotOrClass(selector, contextLevel + 1);
      }
      return -1;
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
      return "LexScp[" + frameDescriptor.toString() + "]";
    }

    public int lookupContextLevelOfSlotOrClass(final SSymbol selector) {
      return getEnclosingClass().lookupContextLevelOfSlotOrClass(selector, 0);
    }

    private ClassScope getEnclosingClass() {
      if (outerMethod == null) {
        return outerClass;
      } else {
        return outerMethod.getEnclosingClass();
      }
    }
  }
}
