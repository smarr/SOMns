package dym;

import java.util.HashMap;
import java.util.Map;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.EventNode;
import com.oracle.truffle.api.instrumentation.Instrumenter;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter.Builder;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.instrumentation.TruffleInstrument.Registration;
import com.oracle.truffle.api.source.SourceSection;

import dym.nodes.CountingNode;
import dym.nodes.InvocationProfilingNode;
import dym.profiles.Counter;
import dym.profiles.InvocationProfile;


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
  public static final String ROOT_TAG = "ROOT";
  public static final String UNSPECIFIED_INVOKE = "UNSPECIFIED_INVOKE"; // this is some form of invoke in the source, unclear what it is during program execution
  public static final String INVOKE_WITH_LOOKUP = "INVOKE_WITH_LOOKUP";
  public static final String NEW_OBJECT         = "NEW_OBJECT";
  public static final String NEW_ARRAY          = "NEW_ARRAY";

  private final Map<SourceSection, InvocationProfile> methodInvocationCounter;
  private int methodStackDepth;
  private int maxStackDepth;

  private final Map<SourceSection, MethodCallsiteProbe> methodCallsiteProbes;
  private final Map<SourceSection, Counter> instantiationCounter;

  public DynamicMetrics() {
    methodInvocationCounter = new HashMap<>();
    methodCallsiteProbes    = new HashMap<>();
    instantiationCounter    = new HashMap<>();

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

  @Override
  protected void onCreate(final Env env, final Instrumenter instrumenter) {
    Builder filters = SourceSectionFilter.newBuilder();
    filters.tagIs(ROOT_TAG);
    instrumenter.attachFactory(
        filters.build(),
        (final EventContext context) -> {
          return createInvocationCountingNode(context);
        });

    filters = SourceSectionFilter.newBuilder();
    filters.tagIs(UNSPECIFIED_INVOKE);
    instrumenter.attachFactory(
        filters.build(),
        (final EventContext context) -> {
          return createMethodCallsiteNode(context);
        });

    filters = SourceSectionFilter.newBuilder();
    filters.tagIs(NEW_OBJECT, NEW_ARRAY);
    instrumenter.attachFactory(
        filters.build(),
        (final EventContext context) -> {
          Counter counter = instantiationCounter.computeIfAbsent(
              context.getInstrumentedSourceSection(), src -> new Counter(src));
          return new CountingNode(counter);
        });
  }

  @Override
  protected void onDispose(final Env env) {
    @SuppressWarnings("unused")
    int i = 0;
  }


  private EventNode createInvocationCountingNode(final EventContext context) {
    SourceSection source = context.getInstrumentedSourceSection();
    InvocationProfile counter = methodInvocationCounter.computeIfAbsent(
        source, src -> new InvocationProfile(src));
    return new InvocationProfilingNode(this, counter);
  }

  private EventNode createMethodCallsiteNode(final EventContext context) {
    SourceSection source = context.getInstrumentedSourceSection();
    MethodCallsiteProbe probe = methodCallsiteProbes.computeIfAbsent(
        source, src -> new MethodCallsiteProbe(src));
    return new CountingNode(probe);
  }

  public static class MethodCallsiteProbe extends Counter {

    public MethodCallsiteProbe(final SourceSection source) {
      super(source);
    }

  }
}
