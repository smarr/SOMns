package bd.inlining.nodes;

import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeInterface;

import bd.inlining.ScopeAdaptationVisitor;


/**
 * All {@link Node} classes that potentially reference the scope in some way should implement
 * the {@link ScopeReference} interface.
 *
 * <p>A reference to the scope is typically an access to some type of variable, which might be
 * realized as an access to a {@link Frame} with a {@link FrameSlot}. More generally, it is any
 * reference that might need to be adjusted after scopes have been changed. Scope changes can
 * be cause by inlining, which usually means that some scopes get merged, or because of
 * splitting, which separates scopes. In either case, nodes with scope references need to be
 * adapted, which is done with {@link #replaceAfterScopeChange(ScopeAdaptationVisitor)}.
 */
public interface ScopeReference extends NodeInterface {

  /**
   * Replaces the current node with one that is adapted to match the a changed scope.
   *
   * <p>A scope change might have been caused by splitting or inlining. The
   * {@link ScopeAdaptationVisitor} provides access to the scope information, to obtain updated
   * scope references, e.g., valid variable or frame slot objects, or for convenience also
   * complete updated read/write nodes.
   *
   * @param visitor the {@link ScopeAdaptationVisitor} that manages the scope adaptation and
   *          provides access to scope information
   */
  void replaceAfterScopeChange(ScopeAdaptationVisitor visitor);
}
