package dym;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import com.oracle.truffle.api.instrumentation.InstrumentableFactory.WrapperNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.SourceSection;


public abstract class Tagging {
  public interface Tagged {
    SourceSection getSourceSection();
    void updateTags(SourceSection sourceSection);
  }

  private static final String[] empty = new String[0];

  public static void updateTags(final Node node,
      final String[] add, final String[] remove) {
    Tagged nodeToBeUpdated = getTagged(node);
    updateTagsForTaggedNode(nodeToBeUpdated, add, remove);
  }

  public static SourceSection getSourceSectionWithTags(final Node node,
      final String... add) {
    Tagged tagged = getTagged(node);
    return cloneAndAddTags(tagged.getSourceSection(), add);
  }

  private static Tagged getTagged(final Node node) {
    Tagged nodeToBeUpdated;

    if (node instanceof WrapperNode) {
      nodeToBeUpdated = (Tagged) ((WrapperNode) node).getDelegateNode();
    } else {
      assert node instanceof Tagged : "Expected a `Tagged` node (" + node.toString() + ")";
      nodeToBeUpdated = (Tagged) node;
    }

    assert !(nodeToBeUpdated instanceof WrapperNode) : "This should not happen, wrapper can't wrap wrappers";
    return nodeToBeUpdated;
  }

  public static void addTags(final Node node, final String... add) {
    updateTags(node, add, empty);
  }

  public static SourceSection cloneAndUpdateTagsIfSourceNode(final SourceSection source,
      final String[] add, final String[] remove) {
    if (source == null) { return null; }
    return cloneAndUpdateTags(source, add, remove);
  }

  public static SourceSection cloneAndUpdateTags(final SourceSection source,
      final String[] add, final String[] remove) {
    String[] updatedTags = updatedTags(source.getTags(), add, remove);
    return source.withTags(updatedTags);
  }

  public static SourceSection cloneAndAddTags(final SourceSection source,
      final String... add) {
    return cloneAndUpdateTags(source, add, empty);
  }

  private static void updateTagsForTaggedNode(final Tagged node,
      final String[] add, final String[] remove) {
    assert !(node instanceof WrapperNode);
    node.updateTags(cloneAndUpdateTags(node.getSourceSection(), add, remove));
  }

  private static String[] updatedTags(final String[] current,
      final String[] add, final String[] remove) {
    String[] originalTags = current;
    if (originalTags == null) {
      originalTags = new String[0];
    }
    Set<String> newTags = new HashSet<String>(Arrays.asList(originalTags));
    newTags.addAll(Arrays.asList(add));
    newTags.removeAll(Arrays.asList(remove));
    return newTags.toArray(new String[0]);
  }
}
