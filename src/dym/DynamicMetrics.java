package dym;

import java.util.HashMap;
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
import com.oracle.truffle.api.instrumentation.EventNode;
import com.oracle.truffle.api.instrumentation.Instrumenter;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter.Builder;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.instrumentation.TruffleInstrument.Registration;
import com.oracle.truffle.api.source.SourceSection;

import dym.nodes.AllocationProfilingNode;
import dym.nodes.ArrayAllocationProfilingNode;
import dym.nodes.ControlFlowProfileNode;
import dym.nodes.CountingNode;
import dym.nodes.FieldReadProfilingNode;
import dym.nodes.InvocationProfilingNode;
import dym.profiles.AllocationProfile;
import dym.profiles.ArrayCreationProfile;
import dym.profiles.BranchProfile;
import dym.profiles.Counter;
import dym.profiles.InvocationProfile;
import dym.profiles.MethodCallsiteProbe;
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
@Registration(id = DynamicMetrics.ID, autostart = false)
public class DynamicMetrics extends TruffleInstrument {

  public static final String ID       = "dym-dynamic-metrics";

  // Tags used by the DynamicMetrics tool
  public static final String ROOT_TAG           = "ROOT";
  public static final String UNSPECIFIED_INVOKE = "UNSPECIFIED_INVOKE"; // this is some form of invoke in the source, unclear what it is during program execution
  public static final String INVOKE_WITH_LOOKUP = "INVOKE_WITH_LOOKUP";
  public static final String NEW_OBJECT         = "NEW_OBJECT";
  public static final String NEW_ARRAY          = "NEW_ARRAY";
  public static final String CONTROL_FLOW_CONDITION  = "CONTROL_FLOW_CONDITION"; // a condition expression that results in a control-flow change

  // TODO
  public static final String FIELD_READ         = "FIELD_READ";
  public static final String FIELD_WRITE        = "FIELD_WRITE";
  public static final String ARRAY_READ         = "ARRAY_READ";
  public static final String ARRAY_WRITE        = "ARRAY_WRITE";
  public static final String LOOP_BODY          = "LOOP_BODY";

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
  private final Map<SourceSection, Counter> basicOperationCounter;
  private final Map<SourceSection, Counter> loopProfiles;

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

  private <N extends EventNode, PRO extends Counter>
    void addInstrumentation(final Instrumenter instrumenter,
      final Map<SourceSection, PRO> storageMap,
      final Function<SourceSection, PRO> pCtor,
      final Function<PRO, N> nCtor,
      final String... tags) {
    Builder filters = SourceSectionFilter.newBuilder();
    filters.tagIs(tags);
    instrumenter.attachFactory(filters.build(),
        (final EventContext ctx) -> {
          PRO p = storageMap.computeIfAbsent(ctx.getInstrumentedSourceSection(),
              pCtor);
          return nCtor.apply(p);
        });
  }

  @Override
  protected void onCreate(final Env env, final Instrumenter instrumenter) {
    addInstrumentation(instrumenter, methodInvocationCounter, InvocationProfile::new,
        p -> new InvocationProfilingNode(this, p), ROOT_TAG);

    addInstrumentation(instrumenter, methodCallsiteProbes, MethodCallsiteProbe::new,
        CountingNode<Counter>::new, UNSPECIFIED_INVOKE);

    addInstrumentation(instrumenter, newObjectCounter, AllocationProfile::new,
        AllocationProfilingNode::new, NEW_OBJECT);
    addInstrumentation(instrumenter, newArrayCounter, ArrayCreationProfile::new,
        ArrayAllocationProfilingNode::new, NEW_ARRAY);

    addInstrumentation(instrumenter, literalReadCounter, Counter::new,
        CountingNode<Counter>::new, Tags.SYNTAX_LITERAL);

    addInstrumentation(instrumenter, basicOperationCounter, Counter::new,
        CountingNode<Counter>::new, Tags.BASIC_PRIMITIVE_OPERATION);

    addInstrumentation(instrumenter, fieldReadProfiles, ReadValueProfile::new,
        FieldReadProfilingNode::new, Tags.FIELD_READ);
    addInstrumentation(instrumenter, localsReadProfiles, ReadValueProfile::new,
        FieldReadProfilingNode::new, Tags.LOCAL_ARG_READ, Tags.LOCAL_VAR_READ);

    addInstrumentation(instrumenter, fieldWriteProfiles, Counter::new,
        CountingNode<Counter>::new, Tags.FIELD_WRITE);
    addInstrumentation(instrumenter, localsWriteProfiles, Counter::new,
        CountingNode<Counter>::new, Tags.LOCAL_VAR_WRITE);

    addInstrumentation(instrumenter, controlFlowProfiles, BranchProfile::new,
        ControlFlowProfileNode::new, Tags.CONTROL_FLOW_CONDITION);

    addInstrumentation(instrumenter, loopProfiles, Counter::new,
        CountingNode<Counter>::new, Tags.LOOP_BODY);
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
