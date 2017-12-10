package tools.debugger.message;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Map.Entry;

import som.compiler.MixinDefinition.SlotDefinition;
import som.interpreter.Types;
import som.interpreter.objectstorage.StorageLocation;
import som.vm.constants.Nil;
import som.vmobjects.SArray;
import som.vmobjects.SArray.PartiallyEmptyArray;
import som.vmobjects.SObject;
import tools.TraceData;
import tools.debugger.frontend.RuntimeScope;
import tools.debugger.frontend.Suspension;
import tools.debugger.message.Message.Response;
import tools.debugger.message.VariablesRequest.FilterType;


@SuppressWarnings("unused")
public final class VariablesResponse extends Response {
  private final Variable[] variables;
  private final long       variablesReference;

  private VariablesResponse(final int requestId, final long globalVarRef,
      final Variable[] variables) {
    super(requestId);
    assert TraceData.isWithinJSIntValueRange(globalVarRef);
    this.variablesReference = globalVarRef;
    this.variables = variables;
  }

  private static class Variable {
    private final String name;
    private final String value;

    private final long variablesReference;
    private final int  namedVariables;
    private final int  indexedVariables;

    Variable(final String name, final String value, final long globalVarRef,
        final int named, final int indexed) {
      assert TraceData.isWithinJSIntValueRange(globalVarRef);
      this.name = name;
      this.value = value;
      this.variablesReference = globalVarRef;
      this.namedVariables = named;
      this.indexedVariables = indexed;
    }
  }

  public static VariablesResponse create(final long globalVarRef, final int requestId,
      final Suspension suspension, final FilterType filter, final Long start,
      final Long count) {
    Object scopeOrObject = suspension.getScopeOrObject(globalVarRef);
    ArrayList<Variable> results;
    if (scopeOrObject instanceof RuntimeScope) {
      assert start == null || start == 0 : "Don't support starting from non-0 index";
      results = createFromScope((RuntimeScope) scopeOrObject, suspension);
    } else {
      results = createFromObject(scopeOrObject, suspension, filter, start, count);
    }
    return new VariablesResponse(requestId, globalVarRef, results.toArray(new Variable[0]));
  }

  private static ArrayList<Variable> createFromObject(final Object obj,
      final Suspension suspension, final FilterType filter, final Long start,
      final Long count) {
    ArrayList<Variable> results = new ArrayList<>();

    if (obj instanceof SObject) {
      assert start == null || start == 0 : "Don't support starting from non-0 index";
      SObject o = (SObject) obj;
      for (Entry<SlotDefinition, StorageLocation> e : o.getObjectLayout().getStorageLocations()
                                                       .entrySet()) {
        results.add(
            createVariable(e.getKey().getName().getString(), e.getValue().read(o),
                suspension));
      }
    } else {
      int startIdx = start == null ? 0 : (int) (long) start;

      assert obj instanceof SArray;
      SArray arr = (SArray) obj;
      Object storage = arr.getStoragePlain();
      if (storage instanceof Integer) {
        long numItems = count == null ? (int) storage : count;
        for (int i = startIdx; i < numItems; i += 1) {
          results.add(createVariable("" + (i + 1), Nil.nilObject, suspension));
        }
      } else {
        if (storage instanceof PartiallyEmptyArray) {
          storage = ((PartiallyEmptyArray) storage).getStorage();
        }

        long numItems = count == null ? Array.getLength(storage) : count;
        for (int i = startIdx; i < numItems; i += 1) {
          results.add(createVariable("" + (i + 1), Array.get(storage, i), suspension));
        }
      }
    }
    return results;
  }

  private static ArrayList<Variable> createFromScope(final RuntimeScope scope,
      final Suspension suspension) {
    ArrayList<Variable> results = new ArrayList<>();
    for (som.compiler.Variable v : scope.getVariables()) {
      if (!v.isInternal()) {
        Object val = scope.read(v);
        results.add(createVariable(v.name.getString(), val, suspension));
      }
    }
    return results;
  }

  private static Variable createVariable(final String name, final Object val,
      final Suspension suspension) {
    int named = Types.getNumberOfNamedSlots(val);
    int indexed = Types.getNumberOfIndexedSlots(val);
    long id;
    if (named + indexed > 0) {
      id = suspension.addObject(val);
    } else {
      id = 0;
    }
    return new Variable(name, Types.toDebuggerString(val), id, named, indexed);
  }
}
