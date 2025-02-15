package somns.vm;

import java.util.ArrayList;
import java.util.List;

import org.graalvm.collections.EconomicMap;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

import bd.primitives.PrimitiveLoader;
import bd.primitives.Specializer;
import somns.interpreter.actors.EagerResolvePromiseNodeFactory;
import somns.interpreter.actors.ErrorPromiseNodeFactory;
import somns.interpreter.actors.ResolvePromiseNodeFactory;
import somns.interpreter.nodes.specialized.AndMessageNodeFactory;
import somns.interpreter.nodes.specialized.IfMessageNodeGen;
import somns.interpreter.nodes.specialized.IfTrueIfFalseMessageNodeFactory;
import somns.interpreter.nodes.specialized.IntDownToDoInlinedLiteralsNodeFactory;
import somns.interpreter.nodes.specialized.IntDownToDoMessageNodeFactory;
import somns.interpreter.nodes.specialized.IntTimesRepeatLiteralNodeFactory;
import somns.interpreter.nodes.specialized.IntToByDoMessageNodeFactory;
import somns.interpreter.nodes.specialized.IntToDoInlinedLiteralsNodeFactory;
import somns.interpreter.nodes.specialized.IntToDoMessageNodeFactory;
import somns.interpreter.nodes.specialized.NotMessageNodeFactory;
import somns.interpreter.nodes.specialized.OrMessageNodeFactory;
import somns.interpreter.nodes.specialized.whileloops.WhileFalsePrimitiveNodeFactory;
import somns.interpreter.nodes.specialized.whileloops.WhileTruePrimitiveNodeFactory;
import somns.primitives.ActivityJoinFactory;
import somns.primitives.ActivitySpawnFactory;
import somns.primitives.AsStringPrimFactory;
import somns.primitives.BlockPrimsFactory;
import somns.primitives.ClassPrimsFactory;
import somns.primitives.CosPrimFactory;
import somns.primitives.DoublePrimsFactory;
import somns.primitives.EqualsEqualsPrimFactory;
import somns.primitives.EqualsPrimFactory;
import somns.primitives.ExceptionsPrimsFactory;
import somns.primitives.FilePrimsFactory;
import somns.primitives.HashPrimFactory;
import somns.primitives.IntegerPrimsFactory;
import somns.primitives.MethodPrimsFactory;
import somns.primitives.MirrorPrimsFactory;
import somns.primitives.ObjectPrimsFactory;
import somns.primitives.ObjectSystemPrimsFactory;
import somns.primitives.PathPrimsFactory;
import somns.primitives.SizeAndLengthPrimFactory;
import somns.primitives.StringPrimsFactory;
import somns.primitives.SystemPrimsFactory;
import somns.primitives.TimerPrimFactory;
import somns.primitives.UnequalsPrimFactory;
import somns.primitives.actors.ActorClassesFactory;
import somns.primitives.actors.CreateActorPrimFactory;
import somns.primitives.actors.PromisePrimsFactory;
import somns.primitives.arithmetic.AbsPrimFactory;
import somns.primitives.arithmetic.AdditionPrimFactory;
import somns.primitives.arithmetic.DividePrimFactory;
import somns.primitives.arithmetic.DoubleDivPrimFactory;
import somns.primitives.arithmetic.ExpPrimFactory;
import somns.primitives.arithmetic.GreaterThanOrEqualPrimFactory;
import somns.primitives.arithmetic.GreaterThanPrimFactory;
import somns.primitives.arithmetic.LessThanOrEqualPrimFactory;
import somns.primitives.arithmetic.LessThanPrimFactory;
import somns.primitives.arithmetic.LogPrimFactory;
import somns.primitives.arithmetic.ModuloPrimFactory;
import somns.primitives.arithmetic.MultiplicationPrimFactory;
import somns.primitives.arithmetic.PowPrimFactory;
import somns.primitives.arithmetic.RemainderPrimFactory;
import somns.primitives.arithmetic.SinPrimFactory;
import somns.primitives.arithmetic.SqrtPrimFactory;
import somns.primitives.arithmetic.SubtractionPrimFactory;
import somns.primitives.arrays.AtPrimFactory;
import somns.primitives.arrays.AtPutPrimFactory;
import somns.primitives.arrays.CopyPrimFactory;
import somns.primitives.arrays.DoIndexesPrimFactory;
import somns.primitives.arrays.DoPrimFactory;
import somns.primitives.arrays.NewImmutableArrayNodeFactory;
import somns.primitives.arrays.NewPrimFactory;
import somns.primitives.arrays.PutAllNodeFactory;
import somns.primitives.bitops.BitAndPrimFactory;
import somns.primitives.bitops.BitOrPrimFactory;
import somns.primitives.bitops.BitXorPrimFactory;
import somns.primitives.processes.ChannelPrimitivesFactory;
import somns.primitives.threading.ConditionPrimitivesFactory;
import somns.primitives.threading.DelayPrimitivesFactory;
import somns.primitives.threading.MutexPrimitivesFactory;
import somns.primitives.threading.ThreadPrimitivesFactory;
import somns.primitives.threading.ThreadingModuleFactory;
import somns.primitives.transactions.AtomicPrimFactory;
import somns.VM;
import somns.compiler.AccessModifier;
import somns.interpreter.Primitive;
import somns.interpreter.SomLanguage;
import somns.interpreter.nodes.ExpressionNode;
import somns.interpreter.nodes.ArgumentReadNode.LocalArgumentReadNode;
import somns.interpreter.nodes.dispatch.Dispatchable;
import somns.interpreter.nodes.specialized.IfInlinedLiteralNode;
import somns.interpreter.nodes.specialized.IfTrueIfFalseInlinedLiteralsNode;
import somns.interpreter.nodes.specialized.BooleanInlinedLiteralNode.AndInlinedLiteralNode;
import somns.interpreter.nodes.specialized.BooleanInlinedLiteralNode.OrInlinedLiteralNode;
import somns.interpreter.nodes.specialized.whileloops.WhileInlinedLiteralsNode;
import somns.interpreter.nodes.specialized.whileloops.WhileWithStaticBlocksNode.WhileWithStaticBlocksNodeFactory;
import somns.vmobjects.SInvokable;
import somns.vmobjects.SSymbol;


