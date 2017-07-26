package som.vm;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

import som.VM;
import som.compiler.AccessModifier;
import som.compiler.MethodBuilder;
import som.interpreter.Primitive;
import som.interpreter.SomLanguage;
import som.interpreter.actors.ErrorPromiseNodeFactory;
import som.interpreter.actors.ResolvePromiseNodeFactory;
import som.interpreter.nodes.ArgumentReadNode.LocalArgumentReadNode;
import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.dispatch.Dispatchable;
import som.interpreter.nodes.nary.EagerlySpecializableNode;
import som.interpreter.nodes.specialized.AndMessageNodeFactory;
import som.interpreter.nodes.specialized.IfMessageNodeGen;
import som.interpreter.nodes.specialized.IfTrueIfFalseMessageNodeFactory;
import som.interpreter.nodes.specialized.IntDownToDoMessageNodeFactory;
import som.interpreter.nodes.specialized.IntToByDoMessageNodeFactory;
import som.interpreter.nodes.specialized.IntToDoMessageNodeFactory;
import som.interpreter.nodes.specialized.NotMessageNodeFactory;
import som.interpreter.nodes.specialized.OrMessageNodeFactory;
import som.interpreter.nodes.specialized.whileloops.WhilePrimitiveNodeFactory;
import som.interpreter.nodes.specialized.whileloops.WhileWithStaticBlocksNode.WhileWithStaticBlocksNodeFactory;
import som.primitives.ActivityJoinFactory;
import som.primitives.ActivitySpawnFactory;
import som.primitives.AsStringPrimFactory;
import som.primitives.BlockPrimsFactory;
import som.primitives.ClassPrimsFactory;
import som.primitives.CosPrimFactory;
import som.primitives.DoublePrimsFactory;
import som.primitives.EqualsEqualsPrimFactory;
import som.primitives.EqualsPrimFactory;
import som.primitives.ExceptionsPrimsFactory;
import som.primitives.HashPrimFactory;
import som.primitives.IntegerPrimsFactory;
import som.primitives.MethodPrimsFactory;
import som.primitives.MirrorPrimsFactory;
import som.primitives.ObjectPrimsFactory;
import som.primitives.ObjectSystemPrimsFactory;
import som.primitives.Primitive.NoChild;
import som.primitives.SizeAndLengthPrimFactory;
import som.primitives.StringPrimsFactory;
import som.primitives.SystemPrimsFactory;
import som.primitives.TimerPrimFactory;
import som.primitives.UnequalsPrimFactory;
import som.primitives.actors.ActorClassesFactory;
import som.primitives.actors.CreateActorPrimFactory;
import som.primitives.actors.PromisePrimsFactory;
import som.primitives.arithmetic.AdditionPrimFactory;
import som.primitives.arithmetic.DividePrimFactory;
import som.primitives.arithmetic.DoubleDivPrimFactory;
import som.primitives.arithmetic.ExpPrimFactory;
import som.primitives.arithmetic.GreaterThanOrEqualPrimFactory;
import som.primitives.arithmetic.GreaterThanPrimFactory;
import som.primitives.arithmetic.LessThanOrEqualPrimFactory;
import som.primitives.arithmetic.LessThanPrimFactory;
import som.primitives.arithmetic.LogPrimFactory;
import som.primitives.arithmetic.ModuloPrimFactory;
import som.primitives.arithmetic.MultiplicationPrimFactory;
import som.primitives.arithmetic.RemainderPrimFactory;
import som.primitives.arithmetic.SinPrimFactory;
import som.primitives.arithmetic.SqrtPrimFactory;
import som.primitives.arithmetic.SubtractionPrimFactory;
import som.primitives.arrays.AtPrimFactory;
import som.primitives.arrays.AtPutPrimFactory;
import som.primitives.arrays.CopyPrimFactory;
import som.primitives.arrays.DoIndexesPrimFactory;
import som.primitives.arrays.DoPrimFactory;
import som.primitives.arrays.NewImmutableArrayNodeFactory;
import som.primitives.arrays.NewPrimFactory;
import som.primitives.arrays.PutAllNodeFactory;
import som.primitives.bitops.BitAndPrimFactory;
import som.primitives.bitops.BitOrPrimFactory;
import som.primitives.bitops.BitXorPrimFactory;
import som.primitives.processes.ChannelPrimitivesFactory;
import som.primitives.threading.ConditionPrimitivesFactory;
import som.primitives.threading.DelayPrimitivesFactory;
import som.primitives.threading.MutexPrimitivesFactory;
import som.primitives.threading.ThreadPrimitivesFactory;
import som.primitives.threading.ThreadingModuleFactory;
import som.primitives.transactions.AtomicPrimFactory;
import som.vm.constants.KernelObjFactory;
import som.vmobjects.SInvokable;
import som.vmobjects.SSymbol;


