package gson;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.internal.Streams;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;


/**
 * This is a GSON {@link TypeAdapterFactory} to support common class-based
 * hierarchies in JSON encodings.
 *
 * <p>One field of the JSON object is used to represent the type, which allows
 * automatic serialization and deserialization.
 */
public class ClassHierarchyAdapterFactory<T> implements TypeAdapterFactory {
  private final Class<T> baseType;
  private final String   typeField;

  private final Map<String, Class<? extends T>> types;

  public ClassHierarchyAdapterFactory(final Class<T> baseType, final String typeField) {
    this.baseType  = baseType;
    this.typeField = typeField;
    this.types = new LinkedHashMap<>();
  }

  /**
   * Register a type by its simple name.
   */
  public void register(final Class<? extends T> type) {
    assert type.getSimpleName().length() > 0 : "Type should have a non-empty name";
    types.put(type.getSimpleName(), type);
  }

  public void register(final String typeId, final Class<? extends T> type) {
    types.put(typeId, type);
  }

  @SuppressWarnings({ "unchecked", "rawtypes" })
  private void populateMaps(final Gson gson,
      final Map<String, TypeAdapter<? extends T>> actionToDelegate,
      final Map<Class<? extends T>, Pair<T>> subtypeToDelegate) {
    for (Entry<String, Class<? extends T>> e : types.entrySet()) {
      TypeAdapter<? extends T> delegate = gson.getDelegateAdapter(
          this, TypeToken.get(e.getValue()));
      actionToDelegate.put(e.getKey(), delegate);
      subtypeToDelegate.put(e.getValue(), new Pair(delegate, e.getKey()));
    }
  }

  @SuppressWarnings("unchecked")
  @Override
  public <R> TypeAdapter<R> create(final Gson gson, final TypeToken<R> type) {
    if (type.getRawType() != baseType) {
      return null;
    }

    final Map<String, TypeAdapter<? extends T>> actionToDelegate  = new LinkedHashMap<>();
    final Map<Class<? extends T>, Pair<T>>      subtypeToDelegate = new LinkedHashMap<>();

    populateMaps(gson, actionToDelegate, subtypeToDelegate);

    return (Adapter<R>) new Adapter<T>(typeField, actionToDelegate, subtypeToDelegate);
  }

  private static class Adapter<T> extends TypeAdapter<T> {
    private final Map<String, TypeAdapter<? extends T>> actionToDelegate;
    private final Map<Class<? extends T>, Pair<T>>      subtypeToDelegate;
    private final String typeField;

    Adapter(final String typeField,
        final Map<String, TypeAdapter<? extends T>> actionToDelegate,
        final Map<Class<? extends T>, Pair<T>> subtypeToDelegate) {
      this.typeField = typeField;
      this.actionToDelegate  = actionToDelegate;
      this.subtypeToDelegate = subtypeToDelegate;
    }

    @Override
    public void write(final JsonWriter out, final T value) throws IOException {
      Pair<T> pair = subtypeToDelegate.get(value.getClass());
      JsonObject obj = pair.adapter.toJsonTree(value).getAsJsonObject();
      assert !obj.has(typeField);
      obj.addProperty(typeField, pair.typeId);
      Streams.write(obj, out);
    }

    @Override
    public T read(final JsonReader in) throws IOException {
      JsonElement jsonElement = Streams.parse(in);
      JsonElement typeElement = jsonElement.getAsJsonObject().remove(typeField);

      String typeId = typeElement.getAsString();
      TypeAdapter<? extends T> delegate = actionToDelegate.get(typeId);
      return delegate.fromJsonTree(jsonElement);
    }
  }

  private static final class Pair<T> {
    public final TypeAdapter<T> adapter;
    public final String typeId;

    @SuppressWarnings("unchecked")
    Pair(final TypeAdapter<? extends T> adapter, final String typeId) {
      this.adapter = (TypeAdapter<T>) adapter;
      this.typeId  = typeId;
    }
  }
}
