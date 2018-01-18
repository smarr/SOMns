package som.vmobjects;

import java.sql.Connection;
import java.sql.PreparedStatement;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;


public class SDerbyConnection extends SObjectWithClass {
  @CompilationFinal public static SClass derbyConnectionClass;
  private final Connection               connection;

  public SDerbyConnection(final Connection c) {
    super(derbyConnectionClass, derbyConnectionClass.getInstanceFactory());
    this.connection = c;
  }

  @Override
  public boolean isValue() {
    return true;
  }

  public static void setSOMClass(final SClass cls) {
    derbyConnectionClass = cls;
  }

  public Connection getConnection() {
    return connection;
  }

  public static class SDerbyPreparedStatement extends SObjectWithClass {
    @CompilationFinal public static SClass derbyPreparedStatementClass;
    private final PreparedStatement        statement;

    public SDerbyPreparedStatement(final PreparedStatement statement) {
      super(derbyPreparedStatementClass, derbyPreparedStatementClass.getInstanceFactory());
      this.statement = statement;
    }

    public static void setSOMClass(final SClass cls) {
      derbyPreparedStatementClass = cls;
    }

    public PreparedStatement getStatement() {
      return statement;
    }

    @Override
    public boolean isValue() {
      return true;
    }
  }
}
