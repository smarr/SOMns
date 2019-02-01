/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

package som.interpreter.objectstorage;

import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

import tools.concurrency.TraceBuffer;


/**
 * A reusable synchronization barrier, similar in functionality to
 * {@link java.util.concurrent.CyclicBarrier CyclicBarrier} and
 * {@link java.util.concurrent.CountDownLatch CountDownLatch}
 * but supporting more flexible usage.
 *
 * <p>
 * <b>Registration.</b> Unlike the case for other barriers, the
 * number of parties <em>registered</em> to synchronize on a phaser
 * may vary over time. Tasks may be registered at any time (using
 * methods {@link #register}, {@link #bulkRegister}, or forms of
 * constructors establishing initial numbers of parties), and
 * optionally deregistered upon any arrival (using {@link
 * #arriveAndDeregister}). As is the case with most basic
 * synchronization constructs, registration and deregistration affect
 * only internal counts; they do not establish any further internal
 * bookkeeping, so tasks cannot query whether they are registered.
 * (However, you can introduce such bookkeeping by subclassing this
 * class.)
 *
 * <p>
 * <b>Synchronization.</b> Like a {@code CyclicBarrier}, a {@code
 * SafepointPhaser} may be repeatedly awaited. Method {@link
 * #arriveAndAwaitAdvance} has effect analogous to {@link
 * java.util.concurrent.CyclicBarrier#await CyclicBarrier.await}. Each
 * generation of a phaser has an associated phase number. The phase
 * number starts at zero, and advances when all parties arrive at the
 * phaser, wrapping around to zero after reaching {@code
 * Integer.MAX_VALUE}. The use of phase numbers enables independent
 * control of actions upon arrival at a phaser and upon awaiting
 * others, via two kinds of methods that may be invoked by any
 * registered party:
 *
 * <ul>
 *
 * <li><b>Arrival.</b> Methods {@link #arrive} and
 * {@link #arriveAndDeregister} record arrival. These methods
 * do not block, but return an associated <em>arrival phase
 * number</em>; that is, the phase number of the phaser to which
 * the arrival applied. When the final party for a given phase
 * arrives, an optional action is performed and the phase
 * advances. These actions are performed by the party
 * triggering a phase advance, and are arranged by overriding
 * method {@link #onAdvance(int, int)}, which also controls
 * termination. Overriding this method is similar to, but more
 * flexible than, providing a barrier action to a {@code
 *       CyclicBarrier}.
 *
 * <li><b>Waiting.</b> Method {@link #awaitAdvance} requires an
 * argument indicating an arrival phase number, and returns when
 * the phaser advances to (or is already at) a different phase.
 * Unlike similar constructions using {@code CyclicBarrier},
 * method {@code awaitAdvance} continues to wait even if the
 * waiting thread is interrupted. Interruptible and timeout
 * versions are also available, but exceptions encountered while
 * tasks wait interruptibly or with timeout do not change the
 * state of the phaser. If necessary, you can perform any
 * associated recovery within handlers of those exceptions,
 * often after invoking {@code forceTermination}. Phasers may
 * also be used by tasks executing in a {@link ForkJoinPool}.
 * Progress is ensured if the pool's parallelismLevel can
 * accommodate the maximum number of simultaneously blocked
 * parties.
 *
 * </ul>
 *
 * <p>
 * <b>Termination.</b> A phaser may enter a <em>termination</em>
 * state, that may be checked using method {@link #isTerminated}. Upon
 * termination, all synchronization methods immediately return without
 * waiting for advance, as indicated by a negative return value.
 * Similarly, attempts to register upon termination have no effect.
 * Termination is triggered when an invocation of {@code onAdvance}
 * returns {@code true}. The default implementation returns {@code
 * true} if a deregistration has caused the number of registered
 * parties to become zero. As illustrated below, when phasers control
 * actions with a fixed number of iterations, it is often convenient
 * to override this method to cause termination when the current phase
 * number reaches a threshold. Method {@link #forceTermination} is
 * also available to abruptly release waiting threads and allow them
 * to terminate.
 *
 * <p>
 * <b>Tiering.</b> Phasers may be <em>tiered</em> (i.e.,
 * constructed in tree structures) to reduce contention. Phasers with
 * large numbers of parties that would otherwise experience heavy
 * synchronization contention costs may instead be set up so that
 * groups of sub-phasers share a common parent. This may greatly
 * increase throughput even though it incurs greater per-operation
 * overhead.
 *
 * <p>
 * In a tree of tiered phasers, registration and deregistration of
 * child phasers with their parent are managed automatically.
 * Whenever the number of registered parties of a child phaser becomes
 * non-zero (as established in the {@link #SafepointPhaser(SafepointPhaser,int)}
 * constructor, {@link #register}, or {@link #bulkRegister}), the
 * child phaser is registered with its parent. Whenever the number of
 * registered parties becomes zero as the result of an invocation of
 * {@link #arriveAndDeregister}, the child phaser is deregistered
 * from its parent.
 *
 * <p>
 * <b>Monitoring.</b> While synchronization methods may be invoked
 * only by registered parties, the current state of a phaser may be
 * monitored by any caller. At any given moment there are {@link
 * #getRegisteredParties} parties in total, of which {@link
 * #getArrivedParties} have arrived at the current phase ({@link
 * #getPhase}). When the remaining ({@link #getUnarrivedParties})
 * parties arrive, the phase advances. The values returned by these
 * methods may reflect transient states and so are not in general
 * useful for synchronization control. Method {@link #toString}
 * returns snapshots of these state queries in a form convenient for
 * informal monitoring.
 *
 * <p>
 * <b>Sample usages:</b>
 *
 * <p>
 * A {@code SafepointPhaser} may be used instead of a {@code CountDownLatch}
 * to control a one-shot action serving a variable number of parties.
 * The typical idiom is for the method setting this up to first
 * register, then start the actions, then deregister, as in:
 *
 * <pre>
 *  {@code
 * void runTasks(List<Runnable> tasks) {
 *   final SafepointPhaser phaser = new SafepointPhaser(1); // "1" to register self
 *   // create and start threads
 *   for (final Runnable task : tasks) {
 *     phaser.register();
 *     new Thread() {
 *       public void run() {
 *         phaser.arriveAndAwaitAdvance(); // await all creation
 *         task.run();
 *       }
 *     }.start();
 *   }
 *
 *   // allow threads to start and deregister self
 *   phaser.arriveAndDeregister();
 * }}
 * </pre>
 *
 * <p>
 * One way to cause a set of threads to repeatedly perform actions
 * for a given number of iterations is to override {@code onAdvance}:
 *
 * <pre>
 *  {@code
 * void startTasks(List<Runnable> tasks, final int iterations) {
 *   final SafepointPhaser phaser = new SafepointPhaser() {
 *     protected boolean onAdvance(int phase, int registeredParties) {
 *       return phase >= iterations || registeredParties == 0;
 *     }
 *   };
 *   phaser.register();
 *   for (final Runnable task : tasks) {
 *     phaser.register();
 *     new Thread() {
 *       public void run() {
 *         do {
 *           task.run();
 *           phaser.arriveAndAwaitAdvance();
 *         } while (!phaser.isTerminated());
 *       }
 *     }.start();
 *   }
 *   phaser.arriveAndDeregister(); // deregister self, don't wait
 * }}
 * </pre>
 *
 * If the main task must later await termination, it
 * may re-register and then execute a similar loop:
 *
 * <pre>
 *  {@code
 *   // ...
 *   phaser.register();
 *   while (!phaser.isTerminated())
 *     phaser.arriveAndAwaitAdvance();}
 * </pre>
 *
 * <p>
 * Related constructions may be used to await particular phase numbers
 * in contexts where you are sure that the phase will never wrap around
 * {@code Integer.MAX_VALUE}. For example:
 *
 * <pre>
 *  {@code
 * void awaitPhase(SafepointPhaser phaser, int phase) {
 *   int p = phaser.register(); // assumes caller not already registered
 *   while (p < phase) {
 *     if (phaser.isTerminated())
 *       // ... deal with unexpected termination
 *     else
 *       p = phaser.arriveAndAwaitAdvance();
 *   }
 *   phaser.arriveAndDeregister();
 * }}
 * </pre>
 *
 * <p>
 * To create a set of {@code n} tasks using a tree of phasers, you
 * could use code of the following form, assuming a Task class with a
 * constructor accepting a {@code SafepointPhaser} that it registers with upon
 * construction. After invocation of {@code build(new Task[n], 0, n,
 * new SafepointPhaser())}, these tasks could then be started, for example by
 * submitting to a pool:
 *
 * <pre>
 *  {@code
 * void build(Task[] tasks, int lo, int hi, SafepointPhaser ph) {
 *   if (hi - lo > TASKS_PER_PHASER) {
 *     for (int i = lo; i < hi; i += TASKS_PER_PHASER) {
 *       int j = Math.min(i + TASKS_PER_PHASER, hi);
 *       build(tasks, i, j, new SafepointPhaser(ph));
 *     }
 *   } else {
 *     for (int i = lo; i < hi; ++i)
 *       tasks[i] = new Task(ph);
 *       // assumes new Task(ph) performs ph.register()
 *   }
 * }}
 * </pre>
 *
 * The best value of {@code TASKS_PER_PHASER} depends mainly on
 * expected synchronization rates. A value as low as four may
 * be appropriate for extremely small per-phase task bodies (thus
 * high rates), or up to hundreds for extremely large ones.
 *
 * <p>
 * <b>Implementation notes</b>: This implementation restricts the
 * maximum number of parties to 65535. Attempts to register additional
 * parties result in {@code IllegalStateException}. However, you can and
 * should create tiered phasers to accommodate arbitrarily large sets
 * of participants.
 *
 * @since 1.7
 * @author Doug Lea
 */