public class Primitives {
  private final VM vm;
  private HashMap<SSymbol, Dispatchable> vmMirrorPrimitives;
  private final HashMap<SSymbol, Specializer<? extends ExpressionNode>>  eagerPrimitives;

  public Primitives(final SomLanguage lang) {
    vm = lang.getVM();
    vmMirrorPrimitives = new HashMap<>();
    eagerPrimitives    = new HashMap<>();
    initialize(lang);
  }

  @SuppressWarnings("unchecked")
  public Specializer<EagerlySpecializableNode> getParserSpecializer(final SSymbol selector,
      final ExpressionNode[] argNodes) {
    Specializer<? extends ExpressionNode> specializer = eagerPrimitives.get(selector);
    if (specializer != null && specializer.inParser() && specializer.matches(null, argNodes)) {
      return (Specializer<EagerlySpecializableNode>) specializer;
    }
    return null;
  }

  @SuppressWarnings("unchecked")
  public Specializer<EagerlySpecializableNode> getEagerSpecializer(final SSymbol selector,
      final Object[] arguments, final ExpressionNode[] argumentNodes) {
    Specializer<? extends ExpressionNode> specializer = eagerPrimitives.get(selector);
    if (specializer != null && specializer.matches(arguments, argumentNodes)) {
      return (Specializer<EagerlySpecializableNode>) specializer;
    }
    return null;
  }

  /**
   * A Specializer defines when a node can be used as a eager primitive and how
   * it is to be instantiated.
   */
  public static class Specializer<T> {
    protected final VM vm;
    protected final som.primitives.Primitive prim;
    protected final NodeFactory<T> fact;
    private final NodeFactory<? extends ExpressionNode> extraChildFactory;

    @SuppressWarnings("unchecked")
    public Specializer(final som.primitives.Primitive prim, final NodeFactory<T> fact, final VM vm) {
      this.prim = prim;
      this.fact = fact;
      this.vm   = vm;

      if (prim.extraChild() == NoChild.class) {
        extraChildFactory = null;
      } else {
        try {
          extraChildFactory = (NodeFactory<? extends ExpressionNode>) prim.extraChild().getMethod("getInstance").invoke(null);
        } catch (IllegalAccessException | IllegalArgumentException
            | InvocationTargetException | NoSuchMethodException
            | SecurityException e) {
          throw new RuntimeException(e);
        }
      }
    }

    public boolean inParser() {
      return prim.inParser() && !prim.requiresArguments();
    }

    public boolean noWrapper() {
      return prim.noWrapper();
    }

    public boolean matches(final Object[] args, final ExpressionNode[] argNodes) {
      if (prim.disabled() && VmSettings.DYNAMIC_METRICS) {
        return false;
      }

      if (args == null || prim.receiverType().length == 0) {
        // no constraints, so, it matches
        return true;
      }

      for (Class<?> c : prim.receiverType()) {
        if (c.isInstance(args[0])) {
          return true;
        }
      }
      return false;
    }

    private int numberOfNodeConstructorArguments(final ExpressionNode[] argNodes) {
      return argNodes.length + 2 +
          (extraChildFactory != null ? 1 : 0) +
          (prim.requiresArguments() ? 1 : 0) +
          (prim.requiresContext() ? 1 : 0);
    }

    public T create(final Object[] arguments,
        final ExpressionNode[] argNodes, final SourceSection section,
        final boolean eagerWrapper) {
      assert arguments == null || arguments.length == argNodes.length;
      int numArgs = numberOfNodeConstructorArguments(argNodes);

      Object[] ctorArgs = new Object[numArgs];
      ctorArgs[0] = eagerWrapper;
      ctorArgs[1] = section;
      int offset = 2;

      if (prim.requiresContext()) {
        ctorArgs[offset] = vm;
        offset += 1;
      }

      if (prim.requiresArguments()) {
        assert arguments != null;
        ctorArgs[offset] = arguments;
        offset += 1;
      }

      for (int i = 0; i < argNodes.length; i += 1) {
        ctorArgs[offset] = eagerWrapper ? null : argNodes[i];
        offset += 1;
      }

      if (extraChildFactory != null) {
        ctorArgs[offset] = extraChildFactory.createNode(false, null, null);
        offset += 1;
      }

      return fact.createNode(ctorArgs);
    }
  }

