package som.interpreter.nodes;

import java.util.HashMap;

import som.interpreter.nodes.messages.AbstractMonomorphicMessageNode;
import som.interpreter.nodes.messages.BinaryMonomorphicNode;
import som.interpreter.nodes.messages.BinaryMonomorphicNodeFactory;
import som.interpreter.nodes.messages.KeywordMonomorphicNode;
import som.interpreter.nodes.messages.KeywordMonomorphicNodeFactory;
import som.interpreter.nodes.messages.TernaryMonomorphicNode;
import som.interpreter.nodes.messages.TernaryMonomorphicNodeFactory;
import som.interpreter.nodes.messages.UnaryMonomorphicNode;
import som.interpreter.nodes.messages.UnaryMonomorphicNodeFactory;
import som.interpreter.nodes.specialized.IfFalseMessageNodeFactory;
import som.interpreter.nodes.specialized.IfTrueIfFalseMessageNodeFactory;
import som.interpreter.nodes.specialized.IfTrueMessageNodeFactory;
import som.interpreter.nodes.specialized.WhileFalseMessageNodeFactory;
import som.interpreter.nodes.specialized.WhileTrueMessageNodeFactory;
import som.vm.Universe;
import som.vmobjects.SClass;
import som.vmobjects.SMethod;
import som.vmobjects.SSymbol;


public final class NodeFactory {

  private NodeFactory() {
    unaryMessages   = new HashMap<String, com.oracle.truffle.api.dsl.NodeFactory<? extends UnaryMonomorphicNode>>();
    binaryMessages  = new HashMap<String, com.oracle.truffle.api.dsl.NodeFactory<? extends BinaryMonomorphicNode>>();
    ternaryMessages = new HashMap<String, com.oracle.truffle.api.dsl.NodeFactory<? extends TernaryMonomorphicNode>>();
    keywordMessages = new HashMap<String, com.oracle.truffle.api.dsl.NodeFactory<? extends KeywordMonomorphicNode>>();
  }

  public static void registerKnownFactories() {
    factory.registerFactories();
  }

  private void registerFactories() {
    unaryMessages.clear();
    binaryMessages.clear();
    ternaryMessages.clear();
    keywordMessages.clear();

    registerFactory("ifTrue:",  2, IfTrueMessageNodeFactory.getInstance());
    registerFactory("ifFalse:", 2, IfFalseMessageNodeFactory.getInstance());

    registerFactory("ifTrue:ifFalse:", 2, IfTrueIfFalseMessageNodeFactory.getInstance());

    registerFactory("whileTrue:",  2, WhileTrueMessageNodeFactory.getInstance());
    registerFactory("whileFalse:", 2, WhileFalseMessageNodeFactory.getInstance());
  }

  public static KeywordMonomorphicNode createKeywordMonomorphicNode(
      final SSymbol selector, final Universe universe,
      final SClass rcvrClass, final SMethod invokable,
      final ExpressionNode receiver, final ArgumentEvaluationNode arguments) {
    return factory.keywordMonomorphicNode(selector, universe, rcvrClass,
        invokable, receiver, arguments);
  }

  public static UnaryMonomorphicNode createUnaryMonomorphicNode(
      final SSymbol selector, final Universe universe,
      final SClass rcvrClass, final SMethod invokable,
      final ExpressionNode receiver) {
    return factory.unaryMonomorphicNode(selector, universe, rcvrClass,
        invokable, receiver);
  }

  public static BinaryMonomorphicNode createBinaryMonomorphicNode(
      final SSymbol selector, final Universe universe,
      final SClass rcvrClass, final SMethod invokable,
      final ExpressionNode receiver, final ExpressionNode argument) {
    return factory.binaryMonomorphicNode(selector, universe, rcvrClass,
        invokable, receiver, argument);
  }

  public static TernaryMonomorphicNode createTernaryMonomorphicNode(
      final SSymbol selector, final Universe universe,
      final SClass rcvrClass, final SMethod invokable,
      final ExpressionNode receiver, final ExpressionNode firstArg,
      final ExpressionNode secondArg) {
    return factory.ternaryMonomorphicNode(selector, universe, rcvrClass,
        invokable, receiver, firstArg, secondArg);
  }