public final class SafepointPhaser {
  /*
   * This class implements an extension of X10 "clocks". Thanks to
   * Vijay Saraswat for the idea, and to Vivek Sarkar for
   * enhancements to extend functionality.
   */

  /**
   * Primary state representation, holding four bit-fields:
   *
   * unarrived -- the number of parties yet to hit barrier (bits 0-15)
   * parties -- the number of parties to wait (bits 16-31)
   * phase -- the generation of the barrier (bits 32-62)
   * terminated -- set if barrier is terminated (bit 63 / sign)
   *
   * Except that a phaser with no registered parties is
   * distinguished by the otherwise illegal state of having zero
   * parties and one unarrived parties (encoded as EMPTY below).
   *
   * To efficiently maintain atomicity, these values are packed into
   * a single (atomic) long. Good performance relies on keeping
   * state decoding and encoding simple, and keeping race windows
   * short.
   */
  private volatile long state;

  private final ObjectTransitionSafepoint safepoint;

  private static final int  MAX_PARTIES    = 0xffff;
  private static final int  MAX_PHASE      = Integer.MAX_VALUE;
  private static final int  PARTIES_SHIFT  = 16;
  private static final int  PHASE_SHIFT    = 32;
  private static final int  UNARRIVED_MASK = 0xffff;           // to mask ints
  private static final long PARTIES_MASK   = 0xffff0000L;      // to mask longs