  private static som.primitives.Primitive[] getPrimitiveAnnotation(
      final NodeFactory<? extends ExpressionNode> primFact) {
    Class<?> nodeClass = primFact.getNodeClass();
    return nodeClass.getAnnotationsByType(som.primitives.Primitive.class);
  }

  private static SInvokable constructVmMirrorPrimitive(final SSymbol signature,
      final Specializer<? extends ExpressionNode> specializer, final SomLanguage lang) {
    CompilerAsserts.neverPartOfCompilation("This is only executed during bootstrapping.");
    assert signature.getNumberOfSignatureArguments() > 1 :
      "Primitives should have the vmMirror as receiver, " +
      "and then at least one object they are applied to";

    // ignore the implicit vmMirror argument
    final int numArgs = signature.getNumberOfSignatureArguments() - 1;

    Source s = SomLanguage.getSyntheticSource("primitive", specializer.fact.getClass().getSimpleName());
    MethodBuilder prim = new MethodBuilder(true, lang);
    ExpressionNode[] args = new ExpressionNode[numArgs];

    for (int i = 0; i < numArgs; i++) {
      // we do not pass the vmMirror, makes it easier to use the same primitives
      // as replacements on the node level
      args[i] = new LocalArgumentReadNode(true, i + 1, s.createSection(1));
    }

    SourceSection source = s.createSection(1);
    ExpressionNode primNode = specializer.create(null, args, source, false);

    String name = "vmMirror>>" + signature.toString();

    Primitive primMethodNode = new Primitive(name, primNode,
        prim.getCurrentMethodScope().getFrameDescriptor(),
        (ExpressionNode) primNode.deepCopy(), false, lang);
    return new SInvokable(signature, AccessModifier.PUBLIC,
        primMethodNode, null);
  }

  public HashMap<SSymbol, Dispatchable> takeVmMirrorPrimitives() {
    assert vmMirrorPrimitives != null : "vmMirrorPrimitives can only be obtained once";
    HashMap<SSymbol, Dispatchable> result = vmMirrorPrimitives;
    vmMirrorPrimitives = null;
    return result;
  }

  /**
   * Setup the lookup data structures for vm primitive registration as well as
   * eager primitive replacement.
   */
  private void initialize(final SomLanguage lang) {
    List<NodeFactory<? extends ExpressionNode>> primFacts = getFactories();
    for (NodeFactory<? extends ExpressionNode> primFact : primFacts) {
      som.primitives.Primitive[] prims = getPrimitiveAnnotation(primFact);
      if (prims != null) {
        for (som.primitives.Primitive prim : prims) {
          Specializer<? extends ExpressionNode> specializer = getSpecializer(prim, primFact);
          String vmMirrorName = prim.primitive();

          if (!("".equals(vmMirrorName))) {
            SSymbol signature = Symbols.symbolFor(vmMirrorName);
            assert !vmMirrorPrimitives.containsKey(signature) : "clash of vmMirrorPrimitive names";
            vmMirrorPrimitives.put(signature,
                constructVmMirrorPrimitive(signature, specializer, lang));
          }

          if (!("".equals(prim.selector()))) {
            SSymbol msgSel = Symbols.symbolFor(prim.selector());
            assert !eagerPrimitives.containsKey(msgSel) : "clash of selectors and eager specialization";
            eagerPrimitives.put(msgSel, specializer);
          }
        }
      }
    }
  }

  @SuppressWarnings("unchecked")
  private <T> Specializer<T> getSpecializer(final som.primitives.Primitive prim, final NodeFactory<T> factory) {
    try {
      return prim.specializer().
          getConstructor(som.primitives.Primitive.class, NodeFactory.class, VM.class).
          newInstance(prim, factory, vm);
    } catch (InstantiationException | IllegalAccessException |
        IllegalArgumentException | InvocationTargetException |
        NoSuchMethodException | SecurityException e) {
      throw new RuntimeException(e);
    }
  }

