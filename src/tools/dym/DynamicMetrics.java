package tools.dym;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.ExecutionEventNode;
import com.oracle.truffle.api.instrumentation.ExecutionEventNodeFactory;
import com.oracle.truffle.api.instrumentation.Instrumenter;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter.Builder;
import com.oracle.truffle.api.instrumentation.StandardTags.RootTag;
import com.oracle.truffle.api.instrumentation.StandardTags.StatementTag;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.instrumentation.TruffleInstrument.Registration;
import com.oracle.truffle.api.nodes.GraphPrintVisitor;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;

import som.compiler.MixinDefinition;
import som.instrumentation.InstrumentableDirectCallNode;
import som.instrumentation.InstrumentableDirectCallNode.InstrumentableBlockApplyNode;
import som.interpreter.Invokable;
import som.interpreter.nodes.OperationNode;
import som.interpreter.nodes.dispatch.Dispatchable;
import som.vm.NotYetImplementedException;
import som.vmobjects.SInvokable;
import tools.dym.Tags.BasicPrimitiveOperation;
import tools.dym.Tags.CachedClosureInvoke;
import tools.dym.Tags.CachedVirtualInvoke;
import tools.dym.Tags.ClassRead;
import tools.dym.Tags.ComplexPrimitiveOperation;
import tools.dym.Tags.ControlFlowCondition;
import tools.dym.Tags.FieldRead;
import tools.dym.Tags.FieldWrite;
import tools.dym.Tags.LocalArgRead;
import tools.dym.Tags.LocalVarRead;
import tools.dym.Tags.LocalVarWrite;
import tools.dym.Tags.LoopBody;
import tools.dym.Tags.LoopNode;
import tools.dym.Tags.NewArray;
import tools.dym.Tags.NewObject;
import tools.dym.Tags.OpClosureApplication;
import tools.dym.Tags.PrimitiveArgument;
import tools.dym.Tags.VirtualInvoke;
import tools.dym.Tags.VirtualInvokeReceiver;
import tools.dym.nodes.AllocationProfilingNode;
import tools.dym.nodes.ArrayAllocationProfilingNode;
import tools.dym.nodes.CallTargetNode;
import tools.dym.nodes.ClosureTargetNode;
import tools.dym.nodes.ControlFlowProfileNode;
import tools.dym.nodes.CountingNode;
import tools.dym.nodes.InvocationProfilingNode;
import tools.dym.nodes.LateCallTargetNode;
import tools.dym.nodes.LateClosureTargetNode;
import tools.dym.nodes.LateReportResultNode;
import tools.dym.nodes.LoopIterationReportNode;
import tools.dym.nodes.LoopProfilingNode;
import tools.dym.nodes.OperationProfilingNode;
import tools.dym.nodes.ReadProfilingNode;
import tools.dym.nodes.ReportReceiverNode;
import tools.dym.nodes.ReportResultNode;
import tools.dym.profiles.AllocationProfile;
import tools.dym.profiles.ArrayCreationProfile;
import tools.dym.profiles.BranchProfile;
import tools.dym.profiles.CallsiteProfile;
import tools.dym.profiles.ClosureApplicationProfile;
import tools.dym.profiles.Counter;
import tools.dym.profiles.InvocationProfile;
import tools.dym.profiles.LoopProfile;
import tools.dym.profiles.OperationProfile;
import tools.dym.profiles.ReadValueProfile;
import tools.dym.profiles.StructuralProbe;
import tools.highlight.Tags.LiteralTag;


/**
 * DynamicMetric is a Truffle instrumentation tool to measure a wide range of
 * dynamic metrics to characterize the behavior of executing code.
 *
 * WARNING:
 *   - designed for single-threaded use only
 *   - designed for use in interpreted mode only
 */
@Registration(id = DynamicMetrics.ID)
public class DynamicMetrics extends TruffleInstrument {

  public static final String ID = "dym-dynamic-metrics";

  private int methodStackDepth;
  private int maxStackDepth;

  private final Map<SourceSection, InvocationProfile>    methodInvocationCounter;
  private final Map<SourceSection, CallsiteProfile>      methodCallsiteProfiles;
  private final Map<SourceSection, ClosureApplicationProfile> closureProfiles;
  private final Map<SourceSection, OperationProfile>     operationProfiles;

