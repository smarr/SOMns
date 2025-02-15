package bd.testsetup;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import com.oracle.truffle.api.nodes.UnexpectedResultException;


public class TestAdd {

  @Test
  public void testLiteralIntegerAddition() throws UnexpectedResultException {
    AddNode node = AddNodeFactory.create(new IntLiteral(2), new IntLiteral(40));
    assertEquals(42, node.executeInt(null));
  }
}