  // some special values
  private static final int ONE_ARRIVAL    = 1;
  private static final int ONE_PARTY      = 1 << PARTIES_SHIFT;
  private static final int ONE_DEREGISTER = ONE_ARRIVAL | ONE_PARTY;
  private static final int EMPTY          = 1;

  // The following unpacking methods are usually manually inlined

  private static int partiesOf(final long s) {
    return (int) s >>> PARTIES_SHIFT;
  }

  private static int phaseOf(final long s) {
    return (int) (s >>> PHASE_SHIFT);
  }

  private static int arrivedOf(final long s) {
    int counts = (int) s;
    return (counts == EMPTY) ? 0 : (counts >>> PARTIES_SHIFT) - (counts & UNARRIVED_MASK);
  }

  /**
   * Heads of Treiber stacks for waiting threads. To eliminate
   * contention when releasing some threads while adding others, we
   * use two of them, alternating across even and odd phases.
   * Subphasers share queues with root to speed up releases.
   */
  private final AtomicReference<QNode> evenQ;
  private final AtomicReference<QNode> oddQ;

  /**
   * Returns message string for bounds exceptions on arrival.
   */
  private String badArrive(final long s) {
    return "Attempted arrival of unregistered party for " +
        stateToString(s);
  }

  /**
   * Returns message string for bounds exceptions on registration.
   */
  private String badRegister(final long s) {
    return "Attempt to register more than " +
        MAX_PARTIES + " parties for " + stateToString(s);
  }