  private final Map<SourceSection, AllocationProfile>    newObjectCounter;
  private final Map<SourceSection, ArrayCreationProfile> newArrayCounter;

  private final Map<SourceSection, BranchProfile>        controlFlowProfiles;
  private final Map<SourceSection, LoopProfile>          loopProfiles;

  private final Map<SourceSection, ReadValueProfile>     fieldReadProfiles;
  private final Map<SourceSection, Counter>              fieldWriteProfiles;
  private final Map<SourceSection, Counter>              classReadProfiles;
  private final Map<SourceSection, Counter>              literalReadCounter;
  private final Map<SourceSection, ReadValueProfile>     localsReadProfiles;
  private final Map<SourceSection, Counter>              localsWriteProfiles;

  private final StructuralProbe structuralProbe;

  private final Set<RootNode> rootNodes;

  @CompilationFinal private static Instrumenter instrumenter; // TODO: this is one of those evil hacks

  public static boolean isTaggedWith(final Node node, final Class<?> tag) {
    assert instrumenter != null : "Initialization order/dependencies?";
    return instrumenter.isTaggedWith(node, tag);
  }

  public DynamicMetrics() {
    structuralProbe = new StructuralProbe();

    methodInvocationCounter = new HashMap<>();
    methodCallsiteProfiles  = new HashMap<>();
    closureProfiles         = new HashMap<>();
    operationProfiles       = new HashMap<>();

    newObjectCounter        = new HashMap<>();
    newArrayCounter         = new HashMap<>();

    controlFlowProfiles     = new HashMap<>();
    loopProfiles            = new HashMap<>();

    fieldReadProfiles       = new HashMap<>();
    fieldWriteProfiles      = new HashMap<>();
    classReadProfiles       = new HashMap<>();
    literalReadCounter      = new HashMap<>();
    localsReadProfiles      = new HashMap<>();
    localsWriteProfiles     = new HashMap<>();

    rootNodes = new HashSet<>();

    assert "DefaultTruffleRuntime".equals(
        Truffle.getRuntime().getClass().getSimpleName())
        : "To get metrics for the lexical, unoptimized behavior, please run this tool without Graal";
  }

  public void enterMethod() {
    methodStackDepth += 1;
    maxStackDepth = Math.max(methodStackDepth, maxStackDepth);
    assert methodStackDepth > 0;
  }

  public void leaveMethod() {
    methodStackDepth -= 1;
    assert methodStackDepth >= 0;
  }

  private <N extends ExecutionEventNode, PRO extends Counter>
    ExecutionEventNodeFactory addInstrumentation(final Instrumenter instrumenter,
        final Map<SourceSection, PRO> storageMap,
        final Class<?>[] tagsIs,
        final Class<?>[] tagsIsNot,
        final Function<SourceSection, PRO> pCtor,
        final Function<PRO, N> nCtor) {
    Builder filters = SourceSectionFilter.newBuilder();
    if (tagsIs != null && tagsIs.length > 0) {
      filters.tagIs(tagsIs);
    }
    if (tagsIsNot != null && tagsIsNot.length > 0) {
      filters.tagIsNot(tagsIsNot);
    }

    ExecutionEventNodeFactory factory = (final EventContext ctx) -> {
      PRO p = storageMap.computeIfAbsent(ctx.getInstrumentedSourceSection(), pCtor);
      return nCtor.apply(p);
    };

    instrumenter.attachFactory(filters.build(), factory);
    return factory;
  }

  private void addRootTagInstrumentation(final Instrumenter instrumenter) {
    Builder filters = SourceSectionFilter.newBuilder();
    filters.tagIs(RootTag.class);
    instrumenter.attachFactory(filters.build(), (final EventContext ctx) -> {
      RootNode root = ctx.getInstrumentedNode().getRootNode();
      assert root instanceof Invokable : "TODO: make language independent";
      InvocationProfile p = methodInvocationCounter.computeIfAbsent(
          ctx.getInstrumentedSourceSection(), ss -> new InvocationProfile(ss, (Invokable) root));
      return new InvocationProfilingNode(this, p);
    });
  }

