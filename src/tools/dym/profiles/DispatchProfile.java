package tools.dym.profiles;

import java.util.Map;

import som.interpreter.Invokable;


public interface DispatchProfile {
  /** The passed result map will be filled with the information about activated invokables. */
  void collectDispatchStatistics(Map<Invokable, Integer> result);
}