  /**
   * Main implementation for methods arrive and arriveAndDeregister.
   * Manually tuned to speed up and minimize race windows for the
   * common case of just decrementing unarrived field.
   *
   * @param adjust value to subtract from state;
   *          ONE_ARRIVAL for arrive,
   *          ONE_DEREGISTER for arriveAndDeregister
   */
  private int doArrive(final int adjust) {
    for (;;) {
      long s = state;
      int phase = (int) (s >>> PHASE_SHIFT);
      if (phase < 0) {
        return phase;
      }
      int counts = (int) s;
      int unarrived = (counts == EMPTY) ? 0 : (counts & UNARRIVED_MASK);
      if (unarrived <= 0) {
        throw new IllegalStateException(badArrive(s));
      }
      if (U.compareAndSwapLong(this, STATE, s, s -= adjust)) {
        if (unarrived == 1) {
          long n = s & PARTIES_MASK; // base of next state
          int nextUnarrived = (int) n >>> PARTIES_SHIFT;
          if (nextUnarrived == 0) {
            n |= EMPTY;
          } else {
            n |= nextUnarrived;
          }
          int nextPhase = (phase + 1) & MAX_PHASE;
          onArrive(nextPhase);
          n |= (long) nextPhase << PHASE_SHIFT;
          U.compareAndSwapLong(this, STATE, s, n);
          releaseWaiters(phase);
        }
        return phase;
      }
    }
  }

  /**
   * Implementation of register, bulkRegister.
   *
   * @param registrations number to add to both parties and
   *          unarrived fields. Must be greater than zero.
   */
  private int doRegister(final int registrations) {
    // adjustment to state
    long adjust = ((long) registrations << PARTIES_SHIFT) | registrations;
    int phase;
    for (;;) {
      long s = state;
      int counts = (int) s;
      int parties = counts >>> PARTIES_SHIFT;
      int unarrived = counts & UNARRIVED_MASK;
      if (registrations > MAX_PARTIES - parties) {
        throw new IllegalStateException(badRegister(s));
      }
      phase = (int) (s >>> PHASE_SHIFT);
      if (phase < 0) {
        break;
      }
      if (counts != EMPTY) { // not 1st registration
        if (unarrived == 0) {
          internalAwaitAdvance(phase, null);
        } else if (U.compareAndSwapLong(this, STATE, s, s + adjust)) {
          break;
        }
      } else {
        long next = ((long) phase << PHASE_SHIFT) | adjust;
        if (U.compareAndSwapLong(this, STATE, s, next)) {
          break;
        }
      }
    }
    return phase;
  }

  /**
   * Creates a new phaser without unarrived parties.
   */
  public SafepointPhaser(final ObjectTransitionSafepoint safepoint) {
    this.safepoint = safepoint;
    this.evenQ = new AtomicReference<QNode>();
    this.oddQ = new AtomicReference<QNode>();
    this.state = EMPTY;
  }

  /**
   * Adds a new unarrived party to this phaser. If an ongoing
   * invocation of {@link #onAdvance} is in progress, this method
   * may await its completion before returning. If this phaser has
   * a parent, and this phaser previously had no registered parties,
   * this child phaser is also registered with its parent. If
   * this phaser is terminated, the attempt to register has
   * no effect, and a negative value is returned.
   *
   * @return the arrival phase number to which this registration
   *         applied. If this value is negative, then this phaser has
   *         terminated, in which case registration has no effect.
   * @throws IllegalStateException if attempting to register more
   *           than the maximum supported number of parties
   */
  void register() {
    int phase = doRegister(1);

    if ((phase & 1) == 1) { // isSafepointStarted(phase)
      finishSafepointAndAwaitCompletion();
    }
  }

  /**
   * Arrives at this phaser and deregisters from it without waiting
   * for others to arrive. Deregistration reduces the number of
   * parties required to advance in future phases. If this phaser
   * has a parent, and deregistration causes this phaser to have
   * zero parties, this phaser is also deregistered from its parent.
   *
   * <p>
   * It is a usage error for an unregistered party to invoke this
   * method. However, this error may result in an {@code
   * IllegalStateException} only upon some subsequent operation on
   * this phaser, if ever.
   *
   * @return the arrival phase number, or a negative value if terminated
   * @throws IllegalStateException if not terminated and the number
   *           of registered or unarrived parties would become negative
   */
  int arriveAndDeregister() {
    return doArrive(ONE_DEREGISTER);
  }

  void performSafepoint() {
    arriveAtSafepointAndAwaitStart();
    finishSafepointAndAwaitCompletion();
  }

  void arriveAtSafepointAndAwaitStart() {
    int phase = arriveAndAwaitAdvance();
    assert (phase & 1) == 1 : "Expect phase to be odd after start of safepoint, but was "
        + phase;
  }

  void finishSafepointAndAwaitCompletion() {
    int phase = arriveAndAwaitAdvance();
    assert (phase & 1) == 0 : "Expect phase to be evem on completion of safepoint, but was "
        + phase;
  }