  private static int numberOfChildren(final Node node) {
    int i = 0;
    for (@SuppressWarnings("unused") Node child : node.getChildren()) {
      i += 1;
    }
    return i;
  }

  private static String getOperation(final Node node) {
    if (node instanceof OperationNode) {
      return ((OperationNode) node).getOperation();
    }
    throw new NotYetImplementedException();
  }

  private ExecutionEventNodeFactory addOperationInstrumentation(
      final Instrumenter instrumenter) {
    Builder filters = SourceSectionFilter.newBuilder();
    filters.tagIs(ComplexPrimitiveOperation.class, BasicPrimitiveOperation.class);

    ExecutionEventNodeFactory primExpFactory = (final EventContext ctx) -> {
      int numSubExpr = numberOfChildren(ctx.getInstrumentedNode());
      String operation = getOperation(ctx.getInstrumentedNode());
      Set<Class<?>> tags = instrumenter.queryTags(ctx.getInstrumentedNode());

      OperationProfile p = operationProfiles.computeIfAbsent(
        ctx.getInstrumentedSourceSection(),
        (final SourceSection src) -> new OperationProfile(
            src, operation, tags, numSubExpr));
      return new OperationProfilingNode(p, ctx);
    };

    instrumenter.attachFactory(filters.build(), primExpFactory);
    return primExpFactory;
  }

  private void addSubexpressionInstrumentation(final Instrumenter instrumenter,
      final ExecutionEventNodeFactory factory) {
    Builder filters = SourceSectionFilter.newBuilder();
    filters.tagIs(PrimitiveArgument.class);

    instrumenter.attachFactory(filters.build(), (final EventContext ctx) -> {
      ExecutionEventNode parent = ctx.findDirectParentEventNode(factory);

      if (parent == null) {
        return new LateReportResultNode(ctx, factory);
      }

      OperationProfilingNode p = (OperationProfilingNode) parent;
      int idx = p.registerSubexpressionAndGetIdx(ctx.getInstrumentedNode());
      return new ReportResultNode(p.getProfile(), idx);
    });
  }

  private void addReceiverInstrumentation(final Instrumenter instrumenter,
      final ExecutionEventNodeFactory virtInvokeFactory) {
    Builder filters = SourceSectionFilter.newBuilder();
    filters.tagIs(VirtualInvokeReceiver.class);

    instrumenter.attachFactory(filters.build(), (final EventContext ctx) -> {
      ExecutionEventNode parent = ctx.findDirectParentEventNode(virtInvokeFactory);

      @SuppressWarnings("unchecked")
      CountingNode<CallsiteProfile> p = (CountingNode<CallsiteProfile>) parent;
      CallsiteProfile profile = p.getProfile();
      return new ReportReceiverNode(profile);
    });
  }

  private void addCalltargetInstrumentation(final Instrumenter instrumenter,
      final ExecutionEventNodeFactory virtInvokeFactory) {
    Builder filters = SourceSectionFilter.newBuilder();
    filters.tagIs(CachedVirtualInvoke.class);

    instrumenter.attachFactory(filters.build(), (final EventContext ctx) -> {
      ExecutionEventNode parent = ctx.findParentEventNode(virtInvokeFactory);
      InstrumentableDirectCallNode disp = (InstrumentableDirectCallNode) ctx.getInstrumentedNode();

      if (parent == null) {
        return new LateCallTargetNode(ctx, virtInvokeFactory);
      }

      @SuppressWarnings("unchecked")
      CountingNode<CallsiteProfile> p = (CountingNode<CallsiteProfile>) parent;
      CallsiteProfile profile = p.getProfile();
      RootCallTarget root = (RootCallTarget) disp.getCallTarget();
      return new CallTargetNode(profile, (Invokable) root.getRootNode());
    });
  }

  private void addClosureTargetInstrumentation(final Instrumenter instrumenter,
      final ExecutionEventNodeFactory factory) {
    Builder filters = SourceSectionFilter.newBuilder();
    filters.tagIs(CachedClosureInvoke.class);

    instrumenter.attachFactory(filters.build(), (final EventContext ctx) -> {
      ExecutionEventNode parent = ctx.findParentEventNode(factory);
      InstrumentableBlockApplyNode disp = (InstrumentableBlockApplyNode) ctx.getInstrumentedNode();

      if (parent == null) {
        return new LateClosureTargetNode(ctx, factory);
      }

      @SuppressWarnings("unchecked")
      CountingNode<ClosureApplicationProfile> p = (CountingNode<ClosureApplicationProfile>) parent;
      ClosureApplicationProfile profile = p.getProfile();
      RootCallTarget root = (RootCallTarget) disp.getCallTarget();
      return new ClosureTargetNode(profile, (Invokable) root.getRootNode());
    });
  }

