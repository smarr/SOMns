package som.vmobjects;

import com.oracle.truffle.api.frame.MaterializedFrame;

public interface SObjectWithContext {

  MaterializedFrame getContext();
  Object getContextualSelf();

}

