package som.vmobjects;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.concurrent.ForkJoinPool;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;

import som.interpreter.SomLanguage;
import som.interpreter.actors.Actor;
import som.interpreter.actors.EventualMessage;


public class SDerbyConnection extends SObjectWithClass {
  @CompilationFinal public static SClass derbyConnectionClass;
  private final Connection               connection;
  private final Actor                    derbyActor;
  private final ForkJoinPool             actorPool;
  private final SomLanguage              language;

  public SDerbyConnection(final Connection c, final ForkJoinPool actorPool,
      final SomLanguage language) {
    super(derbyConnectionClass, derbyConnectionClass.getInstanceFactory());
    this.connection = c;
    this.derbyActor = EventualMessage.getActorCurrentMessageIsExecutionOn();
    this.actorPool = actorPool;
    this.language = language;
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

  public Actor getDerbyActor() {
    return derbyActor;
  }

  public ForkJoinPool getActorPool() {
    return actorPool;
  }

  public SomLanguage getLanguage() {
    return language;
  }

  public static class SDerbyPreparedStatement extends SObjectWithClass {
    @CompilationFinal public static SClass derbyPreparedStatementClass;
    private final PreparedStatement        statement;
    protected final SDerbyConnection       connection;

    public SDerbyPreparedStatement(final PreparedStatement statement,
        final SDerbyConnection connection) {
      super(derbyPreparedStatementClass, derbyPreparedStatementClass.getInstanceFactory());
      this.statement = statement;
      this.connection = connection;
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

    public ForkJoinPool getActorPool() {
      return connection.actorPool;
    }

    public SomLanguage getSomLangauge() {
      return connection.language;
    }

    public Actor getDerbyActor() {
      return connection.derbyActor;
    }
  }
}