  void onArrive(final int phase) {
    if ((phase & 1) == 0) { // safepoint to be completed
      safepoint.renewAssumption();
    }
  }

  /**
   * Arrives at this phaser and awaits others. Equivalent in effect
   * to {@code awaitAdvance(arrive())}. If you need to await with
   * interruption or timeout, you can arrange this with an analogous
   * construction using one of the other forms of the {@code
   * awaitAdvance} method. If instead you need to deregister upon
   * arrival, use {@code awaitAdvance(arriveAndDeregister())}.
   *
   * <p>
   * It is a usage error for an unregistered party to invoke this
   * method. However, this error may result in an {@code
   * IllegalStateException} only upon some subsequent operation on
   * this phaser, if ever.
   *
   * @return the arrival phase number, or the (negative)
   *         {@linkplain #getPhase() current phase} if terminated
   * @throws IllegalStateException if not terminated and the number
   *           of unarrived parties would become negative
   */
  private int arriveAndAwaitAdvance() {
    // Specialization of doArrive+awaitAdvance eliminating some reads/paths
    for (;;) {
      long s = state;
      int phase = (int) (s >>> PHASE_SHIFT);
      if (phase < 0) {
        return phase;
      }
      int counts = (int) s;
      int unarrived = (counts == EMPTY) ? 0 : (counts & UNARRIVED_MASK);
      if (unarrived <= 0) {
        throw new IllegalStateException(badArrive(s));
      }
      if (U.compareAndSwapLong(this, STATE, s, s -= ONE_ARRIVAL)) {
        if (unarrived > 1) {
          return internalAwaitAdvance(phase, null);
        }
        long n = s & PARTIES_MASK; // base of next state
        int nextUnarrived = (int) n >>> PARTIES_SHIFT;
        if (nextUnarrived == 0) {
          n |= EMPTY;
        } else {
          n |= nextUnarrived;
        }
        int nextPhase = (phase + 1) & MAX_PHASE;
        onArrive(nextPhase);
        n |= (long) nextPhase << PHASE_SHIFT;
        if (!U.compareAndSwapLong(this, STATE, s, n)) {
          return (int) (state >>> PHASE_SHIFT); // terminated
        }
        releaseWaiters(phase);
        return nextPhase;
      }
    }
  }

  /**
   * Returns the current phase number. The maximum phase number is
   * {@code Integer.MAX_VALUE}, after which it restarts at
   * zero. Upon termination, the phase number is negative,
   * in which case the prevailing phase prior to termination
   * may be obtained via {@code getPhase() + Integer.MIN_VALUE}.
   *
   * @return the phase number, or a negative value if terminated
   */
  private int getPhase() {
    return (int) (state >>> PHASE_SHIFT);
  }

  /**
   * Returns a string identifying this phaser, as well as its
   * state. The state, in brackets, includes the String {@code
   * "phase = "} followed by the phase number, {@code "parties = "}
   * followed by the number of registered parties, and {@code
   * "arrived = "} followed by the number of arrived parties.
   *
   * @return a string identifying this phaser, as well as its state
   */
  @Override
  public String toString() {
    return stateToString(state);
  }

  /**
   * Implementation of toString and string-based error messages.
   */
  private String stateToString(final long s) {
    return super.toString() +
        "[phase = " + phaseOf(s) +
        " parties = " + partiesOf(s) +
        " arrived = " + arrivedOf(s) + "]";
  }

  // Waiting mechanics

  /**
   * Removes and signals threads from queue for phase.
   */
  private void releaseWaiters(final int phase) {
    QNode q; // first element of queue
    Thread t; // its thread
    AtomicReference<QNode> head = (phase & 1) == 0 ? evenQ : oddQ;
    while ((q = head.get()) != null &&
        q.phase != (int) (state >>> PHASE_SHIFT)) {
      if (head.compareAndSet(q, q.next) &&
          (t = q.thread) != null) {
        q.thread = null;
        LockSupport.unpark(t);
      }
    }
  }

  /**
   * Variant of releaseWaiters that additionally tries to remove any
   * nodes no longer waiting for advance due to timeout or
   * interrupt. Currently, nodes are removed only if they are at
   * head of queue, which suffices to reduce memory footprint in
   * most usages.
   *
   * @return current phase on exit
   */
  private int abortWait(final int phase) {
    AtomicReference<QNode> head = (phase & 1) == 0 ? evenQ : oddQ;
    for (;;) {
      Thread t;
      QNode q = head.get();
      int p = (int) (state >>> PHASE_SHIFT);
      if (q == null || ((t = q.thread) != null && q.phase == p)) {
        return p;
      }
      if (head.compareAndSet(q, q.next) && t != null) {
        q.thread = null;
        LockSupport.unpark(t);
      }
    }
  }

