package som.primitives;

import java.sql.Array;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;

import bd.primitives.Primitive;
import som.interpreter.nodes.dispatch.BlockDispatchNode;
import som.interpreter.nodes.dispatch.BlockDispatchNodeGen;
import som.interpreter.nodes.nary.BinaryComplexOperation;
import som.interpreter.nodes.nary.QuaternaryExpressionNode;
import som.interpreter.nodes.nary.TernaryExpressionNode;
import som.interpreter.nodes.nary.UnaryExpressionNode;
import som.primitives.arrays.AtPrim;
import som.primitives.arrays.AtPrimFactory;
import som.vm.Symbols;
import som.vm.constants.Classes;
import som.vm.constants.Nil;
import som.vmobjects.SArray;
import som.vmobjects.SArray.SImmutableArray;
import som.vmobjects.SBlock;
import som.vmobjects.SClass;
import som.vmobjects.SDerbyConnection;
import som.vmobjects.SDerbyConnection.SDerbyPreparedStatement;


public final class DerbyPrims {
  private final static String DRIVER = "org.apache.derby.jdbc.EmbeddedDriver";

  @GenerateNodeFactory
  @ImportStatic(DerbyPrims.class)
  @Primitive(primitive = "derbyConnectionClass:")
  public abstract static class SetDerbyConnectionClassPrim extends UnaryExpressionNode {
    @Specialization
    public final SClass setClass(final SClass value) {
      SDerbyConnection.setSOMClass(value);
      return value;
    }
  }

