package tools.dym;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

import org.graalvm.collections.EconomicSet;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Instrument;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.InstrumentInfo;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.ExecutionEventNode;
import com.oracle.truffle.api.instrumentation.ExecutionEventNodeFactory;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.Instrumenter;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter.Builder;
import com.oracle.truffle.api.instrumentation.StandardTags.RootTag;
import com.oracle.truffle.api.instrumentation.StandardTags.StatementTag;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.instrumentation.TruffleInstrument.Registration;
import com.oracle.truffle.api.nodes.GraphPrintVisitor;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;

import bd.tools.nodes.Operation;
import bd.tools.structure.StructuralProbe;
import som.compiler.MixinDefinition;
import som.compiler.MixinDefinition.SlotDefinition;
import som.compiler.Variable;
import som.interpreter.Invokable;
import som.interpreter.nodes.dispatch.Dispatchable;
import som.vm.NotYetImplementedException;
import som.vm.VmSettings;
import som.vmobjects.SInvokable;
import som.vmobjects.SSymbol;
import tools.concurrency.Tags.EventualMessageSend;
import tools.debugger.Tags.LiteralTag;
import tools.dym.Tags.BasicPrimitiveOperation;
import tools.dym.Tags.ClassRead;
import tools.dym.Tags.ComplexPrimitiveOperation;
import tools.dym.Tags.ControlFlowCondition;
import tools.dym.Tags.CreateActor;
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
import tools.dym.Tags.VirtualInvoke;
import tools.dym.Tags.VirtualInvokeReceiver;
import tools.dym.nodes.AllocationProfilingNode;
import tools.dym.nodes.ArrayAllocationProfilingNode;
import tools.dym.nodes.ControlFlowProfileNode;
import tools.dym.nodes.CountingNode;
import tools.dym.nodes.FarRefTypeProfilingNode;
import tools.dym.nodes.InvocationProfilingNode;
import tools.dym.nodes.LoopIterationReportNode;
import tools.dym.nodes.LoopProfilingNode;
import tools.dym.nodes.OperationProfilingNode;
import tools.dym.nodes.ReadProfilingNode;
import tools.dym.nodes.ReportReceiverNode;
import tools.dym.profiles.ActorCreationProfile;
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


/**
 * DynamicMetric is a Truffle instrumentation tool to measure a wide range of
 * dynamic metrics to characterize the behavior of executing code.
 *
 * WARNING:
 * - designed for single-threaded use only
 * - designed for use in interpreted mode only
 */
// @SuppressWarnings("deprecation")
@Registration(name = "DynamicMetrics", id = DynamicMetrics.ID, version = "0.1",
    services = {StructuralProbe.class})
public class DynamicMetrics extends TruffleInstrument {

  static final String ID = "dym-dynamic-metrics";

  @SuppressWarnings("unchecked")
  public static StructuralProbe<SSymbol, MixinDefinition, SInvokable, SlotDefinition, Variable> find(
      final TruffleLanguage.Env env) {
    InstrumentInfo instrument = env.getInstruments().get(ID);
    if (instrument == null) {
      throw new IllegalStateException(
          "DynamicMetrics not properly installed into polyglot.Engine");
    }

    return env.lookup(instrument, StructuralProbe.class);
  }

  @SuppressWarnings("unchecked")
  public static StructuralProbe<SSymbol, MixinDefinition, SInvokable, SlotDefinition, Variable> find(
      final Engine engine) {
    Instrument instrument = engine.getInstruments().get(ID);
    if (instrument == null) {
      throw new IllegalStateException(
          "DynamicMetrics not properly installed into polyglot.Engine");
    }

    return instrument.lookup(StructuralProbe.class);
  }

  private int methodStackDepth;
  private int maxStackDepth;

  private final Map<SourceSection, InvocationProfile>         methodInvocationCounter;
  private final Map<SourceSection, CallsiteProfile>           methodCallsiteProfiles;
  private final Map<SourceSection, ClosureApplicationProfile> closureProfiles;
  private final Map<SourceSection, OperationProfile>          operationProfiles;

