package som.vm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.dsl.GeneratedBy;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

import som.compiler.AccessModifier;
import som.compiler.MethodBuilder;
import som.interpreter.Primitive;
import som.interpreter.SomLanguage;
import som.interpreter.actors.ResolvePromiseNodeFactory;
import som.interpreter.nodes.ArgumentReadNode.LocalArgumentReadNode;
import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.dispatch.Dispatchable;
import som.interpreter.nodes.specialized.AndMessageNodeFactory;
import som.interpreter.nodes.specialized.NotMessageNodeFactory;
import som.interpreter.nodes.specialized.whileloops.WhilePrimitiveNodeFactory;
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
import som.primitives.MethodPrimsFactory.InvokeOnPrimFactory;
import som.primitives.MirrorPrimsFactory;
import som.primitives.ObjectPrimsFactory;
import som.primitives.ObjectPrimsFactory.IsValueFactory;
import som.primitives.ObjectSystemPrimsFactory;
import som.primitives.SizeAndLengthPrimFactory;
import som.primitives.StringPrimsFactory;
import som.primitives.SystemPrimsFactory;
import som.primitives.UnequalsPrimFactory;
import som.primitives.actors.ActorClassesFactory;
import som.primitives.actors.CreateActorPrimFactory;
import som.primitives.actors.PromisePrimsFactory;
import som.primitives.arithmetic.AdditionPrimFactory;
import som.primitives.arithmetic.DividePrimFactory;
import som.primitives.arithmetic.DoubleDivPrimFactory;
import som.primitives.arithmetic.ExpPrimFactory;
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
import som.primitives.arrays.DoIndexesPrimFactory;
import som.primitives.arrays.NewPrimFactory;
import som.primitives.arrays.PutAllNodeFactory;
import som.primitives.arrays.ToArgumentsArrayNodeGen;
import som.primitives.bitops.BitAndPrimFactory;
import som.primitives.bitops.BitXorPrimFactory;
import som.vmobjects.SInvokable;
import som.vmobjects.SSymbol;

public class Primitives {
  private static som.primitives.Primitive getPrimitiveAnnotation(
      final NodeFactory<? extends ExpressionNode> primFact) {
    GeneratedBy[] genAnnotation = primFact.getClass().getAnnotationsByType(
        GeneratedBy.class);

    assert genAnnotation.length == 1 :
      "We don't support more than one @Primitive annotation";

    Class<?> nodeClass = genAnnotation[0].value();
    som.primitives.Primitive[] ann = nodeClass.getAnnotationsByType(
        som.primitives.Primitive.class);
    som.primitives.Primitive prim;
    if (ann.length == 1) {
      prim = ann[0];
    } else {
      prim = null;
      assert ann.length == 0;
    }
    return prim;
  }

  private static SInvokable constructVmMirrorPrimitive(
      final SSymbol signature,
      final som.primitives.Primitive primitive,
      final NodeFactory<? extends ExpressionNode> factory) {
    CompilerAsserts.neverPartOfCompilation("This is only executed during bootstrapping.");
    assert signature.getNumberOfSignatureArguments() > 1 :
      "Primitives should have the vmMirror as receiver, " +
      "and then at least one object they are applied to";

    // ignore the implicit vmMirror argument
    final int numArgs = signature.getNumberOfSignatureArguments() - 1;

    Source s = SomLanguage.getSyntheticSource("primitive", factory.getClass().getSimpleName());
    MethodBuilder prim = new MethodBuilder(true);
    ExpressionNode[] args = new ExpressionNode[numArgs];

    for (int i = 0; i < numArgs; i++) {
      // we do not pass the vmMirror, makes it easier to use the same primitives
      // as replacements on the node level
      args[i] = new LocalArgumentReadNode(i + 1, s.createSection(null, 1));
    }

    ExpressionNode primNode;
    SourceSection source = s.createSection(null, 1);
    switch (numArgs) {
      case 1:
        primNode = factory.createNode(source, args[0]);
        break;
      case 2:
        // HACK for node class where we use `executeWith`
        if (factory == PutAllNodeFactory.getInstance()) {
          primNode = factory.createNode(source, args[0], args[1],
              SizeAndLengthPrimFactory.create(null, null));
//        } else if (factory == SpawnWithArgsPrimFactory.getInstance()) {
//          primNode = factory.createNode(args[0], args[1],
//              ToArgumentsArrayNodeGen.create(null, null));
        } else if (factory == CreateActorPrimFactory.getInstance()) {
          primNode = factory.createNode(source, args[0], args[1],
              IsValueFactory.create(null, null));
        } else {
          primNode = factory.createNode(source, args[0], args[1]);
        }
        break;
      case 3:
        // HACK for node class where we use `executeWith`
        if (factory == InvokeOnPrimFactory.getInstance()) {
          primNode = factory.createNode(source, args[0], args[1], args[2],
              ToArgumentsArrayNodeGen.create(null, null));
        } else {
          primNode = factory.createNode(source, args[0], args[1], args[2]);
        }
        break;
      case 4:
        primNode = factory.createNode(args[0], args[1], args[2], args[3]);
        break;
      default:
        throw new RuntimeException("Not supported by SOM.");
    }

    String name = "vmMirror>>" + signature.toString();

    Primitive primMethodNode = new Primitive(name, primNode,
        prim.getCurrentMethodScope().getFrameDescriptor(),
        (ExpressionNode) primNode.deepCopy());
    return new SInvokable(signature, AccessModifier.PUBLIC, null,
        primMethodNode, null);
  }

