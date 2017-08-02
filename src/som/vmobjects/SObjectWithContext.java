package som.vmobjects;

import com.oracle.truffle.api.frame.MaterializedFrame;

import som.interpreter.SArguments;

/**
 * This role is implement by both SBlock and SClass and provides
 * the behavior for stepping outward through a block or object
 * literal's activations.
 *
 * <p>When a block or object literal is nested inside of another
 * block, a method, or an object literal its context is the activation
 * of that block, method, or object literal.
 */

public interface SObjectWithContext {

  /**
   * Return the block or object literal's enclosing activation, referred to
   * here as a <em>context</em> since it is surrounding/enclosing the current
   * object.
   */
  MaterializedFrame getContext();

  /**
   * Return the object enclosing the current object,
   * which is the receiver of this object.
   */
  default SObjectWithContext getOuterSelf() {
    return (SObjectWithContext) SArguments.rcvr(getContext());
  }

}