  private final Map<SourceSection, AllocationProfile>    newObjectCounter;
  private final Map<SourceSection, ArrayCreationProfile> newArrayCounter;

  private final Map<SourceSection, BranchProfile> controlFlowProfiles;
  private final Map<SourceSection, LoopProfile>   loopProfiles;

  private final Map<SourceSection, ReadValueProfile> fieldReadProfiles;
  private final Map<SourceSection, Counter>          fieldWriteProfiles;
  private final Map<SourceSection, Counter>          classReadProfiles;
  private final Map<SourceSection, Counter>          literalReadCounter;
  private final Map<SourceSection, ReadValueProfile> localsReadProfiles;
  private final Map<SourceSection, Counter>          localsWriteProfiles;

  private final Map<SourceSection, ActorCreationProfile> actorCreationProfile;
  private final Map<SourceSection, Counter>              messageSends;

  private static final Map<String, AtomicLong> generalMetrics =
      VmSettings.DYNAMIC_METRICS ? new HashMap<>() : null;

  public static AtomicLong createLong(final String metric) {
    if (!VmSettings.DYNAMIC_METRICS) {
      return null;
    }
    AtomicLong counter = generalMetrics.get(metric);
    if (counter == null) {
      counter = new AtomicLong();
      generalMetrics.put(metric, counter);
    }
    return counter;
  }

  private final StructuralProbe<SSymbol, MixinDefinition, SInvokable, SlotDefinition, Variable> structuralProbe;

  private final Set<RootNode> rootNodes;

  @CompilationFinal private static Instrumenter instrumenter; // TODO: this is one of those
                                                              // evil hacks

  public static boolean hasTag(final Node node, final Class<? extends Tag> tag) {
    if (node instanceof InstrumentableNode) {
      return ((InstrumentableNode) node).hasTag(tag);
    }
    return false;
  }