  private static List<NodeFactory<? extends ExpressionNode>> getFactories() {
    List<NodeFactory<? extends ExpressionNode>> allFactories = new ArrayList<>();
    allFactories.addAll(ActorClassesFactory.getFactories());
    allFactories.addAll(BlockPrimsFactory.getFactories());
    allFactories.addAll(ClassPrimsFactory.getFactories());
    allFactories.addAll(DoublePrimsFactory.getFactories());
    allFactories.addAll(ExceptionsPrimsFactory.getFactories());
    allFactories.addAll(IfMessageNodeGen.getFactories());
    allFactories.addAll(IntegerPrimsFactory.getFactories());
    allFactories.addAll(KernelObjFactory.getFactories());
    allFactories.addAll(MethodPrimsFactory.getFactories());
    allFactories.addAll(MirrorPrimsFactory.getFactories());
    allFactories.addAll(ObjectPrimsFactory.getFactories());
    allFactories.addAll(ObjectSystemPrimsFactory.getFactories());
    allFactories.addAll(PromisePrimsFactory.getFactories());
    allFactories.addAll(StringPrimsFactory.getFactories());
    allFactories.addAll(SystemPrimsFactory.getFactories());
    allFactories.addAll(WhilePrimitiveNodeFactory.getFactories());

    allFactories.addAll(ActivitySpawnFactory.getFactories());
    allFactories.addAll(ThreadingModuleFactory.getFactories());
    allFactories.addAll(ConditionPrimitivesFactory.getFactories());
    allFactories.addAll(DelayPrimitivesFactory.getFactories());
    allFactories.addAll(MutexPrimitivesFactory.getFactories());
    allFactories.addAll(ActivityJoinFactory.getFactories());
    allFactories.addAll(ThreadPrimitivesFactory.getFactories());
    allFactories.addAll(ChannelPrimitivesFactory.getFactories());

    allFactories.add(AdditionPrimFactory.getInstance());
    allFactories.add(AndMessageNodeFactory.getInstance());
    allFactories.add(AsStringPrimFactory.getInstance());
    allFactories.add(AtomicPrimFactory.getInstance());
    allFactories.add(AtPrimFactory.getInstance());
    allFactories.add(AtPutPrimFactory.getInstance());
    allFactories.add(BitAndPrimFactory.getInstance());
    allFactories.add(BitOrPrimFactory.getInstance());
    allFactories.add(BitXorPrimFactory.getInstance());
    allFactories.add(CopyPrimFactory.getInstance());
    allFactories.add(CosPrimFactory.getInstance());
    allFactories.add(DividePrimFactory.getInstance());
    allFactories.add(DoIndexesPrimFactory.getInstance());
    allFactories.add(DoPrimFactory.getInstance());
    allFactories.add(DoubleDivPrimFactory.getInstance());
    allFactories.add(EqualsEqualsPrimFactory.getInstance());
    allFactories.add(EqualsPrimFactory.getInstance());
    allFactories.add(ExpPrimFactory.getInstance());
    allFactories.add(GreaterThanOrEqualPrimFactory.getInstance());
    allFactories.add(GreaterThanPrimFactory.getInstance());
    allFactories.add(HashPrimFactory.getInstance());
    allFactories.add(IfTrueIfFalseMessageNodeFactory.getInstance());
    allFactories.add(IntToDoMessageNodeFactory.getInstance());
    allFactories.add(IntDownToDoMessageNodeFactory.getInstance());
    allFactories.add(IntToByDoMessageNodeFactory.getInstance());
    allFactories.add(LessThanOrEqualPrimFactory.getInstance());
    allFactories.add(LessThanPrimFactory.getInstance());
    allFactories.add(LogPrimFactory.getInstance());
    allFactories.add(ModuloPrimFactory.getInstance());
    allFactories.add(MultiplicationPrimFactory.getInstance());
    allFactories.add(NewPrimFactory.getInstance());
    allFactories.add(NewImmutableArrayNodeFactory.getInstance());
    allFactories.add(NotMessageNodeFactory.getInstance());
    allFactories.add(OrMessageNodeFactory.getInstance());
    allFactories.add(PutAllNodeFactory.getInstance());
    allFactories.add(RemainderPrimFactory.getInstance());
    allFactories.add(SinPrimFactory.getInstance());
    allFactories.add(SizeAndLengthPrimFactory.getInstance());
    allFactories.add(SqrtPrimFactory.getInstance());
    allFactories.add(SubtractionPrimFactory.getInstance());
    allFactories.add(UnequalsPrimFactory.getInstance());
    allFactories.add(new WhileWithStaticBlocksNodeFactory());
    allFactories.add(TimerPrimFactory.getInstance());

    allFactories.add(CreateActorPrimFactory.getInstance());
    allFactories.add(ResolvePromiseNodeFactory.getInstance());
    allFactories.add(ErrorPromiseNodeFactory.getInstance());
    return allFactories;
  }
}