public class Primitives extends PrimitiveLoader<VM, ExpressionNode, SSymbol> {

  private static List<Specializer<VM, ExpressionNode, SSymbol>> specializer =
      initSpecializers();

  private EconomicMap<SSymbol, Dispatchable> vmMirrorPrimitives;
  private final SomLanguage                  lang;

  public Primitives(final SomLanguage lang) {
    super(Symbols.PROVIDER);
    vmMirrorPrimitives = EconomicMap.create();
    this.lang = lang;
    initialize();
  }

  private static SInvokable constructVmMirrorPrimitive(final SSymbol signature,
      final Specializer<VM, ExpressionNode, SSymbol> specializer, final SomLanguage lang) {
    CompilerAsserts.neverPartOfCompilation("This is only executed during bootstrapping.");
    assert signature.getNumberOfSignatureArguments() > 1
        : "Primitives should have the vmMirror as receiver, "
            + "and then at least one object they are applied to";

    // ignore the implicit vmMirror argument
    final int numArgs = signature.getNumberOfSignatureArguments() - 1;

    Source s = SomLanguage.getSyntheticSource("primitive", specializer.getName());
    ExpressionNode[] args = new ExpressionNode[numArgs];

    SourceSection source = s.createSection(1);
    for (int i = 0; i < numArgs; i++) {
      // we do not pass the vmMirror, makes it easier to use the same primitives
      // as replacements on the node level
      args[i] = new LocalArgumentReadNode(true, i + 1).initialize(source);
    }

    ExpressionNode primNode = specializer.create(null, args, source, false, lang.getVM());

    String name = "vmMirror>>" + signature.toString();

    Primitive primMethodNode = new Primitive(name, primNode,
        (ExpressionNode) primNode.deepCopy(), false, lang);
    return new SInvokable(signature, AccessModifier.PUBLIC,
        primMethodNode, null);
  }

  public EconomicMap<SSymbol, Dispatchable> takeVmMirrorPrimitives() {
    assert vmMirrorPrimitives != null : "vmMirrorPrimitives can only be obtained once";
    EconomicMap<SSymbol, Dispatchable> result = vmMirrorPrimitives;
    vmMirrorPrimitives = null;
    return result;
  }

  @Override
  protected void registerPrimitive(
      final Specializer<VM, ExpressionNode, SSymbol> specializer) {
    String vmMirrorName = specializer.getPrimitive().primitive();

    if (!("".equals(vmMirrorName))) {
      SSymbol signature = Symbols.symbolFor(vmMirrorName);
      assert !vmMirrorPrimitives.containsKey(
          signature) : "clash of vmMirrorPrimitive names";
      vmMirrorPrimitives.put(signature,
          constructVmMirrorPrimitive(signature, specializer, lang));
    }
  }

  @Override
  protected List<Specializer<VM, ExpressionNode, SSymbol>> getSpecializers() {
    return specializer;
  }