  public DynamicMetrics() {
    structuralProbe = new StructuralProbe<>();

    methodInvocationCounter = new HashMap<>();
    methodCallsiteProfiles = new HashMap<>();
    closureProfiles = new HashMap<>();
    operationProfiles = new HashMap<>();

    newObjectCounter = new HashMap<>();
    newArrayCounter = new HashMap<>();

    controlFlowProfiles = new HashMap<>();
    loopProfiles = new HashMap<>();

    fieldReadProfiles = new HashMap<>();
    fieldWriteProfiles = new HashMap<>();
    classReadProfiles = new HashMap<>();
    literalReadCounter = new HashMap<>();
    localsReadProfiles = new HashMap<>();
    localsWriteProfiles = new HashMap<>();

    actorCreationProfile = new HashMap<>();
    messageSends = new HashMap<>();

    rootNodes = new HashSet<>();

    assert "DefaultTruffleRuntime".equals(
        Truffle.getRuntime().getClass()
               .getSimpleName()) : "To get metrics for the lexical, unoptimized behavior, please run this tool without Graal";
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

  private <N extends ExecutionEventNode, PRO extends Counter> ExecutionEventNodeFactory addInstrumentation(
      final Instrumenter instrumenter,
      final Map<SourceSection, PRO> storageMap,
      final Class<?>[] tagsIs,
      final Class<?>[] tagsIsNot,
      final Function<SourceSection, PRO> profileCtor,
      final Function<PRO, N> nodeCtor) {
    Builder filters = SourceSectionFilter.newBuilder();
    if (tagsIs != null && tagsIs.length > 0) {
      filters.tagIs(tagsIs);
    }
    if (tagsIsNot != null && tagsIsNot.length > 0) {
      filters.tagIsNot(tagsIsNot);
    }

    ExecutionEventNodeFactory factory = (final EventContext ctx) -> {
      PRO p = storageMap.computeIfAbsent(ctx.getInstrumentedSourceSection(), profileCtor);
      return nodeCtor.apply(p);
    };

    instrumenter.attachExecutionEventFactory(filters.build(), factory);
    return factory;
  }

  private void addRootTagInstrumentation(final Instrumenter instrumenter) {
    Builder filters = SourceSectionFilter.newBuilder();
    filters.tagIs(RootTag.class);
    instrumenter.attachExecutionEventFactory(filters.build(), (final EventContext ctx) -> {
      RootNode root = ctx.getInstrumentedNode().getRootNode();
      assert root instanceof Invokable : "TODO: make language independent";
      InvocationProfile p = methodInvocationCounter.computeIfAbsent(
          ctx.getInstrumentedSourceSection(),
          ss -> new InvocationProfile(ss, (Invokable) root));
      return new InvocationProfilingNode(this, p);
    });
  }

  private static String getOperation(final Node node) {
    if (node instanceof Operation) {
      return ((Operation) node).getOperation();
    }
    throw new NotYetImplementedException();
  }

  private static int getNumArguments(final Node node) {
    if (node instanceof Operation) {
      return ((Operation) node).getNumArguments();
    }
    throw new NotYetImplementedException();
  }

  private void addOperationInstrumentation(final Instrumenter instrumenter) {
    Builder filters = SourceSectionFilter.newBuilder();
    filters.tagIs(ComplexPrimitiveOperation.class, BasicPrimitiveOperation.class);

    ExecutionEventNodeFactory primExpFactory = (final EventContext ctx) -> {
      int numArgsAndResult = getNumArguments(ctx.getInstrumentedNode()) + 1;
      String operation = getOperation(ctx.getInstrumentedNode());
      Set<Class<?>> tags = instrumenter.queryTags(ctx.getInstrumentedNode());

      OperationProfile p = operationProfiles.computeIfAbsent(
          ctx.getInstrumentedSourceSection(),
          (final SourceSection src) -> new OperationProfile(
              src, operation, tags, numArgsAndResult));
      return new OperationProfilingNode(p);
    };

    SourceSectionFilter filter = filters.build();
    instrumenter.attachExecutionEventFactory(filter, SourceSectionFilter.ANY, primExpFactory);
  }

  private void addReceiverInstrumentation(final Instrumenter instrumenter,
      final ExecutionEventNodeFactory virtInvokeFactory) {
    Builder filters = SourceSectionFilter.newBuilder();
    filters.tagIs(VirtualInvokeReceiver.class);

    instrumenter.attachExecutionEventFactory(filters.build(), (final EventContext ctx) -> {
      ExecutionEventNode parent = ctx.findDirectParentEventNode(virtInvokeFactory);

      @SuppressWarnings("unchecked")
      CountingNode<CallsiteProfile> p = (CountingNode<CallsiteProfile>) parent;
      CallsiteProfile profile = p.getProfile();
      return new ReportReceiverNode(profile);
    });
  }

  private ExecutionEventNodeFactory addVirtualInvokeInstrumentation(
      final Instrumenter instrumenter) {
    Builder filters = SourceSectionFilter.newBuilder();
    filters.tagIs(VirtualInvoke.class);

    ExecutionEventNodeFactory factory = (final EventContext ctx) -> {
      CallsiteProfile profile = methodCallsiteProfiles.computeIfAbsent(
          ctx.getInstrumentedSourceSection(),
          (final SourceSection source) -> new CallsiteProfile(ctx.getInstrumentedNode()));
      return new CountingNode<CallsiteProfile>(profile);
    };

    instrumenter.attachExecutionEventFactory(filters.build(), factory);
    return factory;
  }

  private static final Class<?>[] NO_TAGS = new Class<?>[0];

  @Override
  protected void onCreate(final Env env) {
    instrumenter = env.getInstrumenter();

    addRootTagInstrumentation(instrumenter);

    ExecutionEventNodeFactory virtInvokeFactory =
        addVirtualInvokeInstrumentation(instrumenter);
    addReceiverInstrumentation(instrumenter, virtInvokeFactory);

    addInstrumentation(
        instrumenter, closureProfiles,
        new Class<?>[] {OpClosureApplication.class}, NO_TAGS,
        ClosureApplicationProfile::new, CountingNode<ClosureApplicationProfile>::new);

    addInstrumentation(instrumenter, newObjectCounter,
        new Class<?>[] {NewObject.class}, NO_TAGS,
        AllocationProfile::new, AllocationProfilingNode::new);
    addInstrumentation(instrumenter, newArrayCounter,
        new Class<?>[] {NewArray.class}, NO_TAGS,
        ArrayCreationProfile::new, ArrayAllocationProfilingNode::new);

    addInstrumentation(instrumenter, literalReadCounter,
        new Class<?>[] {LiteralTag.class}, NO_TAGS,
        Counter::new, CountingNode<Counter>::new);

    addOperationInstrumentation(instrumenter);

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

    ExecutionEventNodeFactory loopProfileFactory =
        addInstrumentation(instrumenter, loopProfiles,
            new Class<?>[] {LoopNode.class}, NO_TAGS,
            LoopProfile::new, LoopProfilingNode::new);

    addLoopBodyInstrumentation(instrumenter, loopProfileFactory);

    // Instrumentation for Actor Constructs
    addInstrumentation(instrumenter, actorCreationProfile,
        new Class<?>[] {CreateActor.class}, NO_TAGS,
        ActorCreationProfile::new, FarRefTypeProfilingNode::new);
    addInstrumentation(instrumenter, messageSends,
        new Class<?>[] {EventualMessageSend.class}, NO_TAGS,
        Counter::new, CountingNode<Counter>::new);

    // record all rootNodes to easily get all methods
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

    instrumenter.attachExecutionEventFactory(filters.build(), (final EventContext ctx) -> {
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
        maxStackDepth, getAllStatementsAlsoNotExecuted(), generalMetrics);

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

  @SuppressWarnings("deprecation")
  private void outputAllTruffleMethodsToIGV() {
    GraphPrintVisitor graphPrinter = new GraphPrintVisitor();

    EconomicSet<MixinDefinition> classSet = structuralProbe.getClasses();
    List<MixinDefinition> classes = new ArrayList<MixinDefinition>(classSet.size());

    for (MixinDefinition c : classSet) {
      classes.add(c);
    }

    Collections.sort(classes,
        (final MixinDefinition a,
            final MixinDefinition b) -> a.getName().getString()
                                         .compareTo(b.getName().getString()));

    for (MixinDefinition mixin : classes) {
      graphPrinter.beginGroup(mixin.getName().getString());

      for (Dispatchable disp : mixin.getInstanceDispatchables().getValues()) {
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
    data.put(JsonWriter.METHOD_CALLSITE, methodCallsiteProfiles);
    data.put(JsonWriter.CLOSURE_APPLICATIONS, closureProfiles);
    data.put(JsonWriter.NEW_OBJECT_COUNT, newObjectCounter);
    data.put(JsonWriter.NEW_ARRAY_COUNT, newArrayCounter);
    data.put(JsonWriter.FIELD_READS, fieldReadProfiles);
    data.put(JsonWriter.FIELD_WRITES, fieldWriteProfiles);
    data.put(JsonWriter.CLASS_READS, classReadProfiles);
    data.put(JsonWriter.BRANCH_PROFILES, controlFlowProfiles);
    data.put(JsonWriter.LITERAL_READS, literalReadCounter);
    data.put(JsonWriter.LOCAL_READS, localsReadProfiles);
    data.put(JsonWriter.LOCAL_WRITES, localsWriteProfiles);
    data.put(JsonWriter.OPERATIONS, operationProfiles);
    data.put(JsonWriter.LOOPS, loopProfiles);

    data.put(JsonWriter.ACTOR_CREATION, actorCreationProfile);
    data.put(JsonWriter.MESSAGE_SENDS, messageSends);
    return data;
  }

}
