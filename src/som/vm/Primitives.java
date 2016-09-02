package som.vm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

import som.VmSettings;
import som.compiler.AccessModifier;
import som.compiler.MethodBuilder;
import som.interpreter.Primitive;
import som.interpreter.SomLanguage;
import som.interpreter.actors.ResolvePromiseNodeFactory;
import som.interpreter.nodes.ArgumentReadNode.LocalArgumentReadNode;
import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.dispatch.Dispatchable;
import som.interpreter.nodes.specialized.AndMessageNodeFactory;
import som.interpreter.nodes.specialized.IfMessageNodeFactory;
import som.interpreter.nodes.specialized.IntToByDoMessageNodeFactory;
import som.interpreter.nodes.specialized.NotMessageNodeFactory;
import som.interpreter.nodes.specialized.whileloops.WhilePrimitiveNodeFactory;
import som.interpreter.nodes.specialized.whileloops.WhileWithStaticBlocksNode.WhileWithStaticBlocksNodeFactory;
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
import som.primitives.arrays.ToArgumentsArrayNodeGen;
import som.primitives.bitops.BitAndPrimFactory;
import som.primitives.bitops.BitXorPrimFactory;
import som.vmobjects.SInvokable;
import som.vmobjects.SSymbol;

public class Primitives {
  private HashMap<SSymbol, Dispatchable> vmMirrorPrimitives;
  private HashMap<SSymbol, PrimAndFact>  eagerPrimitives;

  public static class PrimAndFact {
    private final som.primitives.Primitive prim;
    private final NodeFactory<?> fact;
    private final Specializer matcher;

    PrimAndFact(final som.primitives.Primitive prim, final NodeFactory<?> fact,
        final Specializer matcher) {
      this.prim = prim;
      this.fact = fact;
      this.matcher = matcher;
    }

    public NodeFactory<?> getFactory() {
      return fact;
    }

    @SuppressWarnings("unchecked")
    public <T> T createNode(final Object[] arguments,
        final ExpressionNode[] argNodes, final SourceSection section) {
      return (T) matcher.create(fact, arguments, argNodes, section,
          !prim.noWrapper());
    }

    public boolean noWrapper() {
      return prim.noWrapper();
    }
  }

  public Primitives() {
    vmMirrorPrimitives = new HashMap<>();
    eagerPrimitives    = new HashMap<>();
    initialize();
  }

  public PrimAndFact getFactoryForEagerSpecialization(final SSymbol selector,
      final Object receiver, final ExpressionNode[] argumentNodes) {
    PrimAndFact prim = eagerPrimitives.get(selector);
    if (prim != null && prim.matcher.matches(prim.prim, receiver, argumentNodes)) {
      return prim;
    }
    return null;
  }

  private static final Specializer STANDARD_MATCHER = new Specializer();

  public static class Specializer {
    public boolean matches(final som.primitives.Primitive prim,
        final Object receiver, final ExpressionNode[] args) {
      if (prim.disabled() && VmSettings.DYNAMIC_METRICS) {
        return false;
      }

      if (prim.receiverType().length == 0) {
        // no constraints, so, it matches
        return true;
      }

      for (Class<?> c : prim.receiverType()) {
        if (c.isInstance(receiver)) {
          return true;
        }
      }
      return false;
    }

    public <T> T create(final NodeFactory<T> factory, final Object[] arguments,
        final ExpressionNode[] argNodes, final SourceSection section,
        final boolean eagerWrapper) {
      assert arguments.length == argNodes.length;
      Object[] ctorArgs = new Object[arguments.length + 2];
      ctorArgs[0] = eagerWrapper;
      ctorArgs[1] = section;

      for (int i = 0; i < arguments.length; i += 1) {
        ctorArgs[i + 2] = eagerWrapper ? null : argNodes[i];
      }
      return factory.createNode(ctorArgs);
    }
  }

  private static som.primitives.Primitive[] getPrimitiveAnnotation(
      final NodeFactory<? extends ExpressionNode> primFact) {
    Class<?> nodeClass = primFact.getNodeClass();
    return nodeClass.getAnnotationsByType(som.primitives.Primitive.class);
  }

  private static SInvokable constructVmMirrorPrimitive(final SSymbol signature,
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
      args[i] = new LocalArgumentReadNode(i + 1, s.createSection(1));
    }

