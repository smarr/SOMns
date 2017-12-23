package tools.superinstructions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.ExecutionEventNodeFactory;
import com.oracle.truffle.api.instrumentation.Instrumenter;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.StandardTags.RootTag;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.instrumentation.TruffleInstrument.Registration;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;

import tools.dym.Tags.AnyNode;
import tools.language.StructuralProbe;


/**
 * The {@link CandidateIdentifier} is a Truffle instrumentation tool that tracks execution to
 * identify potential candidates for super-instructions.
 */
@Registration(name = "Super-Instruction Candidate Identifier",
    id = CandidateIdentifier.ID, version = "0.1",
    services = {StructuralProbe.class})
public class CandidateIdentifier extends TruffleInstrument {

  public static final String ID = "si-candidate-ider";

  private final Map<Node, TypeCounter> activations;

  private final StructuralProbe structuralProbe;

  private final Set<RootNode> rootNodes;

  public CandidateIdentifier() {
    activations = new HashMap<>();
    structuralProbe = new StructuralProbe();
    rootNodes = new HashSet<>();
  }

  @Override
  protected void onCreate(final Env env) {
    Instrumenter instrumenter = env.getInstrumenter();
    addActivationInstrumentation(instrumenter);

    instrumenter.attachLoadSourceSectionListener(
        SourceSectionFilter.newBuilder().tagIs(RootTag.class).build(),
        e -> rootNodes.add(e.getNode().getRootNode()),
        true);

    env.registerService(structuralProbe);
  }

  private void addActivationInstrumentation(final Instrumenter instrumenter) {
    // Attach a TypeCountingNode to *any* node
    SourceSectionFilter filter = SourceSectionFilter.newBuilder().tagIs(AnyNode.class).build();
    ExecutionEventNodeFactory factory = (final EventContext ctx) -> {
      TypeCounter p = activations.computeIfAbsent(ctx.getInstrumentedNode(),
          k -> new TypeCounter(ctx.getInstrumentedSourceSection()));
      return new TypeCountingNode(p);
    };
    instrumenter.attachFactory(filter, factory);
  }

  @Override
  protected void onDispose(final Env env) {
    String outputFile = System.getProperty("si.output", "superinstruction-candidates.txt");
    identifySuperinstructionCandidates(outputFile);
  }

  private void identifySuperinstructionCandidates(final String outputFile) {
    // First, extract activation contexts from the recorded activations
    ContextCollector collector = new ContextCollector(activations);
    for (RootNode root : rootNodes) {
      root.accept(collector);
    }

    // Then, detect super-instruction candidates using the heuristic ...
    CandidateSelector detector = new CandidateSelector(collector.getContexts());
    // ... and write a report to the metrics folder
    String report = detector.detect();
    Path reportPath = Paths.get(outputFile);
    try {
      Files.write(reportPath, report.getBytes());
    } catch (IOException e) {
      throw new RuntimeException("Could not write superinstruction candidate report: " + e);
    }
  }
}
