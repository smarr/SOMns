package tools.superinstructions;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import som.interpreter.nodes.SequenceNode;


/**
 * Given a map mapping activation contexts to activation counts,
 * create a list of super-instruction candidates.
 */
final class CandidateSelector {
  private static final int             CONSIDER_TOP_CONTEXTS = 100;
  private Map<ActivationContext, Long> contexts;

  CandidateSelector(final Map<ActivationContext, Long> contexts) {
    this.contexts = contexts;
  }

  /**
   * Return a stream of ActivationContext objects whose traces begin with prefix.
   */
  private Stream<ActivationContext> contextsWithPrefix(final Object[] prefix) {
    return contexts.keySet().stream()
                   .filter(ctx -> ctx.traceStartsWith(prefix));
  }

  /**
   * Given a prefix
   * [ C_0, s_0, ..., s_{n-1}, C_n ]
   * this returns a map mapping each s_n to an activation context with a trace
   * [ C_0, s_0, ..., s_{n-1}, C_n, s_n, C_{n+1} ]
   * for which the number of activations is maximal.
   */
  private Map<Integer, ActivationContext> findExtensions(final Object... prefix) {
    Map<Integer, ActivationContext> result = new HashMap<>();
    // Find all activation contexts which extend prefix
    // with one child slot index and one class name
    Set<ActivationContext> extensions =
        contextsWithPrefix(prefix).filter(ctx -> ctx.getTrace().length == prefix.length + 2)
                                  .collect(Collectors.toSet());
    // Extract all possible values of s_n
    Set<Integer> childIndices = extensions.stream()
                                          .map(ActivationContext::getLeafChildIndex)
                                          .collect(Collectors.toSet());
    // Handle each s_n separately ...
    for (int childIndex : childIndices) {
      // Get all extensions for which s_n == childIndex,
      // get the extension with the most activations, put it into the map
      ActivationContext extension =
          extensions.stream().filter(ctx -> ctx.getLeafChildIndex() == childIndex)
                    .max(Comparator.comparingLong(ctx -> contexts.get(ctx)))
                    .orElseThrow(() -> new RuntimeException("No suitable alternatives"));
      result.put(childIndex, extension);
    }
    return result;
  }

  /**
   * Given an activation context, construct a candidate for a super-instruction
   * and return it.
   */
  private Candidate constructCandidate(final ActivationContext currentContext) {
    assert currentContext.getNumberOfClasses() == 3;
    // currentContext has a trace:
    // [C_0, s_0, C_1, s_1, C_2]
    // We now find possible "piblings", i.e. siblings of its parent.
    // For that, we invoke findExtensions on the prefix
    // [C_0]
    Map<Integer, ActivationContext> piblings = findExtensions(currentContext.getClass(0));

    // Also, we find possible siblings. For that, we invoke findExtensions
    // on the prefix
    // [C_0, s_0, C_1]
    Map<Integer, ActivationContext> siblings = findExtensions(currentContext.getClass(0),
        currentContext.getChildIndex(0), currentContext.getClass(1));

    // We now construct a super-instruction candidate, i.e.
    // a tree of height 2. The root of the tree is C_0 (its Java type is unknown).
    Candidate candidate = new Candidate(currentContext.getClass(0), "?");
    // Now, we add the children of C_0, i.e. the siblings of C_1 and C_1 itself.
    for (int piblingSlot : piblings.keySet()) {
      if (piblingSlot == currentContext.getChildIndex(0)) {
        // This is C_1. We add it to the candidate and proceed
        // with adding the siblings of C_2 and C_2 itself.
        Candidate.AstNode child =
            candidate.getRoot().setChild(piblingSlot, currentContext.getClass(1), "?");
        for (int siblingSlot : siblings.keySet()) {
          if (siblingSlot == currentContext.getChildIndex(1)) {
            // Add C_2
            child.setChild(siblingSlot,
                currentContext.getClass(2),
                currentContext.getJavaType());
          } else {
            // Add a sibling of C_2
            ActivationContext sibling = siblings.get(siblingSlot);
            child.setChild(siblingSlot,
                sibling.getClass(2),
                sibling.getJavaType());
          }
        }
      } else {
        ActivationContext pibling = piblings.get(piblingSlot);
        // Add a sibling of C_1.
        assert pibling.getNumberOfClasses() == 2;
        candidate.getRoot().setChild(piblingSlot,
            pibling.getClass(1),
            pibling.getJavaType());
      }
    }
    // The score of the super-instruction candidate corresponds to the number
    // of activations of the current context.
    candidate.setScore(contexts.get(currentContext));
    return candidate;
  }

  private static int candidateByScoreAndPrint(final Candidate a, final Candidate b) {
    long r = b.getScore() - a.getScore();
    if (r != 0) {
      return (int) r;
    }
    return a.prettyPrint().compareTo(b.prettyPrint());
  }

  /**
   * Run the candidate detection heuristic and return a report as a String.
   */
  public String detect() {
    // Find all activation contexts with traces of length 3,
    // exclude traces containing SequenceNodes (they do not yield
    // viable candidates for super-instructions),
    // sort them by activation counts in descending order,
    // fetch the top CONSIDER_TOP_CONTEXTS activation contexts
    // and construct a candidate for each of them.
    List<ActivationContext> sorted =
        contexts.keySet().stream().filter(context -> context.getNumberOfClasses() == 3)
                .filter(context -> !Arrays.asList(context.getTrace())
                                          .contains(SequenceNode.class.getName()))
                .sorted(Comparator.comparingLong(context -> contexts.get(context)).reversed())
                .collect(Collectors.toList());
    Set<Candidate> candidates = new HashSet<>();

    for (ActivationContext context : sorted) {
      candidates.add(constructCandidate(context));
    }

    // Sort the candidates by their score and format the results
    List<Candidate> tops =
        candidates.stream().sorted(CandidateSelector::candidateByScoreAndPrint)
                  .limit(CONSIDER_TOP_CONTEXTS)
                  .collect(Collectors.toList());

    StringBuilder builder = new StringBuilder();
    for (Candidate top : tops) {
      builder.append(top.prettyPrint()).append('\n');
      builder.append(String.format("(%d activations)", top.getScore())).append("\n\n");
    }
    return builder.toString();
  }
}
