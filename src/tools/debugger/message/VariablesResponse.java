package tools.debugger.message;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Map.Entry;
import java.util.Objects;

import som.compiler.MixinDefinition.SlotDefinition;
import som.compiler.Variable.Argument;
import som.compiler.Variable.Local;
import som.interpreter.Types;
import som.interpreter.objectstorage.StorageLocation;
import som.vm.constants.Nil;
import som.vmobjects.SArray;
import som.vmobjects.SArray.PartiallyEmptyArray;
import som.vmobjects.SObject;
import tools.debugger.frontend.RuntimeScope;
import tools.debugger.frontend.Suspension;
import tools.debugger.message.Message.Response;


@SuppressWarnings("unused")
public final class VariablesResponse extends Response {
  private final Variable[] variables;

  private VariablesResponse(final int requestId, final Variable[] variables) {
    super(requestId);
    this.variables = variables;
  }

  private static class Variable {
    private final String name;
    private final String value;

    private final int variablesReference;
    private final int namedVariables;
    private final int indexedVariables;

    Variable(final String name, final String value, final int varRef,
        final int named, final int indexed) {
      this.name = name;
      this.value = value;
      this.variablesReference = varRef;
      this.namedVariables = named;
      this.indexedVariables = indexed;
    }
  }

  public static VariablesResponse create(final int varRef, final int requestId,
      final Suspension suspension) {
    Object scopeOrObject = suspension.getScopeOrObject(varRef);
    ArrayList<Variable> results;
    if (scopeOrObject instanceof RuntimeScope) {
      results = createFromScope((RuntimeScope) scopeOrObject, suspension);
    } else {
      results = createFromObject(scopeOrObject, suspension);
    }
    return new VariablesResponse(requestId, results.toArray(new Variable[0]));
  }

  private static ArrayList<Variable> createFromObject(final Object obj,
      final Suspension suspension) {
    ArrayList<Variable> results = new ArrayList<>();

    if (obj instanceof SObject) {
      SObject o = (SObject) obj;
      for (Entry<SlotDefinition, StorageLocation> e :
          o.getObjectLayout().getStorageLocations().entrySet()) {
        results.add(createVariable(
            e.getKey().getName().getString(), e.getValue().read(o), suspension));
      }
    } else {
      assert obj instanceof SArray;
      SArray arr = (SArray) obj;
      Object storage = arr.getStoragePlain();
      if (storage instanceof Integer) {
        for (int i = 0; i < (int) storage; i += 1) {
          results.add(createVariable("" + (i + 1), Nil.nilObject, suspension));
        }
      } else {
        if (storage instanceof PartiallyEmptyArray) {
          storage = ((PartiallyEmptyArray) storage).getStorage();
        }

        int length = Array.getLength(storage);
        for (int i = 0; i < length; i += 1) {
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
      Object val;
      if (v instanceof Argument) {
        val = scope.getArgument(((Argument) v).index);
      } else if (v instanceof Local) {
        val = scope.getLocal(((Local) v).getSlot());
      } else {
        continue;
      }
      results.add(createVariable(v.name, val, suspension));
    }
    return results;
  }

  private static Variable createVariable(final String name, final Object val,
      final Suspension suspension) {
    int named   = Types.getNumberOfNamedSlots(val);
    int indexed = Types.getNumberOfIndexedSlots(val);
    int id;
    if (named + indexed > 0) {
      id = suspension.addObject(val);
    } else {
      id = 0;
    }
    return new Variable(name, Objects.toString(val), id, named, indexed);
  }
}