  public static HashMap<SSymbol, Dispatchable> constructVmMirrorPrimitives() {
    HashMap<SSymbol, Dispatchable> primitives = new HashMap<>();

    List<NodeFactory<? extends ExpressionNode>> primFacts = getFactories();
    for (NodeFactory<? extends ExpressionNode> primFact : primFacts) {
      som.primitives.Primitive prim = getPrimitiveAnnotation(primFact);
      if (prim != null) {
        for (String sig : prim.value()) {
          SSymbol signature = Symbols.symbolFor(sig);
          primitives.put(signature,
              constructVmMirrorPrimitive(signature, prim, primFact));
        }
      }
    }

    return primitives;
  }

  private static List<NodeFactory<? extends ExpressionNode>> getFactories() {
    List<NodeFactory<? extends ExpressionNode>> allFactories = new ArrayList<>();
    allFactories.addAll(AndMessageNodeFactory.getFactories());
    allFactories.addAll(WhilePrimitiveNodeFactory.getFactories());
    allFactories.addAll(BlockPrimsFactory.getFactories());
    allFactories.addAll(ClassPrimsFactory.getFactories());
    allFactories.addAll(DoublePrimsFactory.getFactories());
    allFactories.addAll(IntegerPrimsFactory.getFactories());
    allFactories.addAll(MethodPrimsFactory.getFactories());
    allFactories.addAll(ObjectPrimsFactory.getFactories());
    allFactories.addAll(StringPrimsFactory.getFactories());
    allFactories.addAll(SystemPrimsFactory.getFactories());
    allFactories.addAll(ObjectSystemPrimsFactory.getFactories());
    allFactories.addAll(MirrorPrimsFactory.getFactories());
    allFactories.addAll(ExceptionsPrimsFactory.getFactories());
    allFactories.addAll(ActorClassesFactory.getFactories());
    allFactories.addAll(PromisePrimsFactory.getFactories());

    allFactories.add(EqualsEqualsPrimFactory.getInstance());
    allFactories.add(EqualsPrimFactory.getInstance());
    allFactories.add(NotMessageNodeFactory.getInstance());
    allFactories.add(AsStringPrimFactory.getInstance());
    allFactories.add(HashPrimFactory.getInstance());
    allFactories.add(SizeAndLengthPrimFactory.getInstance());
    allFactories.add(UnequalsPrimFactory.getInstance());
    allFactories.add(AdditionPrimFactory.getInstance());
    allFactories.add(BitXorPrimFactory.getInstance());
    allFactories.add(BitAndPrimFactory.getInstance());
    allFactories.add(DividePrimFactory.getInstance());
    allFactories.add(DoubleDivPrimFactory.getInstance());
    allFactories.add(LessThanPrimFactory.getInstance());
    allFactories.add(ModuloPrimFactory.getInstance());
    allFactories.add(MultiplicationPrimFactory.getInstance());
    allFactories.add(RemainderPrimFactory.getInstance());
    allFactories.add(ExpPrimFactory.getInstance());
    allFactories.add(LogPrimFactory.getInstance());
    allFactories.add(CosPrimFactory.getInstance());
    allFactories.add(SinPrimFactory.getInstance());
    allFactories.add(SqrtPrimFactory.getInstance());
    allFactories.add(SubtractionPrimFactory.getInstance());
    allFactories.add(AtPrimFactory.getInstance());
    allFactories.add(AtPutPrimFactory.getInstance());
    allFactories.add(DoIndexesPrimFactory.getInstance());
    allFactories.add(NewPrimFactory.getInstance());
    allFactories.add(PutAllNodeFactory.getInstance());

    allFactories.add(CreateActorPrimFactory.getInstance());
    allFactories.add(ResolvePromiseNodeFactory.getInstance());

    return allFactories;
  }
}
