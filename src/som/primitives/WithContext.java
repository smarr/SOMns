package som.primitives;

import som.VM;


public interface WithContext<T> {
  T initialize(VM vm);
}
