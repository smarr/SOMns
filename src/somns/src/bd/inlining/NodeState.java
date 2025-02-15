package bd.inlining;

/**
 * This {@link NodeState} is used by the node factory methods of {@link Variable} to pass in
 * required state.
 * It is thus merely a marker interface.
 *
 * <p>Node state can typically include information about lexical bindings, for instance in form
 * of unique identifiers, which might be class names or ids of mixins or similar lexical
 * constructs.
 */
public interface NodeState {

}
