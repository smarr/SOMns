package tools.superinstructions;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;


/**
 * Representation of a super-instruction candidate. This is a tree
 * of nodes which denotes the AST subtree that would be replaced by the super-instruction.
 * The candidate is annotated with a numeric score (the higher, the better).
 * Each node is annotated with a Java type (or "?" if it is unknown)
 */
final class Candidate {
  private final AstNode rootNode;
  private long          score;

  Candidate(final String rootClass, final String javaType) {
    this.rootNode = new AstNode(rootClass, javaType);
  }

  /**
   * Given a Java class name, return its abbreviation, i.e. strip the package path.
   */
  private static String abbreviateClass(final String className) {
    String[] splitted = className.split("\\.");
    return splitted[splitted.length - 1];
  }

  public AstNode getRoot() {
    return rootNode;
  }

  public long getScore() {
    return score;
  }

  public void setScore(final long score) {
    this.score = score;
  }

  public String prettyPrint() {
    StringBuilder builder = new StringBuilder();
    rootNode.prettyPrint(builder, 0);
    return builder.toString();
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }

    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    Candidate candidate = (Candidate) o;
    return rootNode.equals(candidate.rootNode);
  }

  @Override
  public int hashCode() {
    return Objects.hash(rootNode);
  }

  static final class AstNode {
    private final String                nodeClass;
    private final String                javaType;
    private final Map<Integer, AstNode> children;

    AstNode(final String nodeClass, final String javaType) {
      this.nodeClass = nodeClass;
      this.javaType = javaType;
      this.children = new HashMap<>();
    }

    /**
     * Create a new Node object at the given slot index, add it to the tree and return it.
     */
    public AstNode setChild(final int index, final String childClass,
        final String javaType) {
      AstNode astNode = new AstNode(childClass, javaType);
      this.children.put(index, astNode);
      return astNode;
    }

    public String getNodeClass() {
      return nodeClass;
    }

    public Map<Integer, AstNode> getChildren() {
      return children;
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }

      if (o == null || getClass() != o.getClass()) {
        return false;
      }

      AstNode astNode = (AstNode) o;
      return Objects.equals(nodeClass, astNode.nodeClass) &&
          Objects.equals(javaType, astNode.javaType) &&
          Objects.equals(children, astNode.children);
    }

    @Override
    public int hashCode() {
      return Objects.hash(nodeClass, javaType, children);
    }

    /**
     * Recursively print the tree to a StringBuilder with a given indentation level.
     * Nonexistent slots are denoted with a "?".
     */
    public void prettyPrint(final StringBuilder builder, final int level) {
      // Add proper indentation
      for (int i = 0; i < level; i++) {
        builder.append("  ");
      }
      builder.append(abbreviateClass(nodeClass));
      if (!"?".equals(javaType)) {
        builder.append('[')
               .append(abbreviateClass(javaType))
               .append(']');
      }

      builder.append('\n');
      // Find the maximum slot index
      int maxKey = children.keySet().stream()
                           .max(Comparator.comparingInt(e -> e))
                           .orElse(-1);
      // Recursively print all slots
      for (int slot = 0; slot <= maxKey; slot++) {
        if (children.containsKey(slot)) {
          children.get(slot).prettyPrint(builder, level + 1);
        } else {
          AstNode dummy = new AstNode("?", "?");
          dummy.prettyPrint(builder, level + 1);
        }
      }
    }
  }
}
