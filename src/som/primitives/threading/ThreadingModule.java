package som.primitives.threading;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.source.SourceSection;

import som.compiler.MixinBuilder.MixinDefinitionId;
import som.interpreter.nodes.nary.BinaryExpressionNode;
import som.interpreter.nodes.nary.UnaryExpressionNode;
import som.primitives.Primitive;
import som.vmobjects.SClass;
import som.vmobjects.SObject.SImmutableObject;


public final class ThreadingModule {
  @CompilationFinal public static SImmutableObject  ThreadingModule;
  @CompilationFinal public static SClass            ThreadClass;
  @CompilationFinal public static MixinDefinitionId ThreadClassId;
  @CompilationFinal public static SClass            TaskClass;
  @CompilationFinal public static MixinDefinitionId TaskClassId;
  @CompilationFinal public static SClass            MutexClass;
  @CompilationFinal public static MixinDefinitionId MutexClassId;
  @CompilationFinal public static SClass            ConditionClass;
  @CompilationFinal public static MixinDefinitionId ConditionClassId;

  @GenerateNodeFactory
  @Primitive(primitive = "threadingRegisterCondition:mutex:")
  public abstract static class RegisterConditionAndMutexPrim extends BinaryExpressionNode {
    public RegisterConditionAndMutexPrim(final boolean ew, final SourceSection s) {
      super(ew, s);
    }

    @Specialization
    public final SClass doSClass(final SClass condition, final SClass mutex) {
      assert ConditionClass == null && MutexClass == null;
      ConditionClass = condition;
      MutexClass = mutex;

      ConditionClassId = condition.getMixinDefinition().getMixinId();
      MutexClassId = mutex.getMixinDefinition().getMixinId();
      return condition;
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "threadingRegisterThread:task:")
  public abstract static class RegisterThreadAndTaskPrim extends BinaryExpressionNode {
    public RegisterThreadAndTaskPrim(final boolean ew, final SourceSection s) {
      super(ew, s);
    }

    @Specialization
    public final SClass doSClass(final SClass thread, final SClass task) {
      assert ThreadClass == null && TaskClass == null;
      ThreadClass = thread;
      TaskClass = task;

      ThreadClassId = thread.getMixinDefinition().getMixinId();
      TaskClassId = task.getMixinDefinition().getMixinId();
      return thread;
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "threadingRegisterModule:")
  public abstract static class RegisterModulePrim extends UnaryExpressionNode {
    public RegisterModulePrim(final boolean ew, final SourceSection s) {
      super(ew, s);
    }

    @Specialization
    public final SImmutableObject doSClass(final SImmutableObject module) {
      ThreadingModule = module;
      return module;
    }
  }
}
