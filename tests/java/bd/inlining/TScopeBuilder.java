package bd.inlining;

import com.oracle.truffle.api.source.SourceSection;

import bd.basic.ProgramDefinitionError;
import bd.inlining.nodes.Inlinable;


public class TScopeBuilder implements ScopeBuilder<TScopeBuilder> {

  @Override
  public Variable<?> introduceTempForInlinedVersion(final Inlinable<TScopeBuilder> node,
      final SourceSection source) throws ProgramDefinitionError {
    return null;
  }
}
