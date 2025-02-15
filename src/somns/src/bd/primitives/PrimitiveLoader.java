package bd.primitives;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.List;

import com.oracle.truffle.api.dsl.NodeFactory;

import bd.basic.IdProvider;
import bd.settings.VmSettings;


/**
 * A PrimitiveLoader provides the basic functionality to load the information about primitives
 * from the annotation, based on a list of {@link NodeFactory} objects.
 *
 * @param <Context> the type of the context object
 * @param <ExprT> the root type of expressions used by the language
 * @param <Id> the type of the identifiers used for mapping to primitives, typically some form
 *          of interned string construct
 */
public abstract class PrimitiveLoader<Context, ExprT, Id> {

  protected final IdProvider<Id> ids;

  /** Primitives for selector. */
  private final HashMap<Id, Specializer<Context, ExprT, Id>> eagerPrimitives;

  /**
   * Initializes the PrimitiveLoader.
   *
   * @param context the object representing the language's context
   */
  protected PrimitiveLoader(final IdProvider<Id> ids) {
    this.ids = ids;
    this.eagerPrimitives = new HashMap<>();
  }

  public static <Context, ExprT, Id> void add(final List<Specializer<Context, ExprT, Id>> list,
      final NodeFactory<? extends ExprT> factory) {
    Primitive[] primitives = getPrimitiveAnnotation(factory);
    if (primitives != null && primitives.length != 0) {
      for (bd.primitives.Primitive prim : primitives) {
        Specializer<Context, ExprT, Id> specializer = createSpecializer(prim, factory);
        list.add(specializer);
      }
    }
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  public static <Context, Id, ExprT> void addAll(
      final List<Specializer<Context, ExprT, Id>> list,
      final List toAdd) {
    for (Object factory : toAdd) {
      add(list, (NodeFactory<? extends ExprT>) factory);
    }
  }

  /** Returns all node specializers. */
  protected abstract List<Specializer<Context, ExprT, Id>> getSpecializers();

  /**
   * Setup the lookup data structures for VM primitive registration as well as
   * eager primitive replacement.
   *
   * <p>
   * This methods should be called when the constructor completes.
   */
  protected void initialize() {
    List<Specializer<Context, ExprT, Id>> specializers = getSpecializers();
    for (Specializer<Context, ExprT, Id> s : specializers) {
      // TODO: figure out whether we really want it like this with a VmSetting, or whether
      // there should be something on the context
      if (s.getPrimitive().disabled() && VmSettings.DYNAMIC_METRICS) {
        continue;
      }

      registerPrimitive(s);

      String sel = s.getPrimitive().selector();

      if (!("".equals(sel))) {
        Id selector = ids.getId(sel);
        assert !eagerPrimitives.containsKey(
            selector) : "clash of selectors and eager specialization";
        eagerPrimitives.put(selector, s);
      }
    }
  }

  /**
   * {@link #initialize()} iterates over all factories and consequently primitives provided by
   * {@link #getFactories()}. These primitives are exposed by this method to allow custom
   * handling, for instance to install them in language-level classes.
   *
   * @param prim the primitive annotation
   * @param specializer the specializer object for this primitive
   */
  protected abstract void registerPrimitive(Specializer<Context, ExprT, Id> specializer);

  /**
   * Lookup a specializer for use during parsing.
   *
   * <p>
   * It is identified by a selector or id, and the argument nodes. If the lookup is successful,
   * the primitive allows in-parser specialization, and the argument nodes match the
   * expectations, than a specializer is returned. otherwise, null is returned.
   */
  public final Specializer<Context, ExprT, Id> getParserSpecializer(final Id selector,
      final ExprT[] argNodes) {
    Specializer<Context, ExprT, Id> specializer = eagerPrimitives.get(selector);
    if (specializer != null && specializer.inParser() && specializer.matches(null, argNodes)) {
      return specializer;
    }
    return null;
  }

  /**
   * Lookup a specializer for specialization during execution.
   *
   * <p>
   * If one is found for the given selector/id, it is checked whether the run-time arguments as
   * well as the argument nodes match for the specialization. If they match, the specializer is
   * returned, null is returned otherwise.
   */
  public final Specializer<Context, ExprT, Id> getEagerSpecializer(final Id selector,
      final Object[] arguments, final ExprT[] argumentNodes) {
    Specializer<Context, ExprT, Id> specializer = eagerPrimitives.get(selector);
    if (specializer != null && specializer.matches(arguments, argumentNodes)) {
      return specializer;
    }
    return null;
  }

  /**
   * Create a {@link Specializer} for the given {@link Primitive}.
   */
  @SuppressWarnings("unchecked")
  private static <Context, ExprT, Id, T> Specializer<Context, ExprT, Id> createSpecializer(
      final Primitive prim, final NodeFactory<? extends ExprT> factory) {
    try {
      return prim.specializer()
                 .getConstructor(Primitive.class, NodeFactory.class)
                 .newInstance(prim, factory);
    } catch (InstantiationException | IllegalAccessException | IllegalArgumentException
        | InvocationTargetException | NoSuchMethodException | SecurityException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Get the {@link Primitive} annotation from a {@link NodeFactory}.
   */
  static <ExprT> Primitive[] getPrimitiveAnnotation(
      final NodeFactory<? extends ExprT> factory) {
    Class<?> nodeClass = factory.getNodeClass();
    return nodeClass.getAnnotationsByType(Primitive.class);
  }
}
