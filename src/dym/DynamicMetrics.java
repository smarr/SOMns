package dym;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import som.VM;
import som.compiler.MixinDefinition;
import som.compiler.Tags;
import som.interpreter.Invokable;
import som.interpreter.nodes.dispatch.Dispatchable;
import som.vmobjects.SInvokable;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.ExecutionEventNode;
import com.oracle.truffle.api.instrumentation.ExecutionEventNodeFactory;
import com.oracle.truffle.api.instrumentation.Instrumenter;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter.Builder;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.instrumentation.TruffleInstrument.Registration;
import com.oracle.truffle.api.nodes.GraphPrintVisitor;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;

import dym.nodes.AllocationProfilingNode;
import dym.nodes.ArrayAllocationProfilingNode;
import dym.nodes.ControlFlowProfileNode;
import dym.nodes.CountingNode;
import dym.nodes.FieldReadProfilingNode;
import dym.nodes.InvocationProfilingNode;
import dym.nodes.LoopIterationReportNode;
import dym.nodes.LoopProfilingNode;
import dym.nodes.OperationProfilingNode;
import dym.nodes.ReportResultNode;
import dym.profiles.AllocationProfile;
import dym.profiles.ArrayCreationProfile;
import dym.profiles.BranchProfile;
import dym.profiles.Counter;
import dym.profiles.InvocationProfile;
import dym.profiles.LoopProfile;
import dym.profiles.MethodCallsiteProbe;
import dym.profiles.OperationProfile;
import dym.profiles.PrimitiveOperationProfile;
import dym.profiles.ReadValueProfile;
import dym.profiles.RecursiveOperationProfile;
import dym.profiles.StructuralProbe;


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

  private final Map<SourceSection, InvocationProfile> methodInvocationCounter;
  private int methodStackDepth;
  private int maxStackDepth;

  private final Map<SourceSection, MethodCallsiteProbe> methodCallsiteProbes;
  private final Map<SourceSection, AllocationProfile> newObjectCounter;
  private final Map<SourceSection, ArrayCreationProfile> newArrayCounter;
  private final Map<SourceSection, ReadValueProfile> fieldReadProfiles;
  private final Map<SourceSection, Counter> fieldWriteProfiles;
  private final Map<SourceSection, BranchProfile> controlFlowProfiles;
  private final Map<SourceSection, Counter> literalReadCounter;
  private final Map<SourceSection, ReadValueProfile> localsReadProfiles;
  private final Map<SourceSection, Counter> localsWriteProfiles;
  private final Map<SourceSection, PrimitiveOperationProfile> basicOperationCounter;
  private final Map<SourceSection, RecursiveOperationProfile> recursiveOperationCounter;
  private final Map<SourceSection, LoopProfile> loopProfiles;

  private final StructuralProbe structuralProbe;

  public DynamicMetrics() {
    // TODO: avoid this major hack, there should be some event interface
    //       or a way from the polyglot engine to obtain a reference
    structuralProbe = VM.getStructuralProbe();

    methodInvocationCounter = new HashMap<>();
    methodCallsiteProbes    = new HashMap<>();
    newObjectCounter        = new HashMap<>();
    newArrayCounter         = new HashMap<>();
    fieldReadProfiles       = new HashMap<>();
    fieldWriteProfiles      = new HashMap<>();
    controlFlowProfiles     = new HashMap<>();
    literalReadCounter      = new HashMap<>();
    localsReadProfiles      = new HashMap<>();
    localsWriteProfiles     = new HashMap<>();
    basicOperationCounter   = new HashMap<>();
    recursiveOperationCounter = new HashMap<>();
    loopProfiles            = new HashMap<>();

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
        final String[] tagsIs,
        final String[] tagsIsNot,
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
    filters.tagIs(Tags.ROOT_TAG);
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

  private ExecutionEventNodeFactory addPrimitiveInstrumentation(final Instrumenter instrumenter) {
    Builder filters = SourceSectionFilter.newBuilder();
    filters.tagIs(Tags.BASIC_PRIMITIVE_OPERATION);
    filters.tagIsNot(Tags.EAGERLY_WRAPPED);

    ExecutionEventNodeFactory primExpFactory = (final EventContext ctx) -> {
      int numSubExpr = numberOfChildren(ctx.getInstrumentedNode());

      PrimitiveOperationProfile p = basicOperationCounter.computeIfAbsent(
        ctx.getInstrumentedSourceSection(),
        (final SourceSection src) -> new PrimitiveOperationProfile(src, numSubExpr));
      return new OperationProfilingNode<PrimitiveOperationProfile>(p, ctx);
    };

    instrumenter.attachFactory(filters.build(), primExpFactory);
    return primExpFactory;
  }

  private ExecutionEventNodeFactory addRecursivePrimitiveInstrumentation(final Instrumenter instrumenter) {
    Builder filters = SourceSectionFilter.newBuilder();
    filters.tagIs(Tags.COMPLEX_PRIMITIVE_OPERATION);
    filters.tagIsNot(Tags.EAGERLY_WRAPPED);

    ExecutionEventNodeFactory primExpFactory = (final EventContext ctx) -> {
      int numSubExpr = numberOfChildren(ctx.getInstrumentedNode());

      RecursiveOperationProfile p = recursiveOperationCounter.computeIfAbsent(
        ctx.getInstrumentedSourceSection(),
        (final SourceSection src) -> new RecursiveOperationProfile(src, numSubExpr));
      return new OperationProfilingNode<RecursiveOperationProfile>(p, ctx);
    };

    instrumenter.attachFactory(filters.build(), primExpFactory);
    return primExpFactory;
  }

  @SuppressWarnings("unchecked")
  private void addSubexpressionInstrumentation(
      final Instrumenter instrumenter,
      final ExecutionEventNodeFactory factoryA,
      final ExecutionEventNodeFactory factoryB) {
    Builder filters = SourceSectionFilter.newBuilder();
    filters.tagIs(Tags.PRIMITIVE_ARGUMENT);
    filters.tagIsNot(Tags.EAGERLY_WRAPPED);

    instrumenter.attachFactory(filters.build(), (final EventContext ctx) -> {
      ExecutionEventNode parent = ctx.findDirectParentEventNode(factoryA);
      if (parent == null) {
        parent = ctx.findDirectParentEventNode(factoryB);
      }

      OperationProfilingNode<? extends OperationProfile> p = (OperationProfilingNode<?>) parent;
      int idx = p.registerSubexpressionAndGetIdx(ctx.getInstrumentedNode());
      return new ReportResultNode(p.getProfile(), idx);
    });
  }

  @Override
  protected void onCreate(final Env env) {
    Instrumenter instrumenter = env.getInstrumenter();
    addRootTagInstrumentation(instrumenter);

    addInstrumentation(instrumenter, methodCallsiteProbes,
        new String[] {Tags.UNSPECIFIED_INVOKE}, new String[] {Tags.EAGERLY_WRAPPED},
        MethodCallsiteProbe::new, CountingNode<Counter>::new);

    addInstrumentation(instrumenter, newObjectCounter,
        new String[] {Tags.NEW_OBJECT}, new String[] {},
        AllocationProfile::new, AllocationProfilingNode::new);
    addInstrumentation(instrumenter, newArrayCounter,
        new String[] {Tags.NEW_ARRAY}, new String[] {},
        ArrayCreationProfile::new, ArrayAllocationProfilingNode::new);

    addInstrumentation(instrumenter, literalReadCounter,
        new String[] {Tags.SYNTAX_LITERAL}, new String[] {},
        Counter::new, CountingNode<Counter>::new);

    ExecutionEventNodeFactory basicPrimInstrFact = addPrimitiveInstrumentation(instrumenter);
    ExecutionEventNodeFactory recPrimInstrFact   = addRecursivePrimitiveInstrumentation(instrumenter);
    addSubexpressionInstrumentation(instrumenter, basicPrimInstrFact, recPrimInstrFact);

    addInstrumentation(instrumenter, fieldReadProfiles,
        new String[] {Tags.FIELD_READ}, new String[] {},
        ReadValueProfile::new,
        FieldReadProfilingNode::new);
    addInstrumentation(instrumenter, localsReadProfiles,
        new String[] {Tags.LOCAL_ARG_READ, Tags.LOCAL_VAR_READ}, new String[] {},
        ReadValueProfile::new, FieldReadProfilingNode::new);

    addInstrumentation(instrumenter, fieldWriteProfiles,
        new String[] {Tags.FIELD_WRITE}, new String[] {},
        Counter::new, CountingNode<Counter>::new);
    addInstrumentation(instrumenter, localsWriteProfiles,
        new String[] {Tags.LOCAL_VAR_WRITE}, new String[] {},
        Counter::new, CountingNode<Counter>::new);

    addInstrumentation(instrumenter, controlFlowProfiles,
        new String[] {Tags.CONTROL_FLOW_CONDITION}, new String[] {},
        BranchProfile::new, ControlFlowProfileNode::new);

    ExecutionEventNodeFactory loopProfileFactory = addInstrumentation(instrumenter, loopProfiles,
        new String[] {Tags.LOOP_NODE}, new String[] {},
        LoopProfile::new, LoopProfilingNode::new);

    addLoopBodyInstrumentation(instrumenter, loopProfileFactory);
  }

  private void addLoopBodyInstrumentation(
      final Instrumenter instrumenter,
      final ExecutionEventNodeFactory loopProfileFactory) {
    Builder filters = SourceSectionFilter.newBuilder();
    filters.tagIs(Tags.LOOP_BODY);

    instrumenter.attachFactory(filters.build(), (final EventContext ctx) -> {
      ExecutionEventNode parent = ctx.findDirectParentEventNode(loopProfileFactory);
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
    MetricsCsvWriter.fileOut(data, metricsFolder, structuralProbe);

  }

  private Map<String, Map<SourceSection, ? extends JsonSerializable>> collectData() {
    Map<String, Map<SourceSection, ? extends JsonSerializable>> data = new HashMap<>();
    data.put(JsonWriter.METHOD_INVOCATION_PROFILE, methodInvocationCounter);
    data.put(JsonWriter.METHOD_CALLSITE,          methodCallsiteProbes);
    data.put(JsonWriter.NEW_OBJECT_COUNT,         newObjectCounter);
    data.put(JsonWriter.NEW_ARRAY_COUNT,          newArrayCounter);
    data.put(JsonWriter.FIELD_READS,              fieldReadProfiles);
    data.put(JsonWriter.FIELD_WRITES,             fieldWriteProfiles);
    data.put(JsonWriter.BRANCH_PROFILES,          controlFlowProfiles);
    data.put(JsonWriter.LITERAL_READS,            literalReadCounter);
    data.put(JsonWriter.LOCAL_READS,              localsReadProfiles);
    data.put(JsonWriter.LOCAL_WRITES,             localsWriteProfiles);
    data.put(JsonWriter.BASIC_OPERATIONS,         basicOperationCounter);
    data.put(JsonWriter.LOOPS,                    loopProfiles);
    return data;
  }

}
