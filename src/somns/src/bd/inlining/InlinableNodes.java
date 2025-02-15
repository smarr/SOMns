package bd.inlining;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.List;

import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.SourceSection;

import bd.basic.IdProvider;
import bd.basic.ProgramDefinitionError;
import bd.inlining.Inliner.FactoryInliner;
import bd.settings.VmSettings;


/**
 * Represents the entry point to access the inlining functionality controlled with
 * the @{@link Inline} annotation.
 *
 * <p>A typical use case would be in a parser, which can use the
 * {@link #inline(Object, List, ScopeBuilder, SourceSection)} to request inlining.
 * For this purpose, {@link InlinableNodes} takes a list of node classes and factories as
 * candidates for inlining.
 *
 * @param <Id> the type of the identifiers used for mapping to primitives, typically some form
 *          of interned string construct (see {@link IdProvider})
 */
public final class InlinableNodes<Id> {

  /** The id provider is used to map strings in the {@link Inline} annotation to ids. */
  private final IdProvider<Id> ids;

  /** Inlinable nodes for selector. */
  private final HashMap<Id, Inliner> inlinableNodes;

  /**
   * Initialize this registry for inlinable nodes.
   *
   * @param ids an id provider to convert strings to identifiers
   * @param inlinableNodes list of {@link Node} classes that have the @{@link Inline}
   *          annotation
   * @param inlinableFactories list of {@link NodeFactory}s for classes that have
   *          the @{@link Inline} annotation
   */
  public InlinableNodes(final IdProvider<Id> ids,
      final List<Class<? extends Node>> inlinableNodes,
      final List<NodeFactory<? extends Node>> inlinableFactories) {
    this.ids = ids;
    this.inlinableNodes = new HashMap<>();
    initializeNodes(inlinableNodes);
    initializeFactories(inlinableFactories);
  }

  private void initializeNodes(final List<Class<? extends Node>> inlinableNodes) {
    if (inlinableNodes == null) {
      return;
    }

    for (Class<? extends Node> nodeClass : inlinableNodes) {
      Inline[] ann = getInlineAnnotation(nodeClass);
      assert ann != null;

      Constructor<?>[] ctors = nodeClass.getConstructors();
      assert ctors.length == 1
          : "We expect nodes marked with Inline to have only one constructor,"
              + " or be used via node factories.";

      for (Inline inAn : ann) {
        assert !"".equals(inAn.selector());
        Id selector = ids.getId(inAn.selector());
        assert !this.inlinableNodes.containsKey(selector);

        @SuppressWarnings("unchecked")
        Inliner inliner = new Inliner(inAn, (Constructor<? extends Node>) ctors[0]);

        this.inlinableNodes.put(selector, inliner);
      }
    }
  }

  private void initializeFactories(
      final List<NodeFactory<? extends Node>> inlinableFactories) {
    if (inlinableFactories == null) {
      return;
    }

    for (NodeFactory<? extends Node> fact : inlinableFactories) {
      Inline[] ann = getInlineAnnotation(fact);
      assert ann != null;

      for (Inline inAn : ann) {
        assert !"".equals(inAn.selector());
        Id selector = ids.getId(inAn.selector());

        assert !this.inlinableNodes.containsKey(selector);
        Inliner inliner = new FactoryInliner(inAn, fact);
        this.inlinableNodes.put(selector, inliner);
      }
    }
  }

  /**
   * Try to construct an inlined version for a potential node (which is not given here, but
   * would be constructed as a fall-back version).
   *
   * <p>The potential node is identified with a {@code selector} and it is determined whether
   * inlining is applicable by using the data from {@link Inline} and matching it with the
   * {@code argNodes}.
   *
   * @param <N> the node type of the return value
   * @param <S> the type of the {@link ScopeBuilder}
   *
   * @param selector to identify a potential inline replacement
   * @param argNodes the argument/child nodes to the potential node
   * @param builder used for providing context to the inlining operation
   * @param source the source section of the potential node
   * @return the inlined version of the potential node, or {@code null}, if inlining is not
   *         applicable
   * @throws ProgramDefinitionError in case the inlining would result in a structural violation
   *           of program definition constraints. The error is language specific and not
   *           triggered by the inlining logic.
   */
  public <N extends Node, S extends ScopeBuilder<S>> N inline(final Id selector,
      final List<N> argNodes, final S builder, final SourceSection source)
      throws ProgramDefinitionError {
    Inliner inliner = inlinableNodes.get(selector);
    if (inliner == null || (VmSettings.DYNAMIC_METRICS && inliner.isDisabled())) {
      return null;
    }

    if (!inliner.matches(argNodes)) {
      return null;
    }

    return inliner.create(argNodes, builder, source);
  }

  /**
   * Get the {@link Inline} annotation from a {@link NodeFactory}.
   */
  private <T extends Node> Inline[] getInlineAnnotation(final NodeFactory<T> factory) {
    Class<?> nodeClass = factory.getNodeClass();
    return nodeClass.getAnnotationsByType(Inline.class);
  }

  /**
   * Get the {@link Inline} annotation from a {@link Node} class.
   */
  private Inline[] getInlineAnnotation(final Class<? extends Node> nodeClass) {
    Inline[] annotations = nodeClass.getAnnotationsByType(Inline.class);
    if (annotations == null || annotations.length < 1) {
      throw new IllegalArgumentException("The class " + nodeClass.getName()
          + " was registered with InlinableNodes, but was not marked with @Inline."
          + " Please make sure it has the right annotation.");
    }
    return annotations;
  }
}