  private static final Class<?>[] NO_TAGS = new Class<?>[0];

  @Override
  protected void onCreate(final Env env) {
    instrumenter = env.getInstrumenter();

    addRootTagInstrumentation(instrumenter);

    ExecutionEventNodeFactory virtInvokeFactory = addInstrumentation(
        instrumenter, methodCallsiteProfiles,
        new Class<?>[] {VirtualInvoke.class}, NO_TAGS,
        CallsiteProfile::new, CountingNode<CallsiteProfile>::new);
    addReceiverInstrumentation(instrumenter, virtInvokeFactory);
    addCalltargetInstrumentation(instrumenter, virtInvokeFactory);

    ExecutionEventNodeFactory closureApplicationFactory = addInstrumentation(
        instrumenter, closureProfiles,
        new Class<?>[] {OpClosureApplication.class}, NO_TAGS,
        ClosureApplicationProfile::new, CountingNode<ClosureApplicationProfile>::new);
    addClosureTargetInstrumentation(instrumenter, closureApplicationFactory);

    addInstrumentation(instrumenter, newObjectCounter,
        new Class<?>[] {NewObject.class}, NO_TAGS,
        AllocationProfile::new, AllocationProfilingNode::new);
    addInstrumentation(instrumenter, newArrayCounter,
        new Class<?>[] {NewArray.class}, NO_TAGS,
        ArrayCreationProfile::new, ArrayAllocationProfilingNode::new);

    addInstrumentation(instrumenter, literalReadCounter,
        new Class<?>[] {LiteralTag.class}, NO_TAGS,
        Counter::new, CountingNode<Counter>::new);

    ExecutionEventNodeFactory opInstrumentFact = addOperationInstrumentation(instrumenter);
    addSubexpressionInstrumentation(instrumenter, opInstrumentFact);

    addInstrumentation(instrumenter, fieldReadProfiles,
        new Class<?>[] {FieldRead.class}, NO_TAGS,
        ReadValueProfile::new,
        ReadProfilingNode::new);
    addInstrumentation(instrumenter, localsReadProfiles,
        new Class<?>[] {LocalArgRead.class, LocalVarRead.class}, NO_TAGS,
        ReadValueProfile::new, ReadProfilingNode::new);

    addInstrumentation(instrumenter, fieldWriteProfiles,
        new Class<?>[] {FieldWrite.class}, NO_TAGS,
        Counter::new, CountingNode<Counter>::new);
    addInstrumentation(instrumenter, classReadProfiles,
        new Class<?>[] {ClassRead.class}, NO_TAGS,
        Counter::new, CountingNode<Counter>::new);
    addInstrumentation(instrumenter, localsWriteProfiles,
        new Class<?>[] {LocalVarWrite.class}, NO_TAGS,
        Counter::new, CountingNode<Counter>::new);

    addInstrumentation(instrumenter, controlFlowProfiles,
        new Class<?>[] {ControlFlowCondition.class}, NO_TAGS,
        BranchProfile::new, ControlFlowProfileNode::new);

    ExecutionEventNodeFactory loopProfileFactory = addInstrumentation(instrumenter, loopProfiles,
        new Class<?>[] {LoopNode.class}, NO_TAGS,
        LoopProfile::new, LoopProfilingNode::new);

    addLoopBodyInstrumentation(instrumenter, loopProfileFactory);

    instrumenter.attachLoadSourceSectionListener(
        SourceSectionFilter.newBuilder().tagIs(RootTag.class).build(),
        e -> rootNodes.add(e.getNode().getRootNode()),
        true);

    env.registerService(structuralProbe);
  }

