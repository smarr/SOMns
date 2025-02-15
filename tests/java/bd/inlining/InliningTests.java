package bd.inlining;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

import bd.basic.ProgramDefinitionError;
import bd.testsetup.AddNodeFactory;
import bd.testsetup.ExprNode;
import bd.testsetup.LambdaNode;
import bd.testsetup.StringId;
import bd.testsetup.ValueNode;
import bd.testsetup.ValueSpecializedNode;


public class InliningTests {

  private final SourceSection source =
      Source.newBuilder("x", "test", "test").mimeType("x/test").build().createSection(1);

  private final InlinableNodes<String> nodes =
      new InlinableNodes<String>(new StringId(), Nodes.getInlinableNodes(),
          Nodes.getInlinableFactories());

  @Test
  public void testNonInlinableNode() throws ProgramDefinitionError {
    List<ExprNode> argNodes = new ArrayList<>();
    argNodes.add(AddNodeFactory.create(null, null));
    assertNull(nodes.inline("value", argNodes, null, null));
  }

  @Test
  public void testValueNode() throws ProgramDefinitionError {
    List<ExprNode> argNodes = new ArrayList<>();
    LambdaNode arg = new LambdaNode();
    argNodes.add(arg);
    ExprNode valueNode = nodes.inline("value", argNodes, null, source);
    assertNotNull(valueNode);
    assertTrue(valueNode instanceof ValueNode);

    ValueNode value = (ValueNode) valueNode;

    assertNotEquals(arg, value.inlined);
    assertEquals(arg, value.original);

    assertTrue(value.trueVal);
    assertFalse(value.falseVal);

    assertTrue(valueNode.getSourceSection() == source);
  }

  @Test
  public void testValueSpecNode() throws ProgramDefinitionError {
    List<ExprNode> argNodes = new ArrayList<>();
    LambdaNode arg = new LambdaNode();
    argNodes.add(arg);
    ExprNode valueNode = nodes.inline("valueSpec", argNodes, null, source);
    assertNotNull(valueNode);
    assertTrue(valueNode instanceof ValueSpecializedNode);

    ValueSpecializedNode value = (ValueSpecializedNode) valueNode;

    assertNotEquals(arg, value.inlined);
    assertEquals(arg, value.getLambda());
    assertTrue(valueNode.getSourceSection() == source);
  }

  @Test
  public void testEmptyInit() throws ProgramDefinitionError {
    InlinableNodes<String> n =
        new InlinableNodes<>(new StringId(), null, null);

    assertNull(n.inline("nonExisting", null, null, null));
  }
}
