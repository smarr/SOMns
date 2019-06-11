package som.vm;

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
import som.interpreter.nodes.specialized.AndMessageNodeFactory;
import som.interpreter.nodes.specialized.BooleanInlinedLiteralNode.AndInlinedLiteralNode;
import som.interpreter.nodes.specialized.BooleanInlinedLiteralNode.OrInlinedLiteralNode;
import som.interpreter.nodes.specialized.IfInlinedLiteralNode;
import som.interpreter.nodes.specialized.IfMessageNodeGen;
import som.interpreter.nodes.specialized.IfTrueIfFalseInlinedLiteralsNode;
import som.interpreter.nodes.specialized.IfTrueIfFalseMessageNodeFactory;
import som.interpreter.nodes.specialized.IntDownToDoInlinedLiteralsNodeFactory;
import som.interpreter.nodes.specialized.IntDownToDoMessageNodeFactory;
import som.interpreter.nodes.specialized.IntTimesRepeatLiteralNodeFactory;
import som.interpreter.nodes.specialized.IntToByDoMessageNodeFactory;
import som.interpreter.nodes.specialized.IntToDoInlinedLiteralsNodeFactory;
import som.interpreter.nodes.specialized.IntToDoMessageNodeFactory;
import som.interpreter.nodes.specialized.NotMessageNodeFactory;
import som.interpreter.nodes.specialized.OrMessageNodeFactory;
import som.interpreter.nodes.specialized.whileloops.WhileFalsePrimitiveNodeFactory;
import som.interpreter.nodes.specialized.whileloops.WhileInlinedLiteralsNode;
import som.interpreter.nodes.specialized.whileloops.WhileTruePrimitiveNodeFactory;
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
import som.primitives.FilePrimsFactory;
import som.primitives.HashPrimFactory;
import som.primitives.IntegerPrimsFactory;
import som.primitives.MethodPrimsFactory;
import som.primitives.MirrorPrimsFactory;
import som.primitives.ObjectPrimsFactory;
import som.primitives.ObjectSystemPrimsFactory;
import som.primitives.PathPrimsFactory;
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
import som.primitives.arithmetic.PowPrimFactory;
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
import som.vmobjects.SInvokable;
import som.vmobjects.SSymbol;


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
    assert signature.getNumberOfSignatureArguments() > 1 : "Primitives should have the vmMirror as receiver, "
        + "and then at least one object they are applied to";

    // ignore the implicit vmMirror argument
    final int numArgs = signature.getNumberOfSignatureArguments() - 1;

    Source s = SomLanguage.getSyntheticSource("primitive", specializer.getName());
    MethodBuilder prim = new MethodBuilder(true, lang, null);
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
        prim.getScope().getFrameDescriptor(),
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
