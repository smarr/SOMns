package som.interpreter.transactions;

import java.util.IdentityHashMap;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

import som.vmobjects.SArray.SMutableArray;
import som.vmobjects.SObject.SMutableObject;


/**
 * Implements a simple software transactional memory system.
 *
 * <p>
 * The general idea is that all accesses to objects and arrays are mediated
 * via {@link Change} objects. These keep a copy of the initial state, and a
 * working copy on which the transactions perform their accesses.
 * On commit, we simply compare the initial state with the publicly visible
 * objects, if any change (conflict) is seen the transaction retries. If no
 * conflict was determined, the new state is copied into the publicly visible
 * object.
 *
 * <p>
 * Transactions always succeed, this implementation automatically retries.
 *
 * <p>
 * The initial creation of a {@link Change} object accesses the public object
 * while holding its lock. Similarly, on writing back changes, the lock is
 * acquired. Transaction commits are globally sequentialized on a single lock.
 *
 * <p>
 * Inspired by: Transactional Memory for Smalltalk
 * L. Renggli, and O. Nierstrasz. In Proc. of ICDL, 2007.
 * DOI: 10.1145/1352678.1352692
 */
public final class Transactions {

  private IdentityHashMap<SMutableObject, ObjectChange> objects;
  private IdentityHashMap<SMutableArray, ArrayChange>   arrays;

  private static Object globalCommitLock = new Object();

  private Transactions() {}

  private abstract static class Change {
    abstract boolean hasChange();

    abstract boolean hasConflict();

    abstract void applyChanges();
  }

  private static final class ObjectChange extends Change {
    private final SMutableObject publicObj;
    private final SMutableObject initialState;
    private final SMutableObject workingCopy;

    ObjectChange(final SMutableObject o) {
      synchronized (o) {
        publicObj = o;
        initialState = o.shallowCopy();
        workingCopy = o.shallowCopy();
      }
    }

    @Override
    boolean hasChange() {
      return !workingCopy.txEquals(initialState);
    }

    @Override
    boolean hasConflict() {
      return !publicObj.txEquals(initialState);
    }

    @Override
    void applyChanges() {
      synchronized (publicObj) {
        publicObj.txSet(workingCopy);
      }
    }
  }

  private static final class ArrayChange extends Change {
    private final SMutableArray publicArr;
    private final SMutableArray initialState;
    private final SMutableArray workingCopy;

    ArrayChange(final SMutableArray a) {
      synchronized (a) {
        publicArr = a;
        initialState = a.shallowCopy();
        workingCopy = a.shallowCopy();
      }
    }

    @Override
    boolean hasChange() {
      return !workingCopy.txEquals(initialState);
    }

    @Override
    boolean hasConflict() {
      return !publicArr.txEquals(initialState);
    }

    @Override
    void applyChanges() {
      synchronized (publicArr) {
        publicArr.txSet(workingCopy);
      }
    }
  }

  private void start() {
    objects = new IdentityHashMap<>();
    arrays = new IdentityHashMap<>();
  }

  private SMutableObject getWorkingCopy(final SMutableObject o) {
    ObjectChange change = objects.get(o);
    if (change == null) {
      change = new ObjectChange(o);
      objects.put(o, change);
    }
    return change.workingCopy;
  }

  private SMutableArray getWorkingCopy(final SMutableArray a) {
    ArrayChange change = arrays.get(a);
    if (change == null) {
      change = new ArrayChange(a);
      arrays.put(a, change);
    }
    return change.workingCopy;
  }

  private static final ThreadLocal<Transactions> transactions =
      new ThreadLocal<Transactions>() {
        @Override
        protected Transactions initialValue() {
          return new Transactions();
        }
      };

  @TruffleBoundary
  public static Transactions startTransaction() {
    Transactions t = transactions.get();
    t.start();
    return t;
  }

  /**
   * @return true on success, otherwise false.
   */
  @TruffleBoundary
  public boolean commit() {
    synchronized (globalCommitLock) {
      if (hasConflicts()) {
        return false;
      }
    }

    applyChanges();
    return true;
  }

  private boolean hasConflicts() {
    for (ObjectChange c : objects.values()) {
      if (c.hasConflict()) {
        return true;
      }
    }

    for (ArrayChange c : arrays.values()) {
      if (c.hasConflict()) {
        return true;
      }
    }

    return false;
  }

  private void applyChanges() {
    for (ObjectChange c : objects.values()) {
      if (c.hasChange()) {
        c.applyChanges();
      }
    }

    for (ArrayChange c : arrays.values()) {
      if (c.hasChange()) {
        c.applyChanges();
      }
    }
  }

  @TruffleBoundary
  public static SMutableObject workingCopy(final SMutableObject rcvr) {
    Transactions t = transactions.get();
    return t.getWorkingCopy(rcvr);
  }

  @TruffleBoundary
  public static SMutableArray workingCopy(final SMutableArray rcvr) {
    Transactions t = transactions.get();
    return t.getWorkingCopy(rcvr);
  }
}
