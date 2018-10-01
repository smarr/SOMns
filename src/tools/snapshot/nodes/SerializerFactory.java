package tools.snapshot.nodes;

import som.vmobjects.SClass;


@FunctionalInterface
public interface SerializerFactory {
  AbstractSerializationNode create(SClass clazz);
}