  /** The number of CPUs, for spin control. */
  private static final int NCPU = Runtime.getRuntime().availableProcessors();

  /**
   * The number of times to spin before blocking while waiting for
   * advance, per arrival while waiting. On multiprocessors, fully
   * blocking and waking up a large number of threads all at once is
   * usually a very slow process, so we use rechargeable spins to
   * avoid it when threads regularly arrive: When a thread in
   * internalAwaitAdvance notices another arrival before blocking,
   * and there appear to be enough CPUs available, it spins
   * SPINS_PER_ARRIVAL more times before blocking. The value trades
   * off good-citizenship vs big unnecessary slowdowns.
   */
  private static final int SPINS_PER_ARRIVAL = (NCPU < 2) ? 1 : 1 << 8;

  /**
   * Possibly blocks and waits for phase to advance unless aborted.
   * Call only on root phaser.
   *
   * @param phase current phase
   * @param node if non-null, the wait node to track interrupt and timeout;
   *          if null, denotes noninterruptible wait
   * @return current phase
   */
  private int internalAwaitAdvance(final int phase, QNode node) {
    releaseWaiters(phase - 1); // ensure old queue clean
    boolean queued = false; // true when node is enqueued
    int lastUnarrived = 0; // to increase spins upon change
    int spins = SPINS_PER_ARRIVAL;
    long s;
    int p;
    while ((p = (int) ((s = state) >>> PHASE_SHIFT)) == phase) {
      if (node == null) { // spinning in noninterruptible mode
        int unarrived = (int) s & UNARRIVED_MASK;
        if (unarrived != lastUnarrived &&
            (lastUnarrived = unarrived) < NCPU) {
          spins += SPINS_PER_ARRIVAL;
        }
        boolean interrupted = Thread.interrupted();
        if (interrupted || --spins < 0) { // need node to record intr
          node = new QNode(this, phase);
          node.wasInterrupted = interrupted;
        }
      } else if (node.isReleasable()) {
        break;
      } else if (!queued) { // push onto queue
        AtomicReference<QNode> head = (phase & 1) == 0 ? evenQ : oddQ;
        QNode q = node.next = head.get();
        if ((q == null || q.phase == phase) &&
            (int) (state >>> PHASE_SHIFT) == phase) {
          queued = head.compareAndSet(q, node);
        }
      } else {
        while (!node.isReleasable() && !node.block()) {
          // nothing to do but to spin
        }
      }
    }

    if (node != null) {
      if (node.thread != null) {
        node.thread = null; // avoid need for unpark()
      }
      if (node.wasInterrupted) {
        Thread.currentThread().interrupt();
      }
      if (p == phase && (p = (int) (state >>> PHASE_SHIFT)) == phase) {
        return abortWait(phase); // possibly clean up on abort
      }
    }
    releaseWaiters(phase);
    return p;
  }

  /**
   * Wait nodes for Treiber stack representing wait queue.
   */
  private static final class QNode {
    private final SafepointPhaser phaser;
    private final int             phase;
    private boolean               wasInterrupted;
    private volatile Thread       thread;        // nulled to cancel wait
    private QNode                 next;

    QNode(final SafepointPhaser phaser, final int phase) {
      this.phaser = phaser;
      this.phase = phase;
      thread = Thread.currentThread();
    }

    boolean isReleasable() {
      if (thread == null) {
        return true;
      }
      if (phaser.getPhase() != phase) {
        thread = null;
        return true;
      }
      if (Thread.interrupted()) {
        wasInterrupted = true;
      }
      if (wasInterrupted) {
        thread = null;
        return true;
      }
      return false;
    }

    boolean block() {
      while (!isReleasable()) {
        LockSupport.park(this);
      }
      return true;
    }
  }

  // Unsafe mechanics

  private static final sun.misc.Unsafe U = TraceBuffer.UNSAFE;
  private static final long            STATE;
  static {
    try {
      STATE = U.objectFieldOffset(SafepointPhaser.class.getDeclaredField("state"));
    } catch (ReflectiveOperationException e) {
      throw new Error(e);
    }
  }
}