    ExpressionNode primNode;
    SourceSection source = s.createSection(1);
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
  private void initialize() {
    List<NodeFactory<? extends ExpressionNode>> primFacts = getFactories();
    for (NodeFactory<? extends ExpressionNode> primFact : primFacts) {
      som.primitives.Primitive[] prims = getPrimitiveAnnotation(primFact);
      if (prims != null) {
        for (som.primitives.Primitive prim : prims) {
          String vmMirrorName = prim.primitive();
          if (!("".equals(vmMirrorName))) {
            SSymbol signature = Symbols.symbolFor(vmMirrorName);
            assert !vmMirrorPrimitives.containsKey(signature) : "clash of vmMirrorPrimitive names";
            vmMirrorPrimitives.put(signature,
                constructVmMirrorPrimitive(signature, primFact));
          }

          if (!("".equals(prim.selector()))) {
            SSymbol msgSel = Symbols.symbolFor(prim.selector());
            assert !eagerPrimitives.containsKey(msgSel) : "clash of selectors and eager specialization";
            Specializer matcher;
            if (prim.specializer() == Specializer.class) {
              matcher = STANDARD_MATCHER;
            } else {
              try {
                matcher = prim.specializer().newInstance();
              } catch (InstantiationException | IllegalAccessException e) {
                throw new RuntimeException(e);
              }
            }
            eagerPrimitives.put(msgSel, new PrimAndFact(prim, primFact, matcher));
          }
        }
      }
    }
  }

  private static List<NodeFactory<? extends ExpressionNode>> getFactories() {
    List<NodeFactory<? extends ExpressionNode>> allFactories = new ArrayList<>();
    allFactories.addAll(ActorClassesFactory.getFactories());
    allFactories.addAll(AndMessageNodeFactory.getFactories());
    allFactories.addAll(BlockPrimsFactory.getFactories());
    allFactories.addAll(ClassPrimsFactory.getFactories());
    allFactories.addAll(DoublePrimsFactory.getFactories());
    allFactories.addAll(ExceptionsPrimsFactory.getFactories());
    allFactories.addAll(IntegerPrimsFactory.getFactories());
    allFactories.addAll(MethodPrimsFactory.getFactories());
    allFactories.addAll(MirrorPrimsFactory.getFactories());
    allFactories.addAll(ObjectPrimsFactory.getFactories());
    allFactories.addAll(ObjectSystemPrimsFactory.getFactories());
    allFactories.addAll(PromisePrimsFactory.getFactories());
    allFactories.addAll(StringPrimsFactory.getFactories());
    allFactories.addAll(SystemPrimsFactory.getFactories());
    allFactories.addAll(WhilePrimitiveNodeFactory.getFactories());

    allFactories.add(AdditionPrimFactory.getInstance());
    allFactories.add(AsStringPrimFactory.getInstance());
    allFactories.add(AtPrimFactory.getInstance());
    allFactories.add(AtPutPrimFactory.getInstance());
    allFactories.add(BitAndPrimFactory.getInstance());
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
    allFactories.add(IfMessageNodeFactory.getInstance());
    allFactories.add(IntToByDoMessageNodeFactory.getInstance());
    allFactories.add(LessThanOrEqualPrimFactory.getInstance());
    allFactories.add(LessThanPrimFactory.getInstance());
    allFactories.add(LogPrimFactory.getInstance());
    allFactories.add(ModuloPrimFactory.getInstance());
    allFactories.add(MultiplicationPrimFactory.getInstance());
    allFactories.add(NewPrimFactory.getInstance());
    allFactories.add(NewImmutableArrayNodeFactory.getInstance());
    allFactories.add(NotMessageNodeFactory.getInstance());
    allFactories.add(PutAllNodeFactory.getInstance());
    allFactories.add(RemainderPrimFactory.getInstance());
    allFactories.add(SinPrimFactory.getInstance());
    allFactories.add(SizeAndLengthPrimFactory.getInstance());
    allFactories.add(SqrtPrimFactory.getInstance());
    allFactories.add(SubtractionPrimFactory.getInstance());
    allFactories.add(UnequalsPrimFactory.getInstance());
    allFactories.add(new WhileWithStaticBlocksNodeFactory());

    allFactories.add(CreateActorPrimFactory.getInstance());
    allFactories.add(ResolvePromiseNodeFactory.getInstance());

    return allFactories;
  }
}