  private static List<Specializer<VM, ExpressionNode, SSymbol>> initSpecializers() {
    List<Specializer<VM, ExpressionNode, SSymbol>> allFactories = new ArrayList<>();
    addAll(allFactories, ActorClassesFactory.getFactories());
    addAll(allFactories, BlockPrimsFactory.getFactories());
    addAll(allFactories, ClassPrimsFactory.getFactories());
    addAll(allFactories, DoublePrimsFactory.getFactories());
    addAll(allFactories, ExceptionsPrimsFactory.getFactories());
    addAll(allFactories, FilePrimsFactory.getFactories());
    addAll(allFactories, IfMessageNodeGen.getFactories());
    addAll(allFactories, IntegerPrimsFactory.getFactories());
    addAll(allFactories, MethodPrimsFactory.getFactories());
    addAll(allFactories, MirrorPrimsFactory.getFactories());
    addAll(allFactories, ObjectPrimsFactory.getFactories());
    addAll(allFactories, ObjectSystemPrimsFactory.getFactories());
    addAll(allFactories, PathPrimsFactory.getFactories());
    addAll(allFactories, PromisePrimsFactory.getFactories());
    addAll(allFactories, StringPrimsFactory.getFactories());
    addAll(allFactories, SystemPrimsFactory.getFactories());

    addAll(allFactories, ActivitySpawnFactory.getFactories());
    addAll(allFactories, ThreadingModuleFactory.getFactories());
    addAll(allFactories, ConditionPrimitivesFactory.getFactories());
    addAll(allFactories, DelayPrimitivesFactory.getFactories());
    addAll(allFactories, MutexPrimitivesFactory.getFactories());
    addAll(allFactories, ActivityJoinFactory.getFactories());
    addAll(allFactories, ThreadPrimitivesFactory.getFactories());
    addAll(allFactories, ChannelPrimitivesFactory.getFactories());

    add(allFactories, WhileTruePrimitiveNodeFactory.getInstance());
    add(allFactories, WhileFalsePrimitiveNodeFactory.getInstance());
    add(allFactories, AbsPrimFactory.getInstance());
    add(allFactories, AdditionPrimFactory.getInstance());
    add(allFactories, AndMessageNodeFactory.getInstance());
    add(allFactories, AsStringPrimFactory.getInstance());
    add(allFactories, AtomicPrimFactory.getInstance());
    add(allFactories, AtPrimFactory.getInstance());
    add(allFactories, AtPutPrimFactory.getInstance());
    add(allFactories, BitAndPrimFactory.getInstance());
    add(allFactories, BitOrPrimFactory.getInstance());
    add(allFactories, BitXorPrimFactory.getInstance());
    add(allFactories, CopyPrimFactory.getInstance());
    add(allFactories, CosPrimFactory.getInstance());
    add(allFactories, DividePrimFactory.getInstance());
    add(allFactories, DoIndexesPrimFactory.getInstance());
    add(allFactories, DoPrimFactory.getInstance());
    add(allFactories, DoubleDivPrimFactory.getInstance());
    add(allFactories, EagerResolvePromiseNodeFactory.getInstance());
    add(allFactories, EqualsEqualsPrimFactory.getInstance());
    add(allFactories, EqualsPrimFactory.getInstance());
    add(allFactories, ExpPrimFactory.getInstance());
    add(allFactories, GreaterThanOrEqualPrimFactory.getInstance());
    add(allFactories, GreaterThanPrimFactory.getInstance());
    add(allFactories, HashPrimFactory.getInstance());
    add(allFactories, IfTrueIfFalseMessageNodeFactory.getInstance());
    add(allFactories, IntToDoMessageNodeFactory.getInstance());
    add(allFactories, IntDownToDoMessageNodeFactory.getInstance());
    add(allFactories, IntToByDoMessageNodeFactory.getInstance());
    add(allFactories, LessThanOrEqualPrimFactory.getInstance());
    add(allFactories, LessThanPrimFactory.getInstance());
    add(allFactories, LogPrimFactory.getInstance());
    add(allFactories, PowPrimFactory.getInstance());
    add(allFactories, ModuloPrimFactory.getInstance());
    add(allFactories, MultiplicationPrimFactory.getInstance());
    add(allFactories, NewPrimFactory.getInstance());
    add(allFactories, NewImmutableArrayNodeFactory.getInstance());
    add(allFactories, NotMessageNodeFactory.getInstance());
    add(allFactories, OrMessageNodeFactory.getInstance());
    add(allFactories, PutAllNodeFactory.getInstance());
    add(allFactories, RemainderPrimFactory.getInstance());
    add(allFactories, SinPrimFactory.getInstance());
    add(allFactories, SizeAndLengthPrimFactory.getInstance());
    add(allFactories, SqrtPrimFactory.getInstance());
    add(allFactories, SubtractionPrimFactory.getInstance());
    add(allFactories, UnequalsPrimFactory.getInstance());
    add(allFactories, new WhileWithStaticBlocksNodeFactory());
    add(allFactories, TimerPrimFactory.getInstance());

    add(allFactories, CreateActorPrimFactory.getInstance());
    add(allFactories, ResolvePromiseNodeFactory.getInstance());
    add(allFactories, ErrorPromiseNodeFactory.getInstance());

    return allFactories;
  }

  public static List<Class<? extends Node>> getInlinableNodes() {
    List<Class<? extends Node>> nodes = new ArrayList<>();
    nodes.add(AndInlinedLiteralNode.class);
    nodes.add(OrInlinedLiteralNode.class);
    nodes.add(IfInlinedLiteralNode.class);
    nodes.add(IfTrueIfFalseInlinedLiteralsNode.class);

    nodes.add(WhileInlinedLiteralsNode.class);
    return nodes;
  }

  public static List<NodeFactory<? extends Node>> getInlinableFactories() {
    List<NodeFactory<? extends Node>> factories = new ArrayList<>();

    factories.add(IntDownToDoInlinedLiteralsNodeFactory.getInstance());
    factories.add(IntTimesRepeatLiteralNodeFactory.getInstance());
    factories.add(IntToDoInlinedLiteralsNodeFactory.getInstance());

    return factories;
  }
}
