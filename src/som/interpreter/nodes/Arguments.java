package som.interpreter.nodes;

import som.vmobjects.Object;

public final class Arguments extends com.oracle.truffle.api.Arguments {
  
  public final Object   self;
  public final Object[] arguments;
  
  public Arguments(final Object self, final Object[] arguments) {
    this.self      = self;
    this.arguments = arguments;
  }
}