  @GenerateNodeFactory
  @ImportStatic(DerbyPrims.class)
  @Primitive(primitive = "derbyPrepStatementClass:")
  public abstract static class SetDerbyPrepStatementClassPrim extends UnaryExpressionNode {
    @Specialization
    public final SClass setClass(final SClass value) {
      SDerbyPreparedStatement.setSOMClass(value);
      return value;
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "derbyStart:")
  public abstract static class StartDerbyPrim extends UnaryExpressionNode {
    @Specialization
    @TruffleBoundary
    public final Object doStart(final Object o) {
      try {
        Class.forName(DRIVER);
      } catch (ClassNotFoundException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
      System.out.println("Derby system start");
      return o;
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "derbyStop:")
  public abstract static class StopDerbyPrim extends UnaryExpressionNode {
    @Specialization
    @TruffleBoundary
    public final Object doStop(final Object o) {
      try {
        DriverManager.getConnection("jdbc:derby:;shutdown=true");
      } catch (SQLException e) {
        System.out.println("Derby system shutdown");
      }

      return o;
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "derbyGetConnection:ifFail:")
  public abstract static class DerbyGetConnectionPrim extends BinaryComplexOperation {
    public final String                PROTOCOL        = "jdbc:derby:derby/";
    @Child protected BlockDispatchNode dispatchHandler = BlockDispatchNodeGen.create();

    @Specialization
    @TruffleBoundary
    public final Object getConnection(final String dbName, final SBlock fail) {

      try {
        Connection conn =
            DriverManager.getConnection(PROTOCOL + dbName + ";create=true", null);

        return new SDerbyConnection(conn);
      } catch (SQLException e) {
        return dispatchHandler.executeDispatch(new Object[] {fail,
            Symbols.symbolFor("SQLException" + e.getErrorCode()), e.getMessage()});
      }
    }
  }

  @GenerateNodeFactory
  @ImportStatic(DerbyPrims.class)
  @Primitive(primitive = "derby:prepareStatement:ifFail:")
  public abstract static class DerbyPrepareStatementPrim extends TernaryExpressionNode {
    public final String                PROTOCOL        = "jdbc:derby:";
    @Child protected BlockDispatchNode dispatchHandler = BlockDispatchNodeGen.create();

    @Specialization
    @TruffleBoundary
    public final Object getConnection(final SDerbyConnection conn, final String query,
        final SBlock fail) {
      PreparedStatement ps;
      try {
        ps = conn.getConnection().prepareStatement(query);
        return new SDerbyConnection.SDerbyPreparedStatement(ps);
      } catch (SQLException e) {
        return dispatchHandler.executeDispatch(new Object[] {fail,
            Symbols.symbolFor("SQLException" + e.getErrorCode()), e.getMessage()});
      }
    }
  }

  protected static SArray processResults(final ResultSet rs) throws SQLException {
    int cols = rs.getMetaData().getColumnCount();
    ArrayList<SArray> results = new ArrayList<>();

    while (rs.next()) {
      Object[] storage = new Object[cols];
      for (int i = 0; i < cols; i++) {
        storage[i] = rs.getObject(i + 1);
        if (storage[i] instanceof Array) {
          Array a = (Array) storage[i];
          storage[i] = new SImmutableArray(a.getArray(), Classes.arrayClass);
        } else if (storage[i] instanceof Integer) {
          storage[i] = ((Integer) storage[i]).longValue();
        } else if (storage[i] == null) {
          storage[i] = Nil.nilObject;
        }
      }

      results.add(new SImmutableArray(storage, Classes.arrayClass));
    }

    return new SImmutableArray(results.toArray(new Object[0]), Classes.arrayClass);
  }

  @GenerateNodeFactory
  @ImportStatic(DerbyPrims.class)
  @Primitive(primitive = "derby:executePreparedStatement:callback:ifFail:")
  public abstract static class DerbyExecutePrepareStatementPrim
      extends QuaternaryExpressionNode {
    public final String                PROTOCOL        = "jdbc:derby:";
    @Child protected BlockDispatchNode dispatchHandler = BlockDispatchNodeGen.create();
    @Child protected AtPrim            arrayAt         = AtPrimFactory.create(null, null);

    @Specialization
    @TruffleBoundary
    public final Object execute(final SDerbyPreparedStatement statement,
        final SArray parameters,
        final SBlock callback,
        final SBlock fail) {
      try {
        PreparedStatement prep = statement.getStatement();
        prep.clearParameters();

        for (int i = 1; i <= prep.getParameterMetaData().getParameterCount(); i++) {
          Object o = arrayAt.execute(null, parameters, i);
          prep.setObject(i, o);
        }

        if (prep.execute()) {
          ResultSet result = prep.getResultSet();
          return dispatchHandler.executeDispatch(
              new Object[] {callback, processResults(result)});
        } else {
          return dispatchHandler.executeDispatch(
              new Object[] {callback, (long) prep.getUpdateCount()});
        }
      } catch (SQLException e) {
        return dispatchHandler.executeDispatch(new Object[] {fail,
            Symbols.symbolFor("SQLException" + e.getErrorCode()), e.getMessage()});
      }
    }
  }

  @GenerateNodeFactory
  @ImportStatic(DerbyPrims.class)
  @Primitive(primitive = "derby:executeStatement:callback:ifFail:")
  public abstract static class DerbyExecuteStatementPrim extends QuaternaryExpressionNode {
    public final String                PROTOCOL        = "jdbc:derby:";
    @Child protected BlockDispatchNode dispatchHandler = BlockDispatchNodeGen.create();

    @Specialization
    @TruffleBoundary
    public final Object execute(final SDerbyConnection connection,
        final String query,
        final SBlock callback,
        final SBlock fail) {
      try {
        Statement statement = connection.getConnection().createStatement();

        if (statement.execute(query)) {
          ResultSet result = statement.getResultSet();
          return dispatchHandler.executeDispatch(
              new Object[] {callback, processResults(result)});
        } else {
          return dispatchHandler.executeDispatch(
              new Object[] {callback, (long) statement.getUpdateCount()});
        }
      } catch (SQLException e) {
        return dispatchHandler.executeDispatch(new Object[] {fail,
            Symbols.symbolFor("SQLException" + e.getErrorCode()), e.getMessage()});
      }
    }
  }

}
