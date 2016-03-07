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
import som.instrumentation.InstrumentableDirectCallNode;
import som.interpreter.Invokable;
import som.interpreter.nodes.OperationNode;
import som.interpreter.nodes.dispatch.Dispatchable;
import som.vm.NotYetImplementedException;
import som.vmobjects.SInvokable;

import com.oracle.truffle.api.RootCallTarget;
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
import dym.nodes.CallTargetNode;
import dym.nodes.ControlFlowProfileNode;
import dym.nodes.CountingNode;
import dym.nodes.InvocationProfilingNode;
import dym.nodes.LateCallTargetNode;
import dym.nodes.LateReportResultNode;
import dym.nodes.LoopIterationReportNode;
import dym.nodes.LoopProfilingNode;
import dym.nodes.OperationProfilingNode;
import dym.nodes.ReadProfilingNode;
import dym.nodes.ReportReceiverNode;
import dym.nodes.ReportResultNode;
import dym.profiles.AllocationProfile;
import dym.profiles.ArrayCreationProfile;
import dym.profiles.BranchProfile;
import dym.profiles.CallsiteProfile;
import dym.profiles.Counter;
import dym.profiles.InvocationProfile;
import dym.profiles.LoopProfile;
import dym.profiles.OperationProfile;
import dym.profiles.ReadValueProfile;
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

  private int methodStackDepth;
  private int maxStackDepth;

  private final Map<SourceSection, InvocationProfile>    methodInvocationCounter;
  private final Map<SourceSection, CallsiteProfile>      methodCallsiteProfiles;
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

  public DynamicMetrics() {
    // TODO: avoid this major hack, there should be some event interface
    //       or a way from the polyglot engine to obtain a reference
    structuralProbe = VM.getStructuralProbe();

    methodInvocationCounter = new HashMap<>();
    methodCallsiteProfiles  = new HashMap<>();
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

  private ExecutionEventNodeFactory addOperationInstrumentation(final Instrumenter instrumenter) {
    Builder filters = SourceSectionFilter.newBuilder();
    filters.tagIs(Tags.COMPLEX_PRIMITIVE_OPERATION, Tags.BASIC_PRIMITIVE_OPERATION);
    filters.tagIsNot(Tags.EAGERLY_WRAPPED);

    ExecutionEventNodeFactory primExpFactory = (final EventContext ctx) -> {
      int numSubExpr = numberOfChildren(ctx.getInstrumentedNode());

      OperationProfile p = operationProfiles.computeIfAbsent(
        ctx.getInstrumentedSourceSection(),
        (final SourceSection src) -> new OperationProfile(src, numSubExpr));
      return new OperationProfilingNode(p, ctx);
    };

    instrumenter.attachFactory(filters.build(), primExpFactory);
    return primExpFactory;
  }

  private void addSubexpressionInstrumentation(final Instrumenter instrumenter,
      final ExecutionEventNodeFactory factory) {
    Builder filters = SourceSectionFilter.newBuilder();
    filters.tagIs(Tags.PRIMITIVE_ARGUMENT);
    filters.tagIsNot(Tags.EAGERLY_WRAPPED);

    instrumenter.attachFactory(filters.build(), (final EventContext ctx) -> {
      ExecutionEventNode parent = ctx.findDirectParentEventNode(factory);

      if (parent == null) {
        return new LateReportResultNode(ctx, factory);
      }

      @SuppressWarnings("unchecked")
      OperationProfilingNode p = (OperationProfilingNode) parent;
      int idx = p.registerSubexpressionAndGetIdx(ctx.getInstrumentedNode());
      return new ReportResultNode(p.getProfile(), idx);
    });
  }

  private void addReceiverInstrumentation(final Instrumenter instrumenter,
      final ExecutionEventNodeFactory virtInvokeFactory) {
    Builder filters = SourceSectionFilter.newBuilder();
    filters.tagIs(Tags.VIRTUAL_INVOKE_RECEIVER);

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
    filters.tagIs(Tags.CACHED_VIRTUAL_INVOKE);

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

  @Override
  protected void onCreate(final Env env) {
    Instrumenter instrumenter = env.getInstrumenter();
    addRootTagInstrumentation(instrumenter);

    ExecutionEventNodeFactory virtInvokeFacoty = addInstrumentation(
        instrumenter, methodCallsiteProfiles,
        new String[] {Tags.VIRTUAL_INVOKE}, new String[] {},
        CallsiteProfile::new, CountingNode<CallsiteProfile>::new);
    addReceiverInstrumentation(instrumenter, virtInvokeFacoty);
    addCalltargetInstrumentation(instrumenter, virtInvokeFacoty);

    addInstrumentation(instrumenter, newObjectCounter,
        new String[] {Tags.NEW_OBJECT}, new String[] {},
        AllocationProfile::new, AllocationProfilingNode::new);
    addInstrumentation(instrumenter, newArrayCounter,
        new String[] {Tags.NEW_ARRAY}, new String[] {},
        ArrayCreationProfile::new, ArrayAllocationProfilingNode::new);

    addInstrumentation(instrumenter, literalReadCounter,
        new String[] {Tags.SYNTAX_LITERAL}, new String[] {},
        Counter::new, CountingNode<Counter>::new);

    ExecutionEventNodeFactory opInstrumentFact = addOperationInstrumentation(instrumenter);
    addSubexpressionInstrumentation(instrumenter, opInstrumentFact);

    addInstrumentation(instrumenter, fieldReadProfiles,
        new String[] {Tags.FIELD_READ}, new String[] {},
        ReadValueProfile::new,
        ReadProfilingNode::new);
    addInstrumentation(instrumenter, localsReadProfiles,
        new String[] {Tags.LOCAL_ARG_READ, Tags.LOCAL_VAR_READ}, new String[] {},
        ReadValueProfile::new, ReadProfilingNode::new);

    addInstrumentation(instrumenter, fieldWriteProfiles,
        new String[] {Tags.FIELD_WRITE}, new String[] {},
        Counter::new, CountingNode<Counter>::new);
    addInstrumentation(instrumenter, classReadProfiles,
        new String[] {Tags.CLASS_READ}, new String[] {},
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

    outputAllTruffleMethodsToIGV();
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