  private void addLoopBodyInstrumentation(
      final Instrumenter instrumenter,
      final ExecutionEventNodeFactory loopProfileFactory) {
    Builder filters = SourceSectionFilter.newBuilder();
    filters.tagIs(LoopBody.class);

    instrumenter.attachFactory(filters.build(), (final EventContext ctx) -> {
      ExecutionEventNode parent = ctx.findDirectParentEventNode(loopProfileFactory);
      assert parent != null : "Direct parent does not seem to be set up properly with event node and/or wrapping";
      LoopProfilingNode p = (LoopProfilingNode) parent;
      return new LoopIterationReportNode(p.getProfile());
    });
  }

  @Override
  protected void onDispose(final Env env) {
    String outputFile = System.getProperty("dm.output", "dynamic-metrics.json");
    Map<String, Map<SourceSection, ? extends JsonSerializable>> data = collectData();
    JsonWriter.fileOut(data, outputFile);

    String metricsFolder = System.getProperty("dm.metrics", "metrics");
    MetricsCsvWriter.fileOut(data, metricsFolder, structuralProbe,
        maxStackDepth, getAllStatementsAlsoNotExecuted());

    outputAllTruffleMethodsToIGV();
  }

  private List<SourceSection> getAllStatementsAlsoNotExecuted() {
    List<SourceSection> allSourceSections = new ArrayList<>();

    for (RootNode root : rootNodes) {
      Map<SourceSection, Set<Class<?>>> sourceSectionsAndTags = new HashMap<>();

      root.accept(node -> {
        Set<Class<?>> tags = instrumenter.queryTags(node);

        if (tags.contains(StatementTag.class)) {
          if (sourceSectionsAndTags.containsKey(node.getSourceSection())) {
            sourceSectionsAndTags.get(node.getSourceSection()).addAll(tags);
          } else {
            sourceSectionsAndTags.put(node.getSourceSection(),
                new HashSet<>(tags));
          }
        }
        return true;
      });

      allSourceSections.addAll(sourceSectionsAndTags.keySet());
    }

    return allSourceSections;
  }

  private void outputAllTruffleMethodsToIGV() {
    GraphPrintVisitor graphPrinter = new GraphPrintVisitor();

    List<MixinDefinition> classes = new ArrayList<MixinDefinition>(structuralProbe.getClasses());
    Collections.sort(classes, (final MixinDefinition a, final MixinDefinition b) -> a.getName().getString().compareTo(b.getName().getString()));

    for (MixinDefinition mixin : classes) {
      graphPrinter.beginGroup(mixin.getName().getString());

      for (Dispatchable disp : mixin.getInstanceDispatchables().values()) {
        if (disp instanceof SInvokable) {
          SInvokable i = (SInvokable) disp;
          graphPrinter.beginGraph(i.toString()).visit(i.getCallTarget().getRootNode());
        }
      }
      graphPrinter.endGroup();
    }

    graphPrinter.printToNetwork(true);
    graphPrinter.close();
  }

  private Map<String, Map<SourceSection, ? extends JsonSerializable>> collectData() {
    Map<String, Map<SourceSection, ? extends JsonSerializable>> data = new HashMap<>();
    data.put(JsonWriter.METHOD_INVOCATION_PROFILE, methodInvocationCounter);
    data.put(JsonWriter.METHOD_CALLSITE,          methodCallsiteProfiles);
    data.put(JsonWriter.CLOSURE_APPLICATIONS,     closureProfiles);
    data.put(JsonWriter.NEW_OBJECT_COUNT,         newObjectCounter);
    data.put(JsonWriter.NEW_ARRAY_COUNT,          newArrayCounter);
    data.put(JsonWriter.FIELD_READS,              fieldReadProfiles);
    data.put(JsonWriter.FIELD_WRITES,             fieldWriteProfiles);
    data.put(JsonWriter.CLASS_READS,              classReadProfiles);
    data.put(JsonWriter.BRANCH_PROFILES,          controlFlowProfiles);
    data.put(JsonWriter.LITERAL_READS,            literalReadCounter);
    data.put(JsonWriter.LOCAL_READS,              localsReadProfiles);
    data.put(JsonWriter.LOCAL_WRITES,             localsWriteProfiles);
    data.put(JsonWriter.OPERATIONS,               operationProfiles);
    data.put(JsonWriter.LOOPS,                    loopProfiles);
    return data;
  }

}
