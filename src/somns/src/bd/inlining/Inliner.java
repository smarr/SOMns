package bd.inlining;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.List;

import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.SourceSection;

import bd.basic.ProgramDefinitionError;
import bd.inlining.Inline.False;
import bd.inlining.Inline.True;
import bd.inlining.nodes.Inlinable;
import bd.inlining.nodes.WithSource;


class Inliner {
  protected final Inline inline;

  private final Constructor<? extends Node> ctor;

  Inliner(final Inline inline, final Constructor<? extends Node> ctor) {
    this.inline = inline;
    this.ctor = ctor;
  }

  public boolean isDisabled() {
    return inline.disabled();
  }

  public boolean matches(final List<? extends Node> argNodes) {
    int[] args = inline.inlineableArgIdx();
    assert args != null;

    boolean allInlinable = true;
    for (int i : args) {
      allInlinable &= argNodes.get(i) instanceof Inlinable;
    }
    return allInlinable;
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  public <N extends Node> N create(final List<N> argNodes, final ScopeBuilder scopeBuilder,
      final SourceSection source) throws ProgramDefinitionError {
    Object[] args = new Object[argNodes.size() + inline.inlineableArgIdx().length
        + inline.additionalArgs().length];

    assert args.length == ctor.getParameterCount();

    int i = 0;
    for (N arg : argNodes) {
      args[i] = arg;
      i += 1;
    }

    for (int a : inline.inlineableArgIdx()) {
      args[i] = ((Inlinable) argNodes.get(a)).inline(scopeBuilder);
      i += 1;
    }

    for (Class<?> c : inline.additionalArgs()) {
      if (c == True.class) {
        args[i] = true;
      } else {
        assert c == False.class;
        args[i] = false;
      }
      i += 1;
    }
    try {
      N node = (N) ctor.newInstance(args);
      ((WithSource) node).initialize(source);
      return node;
    } catch (InstantiationException | IllegalAccessException | IllegalArgumentException
        | InvocationTargetException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Nodes that use a factory are expected to be structured so that the first argNodes are
   * going to be evaluated by the DSL, which means, they are going to be appended at the end
   * of the args array.
   *
   * <p>The rest is treated as normal, first the args, then the inlined args,
   * then possibly to be introduced temps, and finally possible additional args.
   *
   * @param <ExprT>
   * @param <NodeState>
   * @param <MethodT>
   * @param <OuterT>
   * @param <S>
   * @param <SB>
   */
  static class FactoryInliner extends Inliner {
    private final NodeFactory<? extends Node> factory;

    FactoryInliner(final Inline inline, final NodeFactory<? extends Node> factory) {
      super(inline, null);
      this.factory = factory;
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public <N extends Node> N create(final List<N> argNodes, final ScopeBuilder scopeBuilder,
        final SourceSection source) throws ProgramDefinitionError {
      Object[] args = new Object[argNodes.size() + inline.inlineableArgIdx().length
          + inline.introduceTemps().length + inline.additionalArgs().length];

      assert args.length == factory.getNodeSignatures().get(0).size();

      int restArgs = factory.getExecutionSignature().size();

      int i = 0;
      for (int j = 0; j < argNodes.size(); j += 1) {
        if (j < restArgs) {
          int endOffset = args.length - restArgs + j;
          args[endOffset] = argNodes.get(j);
        } else {
          args[i] = argNodes.get(j);
          i += 1;
        }
      }

      for (int a : inline.inlineableArgIdx()) {
        args[i] = ((Inlinable) argNodes.get(a)).inline(scopeBuilder);
        i += 1;
      }

      for (int a : inline.introduceTemps()) {
        args[i] = scopeBuilder.introduceTempForInlinedVersion(
            (Inlinable) argNodes.get(a), source);
      }

      for (Class<?> c : inline.additionalArgs()) {
        if (c == True.class) {
          args[i] = true;
        } else {
          assert c == False.class;
          args[i] = false;
        }
        i += 1;
      }

      N node = (N) factory.createNode(args);
      ((WithSource) node).initialize(source);
      return node;
    }
  }
}