  private KeywordMonomorphicNode keywordMonomorphicNode(
      final SSymbol selector, final Universe universe,
      final SClass rcvrClass, final SMethod invokable,
      final ExpressionNode receiver, final ArgumentEvaluationNode arguments) {
    com.oracle.truffle.api.dsl.NodeFactory<? extends KeywordMonomorphicNode> result = keywordMessages.get(selector.getString());
    if (result != null) {
      return result.createNode(selector, universe, rcvrClass, invokable, receiver, arguments);
    } else {
      return KeywordMonomorphicNodeFactory.create(selector, universe, rcvrClass,
          invokable, receiver, arguments);
    }
  }

  private UnaryMonomorphicNode unaryMonomorphicNode(
      final SSymbol selector, final Universe universe,
      final SClass rcvrClass, final SMethod invokable,
      final ExpressionNode receiver) {
    com.oracle.truffle.api.dsl.NodeFactory<? extends UnaryMonomorphicNode> result = unaryMessages.get(selector.getString());
    if (result != null) {
      return result.createNode(selector, universe, rcvrClass, invokable, receiver);
    } else {
      return UnaryMonomorphicNodeFactory.create(selector, universe, rcvrClass,
          invokable, receiver);
    }
  }

  private BinaryMonomorphicNode binaryMonomorphicNode(
      final SSymbol selector, final Universe universe,
      final SClass rcvrClass, final SMethod invokable,
      final ExpressionNode receiver, final ExpressionNode argument) {
    com.oracle.truffle.api.dsl.NodeFactory<? extends BinaryMonomorphicNode> result = binaryMessages.get(selector.getString());
    if (result != null) {
      return result.createNode(selector, universe, rcvrClass, invokable, receiver, argument);
    } else {
      return BinaryMonomorphicNodeFactory.create(selector, universe, rcvrClass,
          invokable, receiver, argument);
    }
  }

  private TernaryMonomorphicNode ternaryMonomorphicNode(
      final SSymbol selector, final Universe universe,
      final SClass rcvrClass, final SMethod invokable,
      final ExpressionNode receiver, final ExpressionNode firstArg,
      final ExpressionNode secondArg) {
    com.oracle.truffle.api.dsl.NodeFactory<? extends TernaryMonomorphicNode> result = ternaryMessages.get(selector.getString());
    if (result != null) {
      return result.createNode(selector, universe, rcvrClass, invokable, receiver, firstArg, secondArg);
    } else {
      return TernaryMonomorphicNodeFactory.create(selector, universe, rcvrClass,
          invokable, receiver, firstArg, secondArg);
    }
  }

  public static void registerNodeFactory(final SSymbol selector,
      final com.oracle.truffle.api.dsl.NodeFactory<? extends AbstractMonomorphicMessageNode> nodeFactory) {
    factory.registerFactory(selector.getString(),
        selector.getNumberOfSignatureArguments(), nodeFactory);
  }

  @SuppressWarnings("unchecked")
  public void registerFactory(final String selector, final int arity,
      final com.oracle.truffle.api.dsl.NodeFactory<? extends AbstractMonomorphicMessageNode> nodeFactory) {
    com.oracle.truffle.api.dsl.NodeFactory<? extends AbstractMonomorphicMessageNode> old;

    if (arity == 1) {
      old = unaryMessages.put(selector,   (com.oracle.truffle.api.dsl.NodeFactory<? extends UnaryMonomorphicNode>)   nodeFactory);
    } else if (arity == 2) {
      old = binaryMessages.put(selector,  (com.oracle.truffle.api.dsl.NodeFactory<? extends BinaryMonomorphicNode>)  nodeFactory);
    } else if (arity == 3) {
      old = ternaryMessages.put(selector, (com.oracle.truffle.api.dsl.NodeFactory<? extends TernaryMonomorphicNode>) nodeFactory);
    } else {
      old = keywordMessages.put(selector, (com.oracle.truffle.api.dsl.NodeFactory<? extends KeywordMonomorphicNode>) nodeFactory);
    }

    if (old != null && old != nodeFactory) {
      throw new RuntimeException("There is already a factory registered for '" + selector + "'. The corresponding node classes need to be unified into one class.");
    }
  }


  private final HashMap<String, com.oracle.truffle.api.dsl.NodeFactory<? extends UnaryMonomorphicNode>>   unaryMessages;
  private final HashMap<String, com.oracle.truffle.api.dsl.NodeFactory<? extends BinaryMonomorphicNode>>  binaryMessages;
  private final HashMap<String, com.oracle.truffle.api.dsl.NodeFactory<? extends TernaryMonomorphicNode>> ternaryMessages;
  private final HashMap<String, com.oracle.truffle.api.dsl.NodeFactory<? extends KeywordMonomorphicNode>> keywordMessages;

  private static final NodeFactory factory = new NodeFactory();
}
