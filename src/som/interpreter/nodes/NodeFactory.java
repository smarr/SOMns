package som.interpreter.nodes;

import java.util.HashMap;

import som.interpreter.nodes.messages.BinarySendNode;
import som.interpreter.nodes.messages.KeywordSendNode;
import som.interpreter.nodes.messages.TernarySendNode;
import som.interpreter.nodes.messages.UnarySendNode;
import som.interpreter.nodes.specialized.IfFalseMessageNodeFactory;
import som.interpreter.nodes.specialized.IfTrueIfFalseMessageNodeFactory;
import som.interpreter.nodes.specialized.IfTrueMessageNodeFactory;
import som.interpreter.nodes.specialized.WhileFalseMessageNodeFactory;
import som.interpreter.nodes.specialized.WhileTrueMessageNodeFactory;
import som.vm.Universe;
import som.vmobjects.SSymbol;


public final class NodeFactory {

  private NodeFactory() {
    unaryMessages   = new HashMap<String, com.oracle.truffle.api.dsl.NodeFactory<? extends UnaryMessageNode>>();
    binaryMessages  = new HashMap<String, com.oracle.truffle.api.dsl.NodeFactory<? extends BinaryMessageNode>>();
    ternaryMessages = new HashMap<String, com.oracle.truffle.api.dsl.NodeFactory<? extends TernaryMessageNode>>();
    keywordMessages = new HashMap<String, com.oracle.truffle.api.dsl.NodeFactory<? extends KeywordMessageNode>>();
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

  public static UnaryMessageNode createUnaryMessageNode(final SSymbol selector, final Universe universe, final ExpressionNode receiver) {
    return UnarySendNode.create(selector, universe, receiver);
  }

  public static BinaryMessageNode createBinaryMessageNode(final SSymbol selector, final Universe universe, final ExpressionNode receiver, final ExpressionNode arg) {
    return BinarySendNode.create(selector, universe, receiver, arg);
  }

  public static AbstractMessageNode createMessageNode(final SSymbol selector, final Universe universe, final ExpressionNode receiver, final ExpressionNode[] arguments) {
    switch (arguments.length) {
      case 0:
        return createUnaryMessageNode(selector, universe, receiver);
      case 1:
        return BinarySendNode.create(selector, universe, receiver, arguments[0]);
      case 2:
        return TernarySendNode.create(selector, universe, receiver, arguments[0], arguments[1]);
      default:
        ArgumentEvaluationNode args = new ArgumentEvaluationNode(arguments);
        return KeywordSendNode.create(selector, universe, receiver, args);
    }
  }



//  public static KeywordMonomorphicNode createKeywordMonomorphicNode(
//      final SSymbol selector, final Universe universe,
//      final SClass rcvrClass, final SMethod invokable,
//      final ExpressionNode receiver, final ArgumentEvaluationNode arguments) {
//    return factory.keywordMonomorphicNode(selector, universe, rcvrClass,
//        invokable, receiver, arguments);
//  }

//  public static UnaryMessageNode createUnaryMonomorphicNode(
//      final SSymbol selector, final Universe universe,
//      final SClass rcvrClass, final SMethod invokable,
//      final ExpressionNode receiver) {
//    return factory.unaryMonomorphicNode(selector, universe, rcvrClass,
//        invokable, receiver);
//  }

//  public static BinaryMessageNode createBinaryMonomorphicNode(
//      final SSymbol selector, final Universe universe,
//      final SClass rcvrClass, final SMethod invokable,
//      final ExpressionNode receiver, final ExpressionNode argument) {
//    return factory.binaryMonomorphicNode(selector, universe, rcvrClass,
//        invokable, receiver, argument);
//  }

//  public static TernaryMessageNode createTernaryMonomorphicNode(
//      final SSymbol selector, final Universe universe,
//      final SClass rcvrClass, final SMethod invokable,
//      final ExpressionNode receiver, final ExpressionNode firstArg,
//      final ExpressionNode secondArg) {
//    return factory.ternaryMonomorphicNode(selector, universe, rcvrClass,
//        invokable, receiver, firstArg, secondArg);
//  }

//  private KeywordMessageNode keywordMonomorphicNode(
//      final SSymbol selector, final Universe universe,
//      final SClass rcvrClass, final SMethod invokable,
//      final ExpressionNode receiver, final ArgumentEvaluationNode arguments) {
//    com.oracle.truffle.api.dsl.NodeFactory<? extends KeywordMessageNode> result = keywordMessages.get(selector.getString());
//    if (result != null) {
//      return result.createNode(selector, universe, rcvrClass, invokable, receiver, arguments);
//    } else {
//      return KeywordMessageNodeFactory.create(selector, universe, rcvrClass,
//          invokable, receiver, arguments);
//    }
//    return KeywordSendNode.create(selector, universe, receiver, arguments);
//  }

//  private UnaryMessageNode unaryMonomorphicNode(
//      final SSymbol selector, final Universe universe,
//      final SClass rcvrClass, final SMethod invokable,
//      final ExpressionNode receiver) {
//    com.oracle.truffle.api.dsl.NodeFactory<? extends UnaryMessageNode> result = unaryMessages.get(selector.getString());
//    if (result != null) {
//      return result.createNode(selector, universe, rcvrClass, invokable, receiver);
//    } else {
//      return UnaryMessageNodeFactory.create(selector, universe, rcvrClass,
//          invokable, receiver);
//    }
//  }

//  private BinaryMessageNode binaryMonomorphicNode(
//      final SSymbol selector, final Universe universe,
//      final SClass rcvrClass, final SMethod invokable,
//      final ExpressionNode receiver, final ExpressionNode argument) {
//    com.oracle.truffle.api.dsl.NodeFactory<? extends BinaryMessageNode> result = binaryMessages.get(selector.getString());
//    if (result != null) {
//      return result.createNode(selector, universe, rcvrClass, invokable, receiver, argument);
//    } else {
//      return BinaryMessageNodeFactory.create(selector, universe, rcvrClass,
//          invokable, receiver, argument);
//    }
//  }

//  private TernaryMessageNode ternaryMonomorphicNode(
//      final SSymbol selector, final Universe universe,
//      final SClass rcvrClass, final SMethod invokable,
//      final ExpressionNode receiver, final ExpressionNode firstArg,
//      final ExpressionNode secondArg) {
//    com.oracle.truffle.api.dsl.NodeFactory<? extends TernaryMessageNode> result = ternaryMessages.get(selector.getString());
//    if (result != null) {
//      return result.createNode(selector, universe, rcvrClass, invokable, receiver, firstArg, secondArg);
//    } else {
//      return TernarySendNode.create(selector, universe, rcvrClass,
//          invokable, receiver, firstArg, secondArg);
//    }
//  }

  public static void registerNodeFactory(final SSymbol selector,
      final com.oracle.truffle.api.dsl.NodeFactory<? extends AbstractMessageNode> nodeFactory) {
    factory.registerFactory(selector.getString(),
        selector.getNumberOfSignatureArguments(), nodeFactory);
  }

  @SuppressWarnings("unchecked")
  public void registerFactory(final String selector, final int arity,
      final com.oracle.truffle.api.dsl.NodeFactory<? extends AbstractMessageNode> nodeFactory) {
    com.oracle.truffle.api.dsl.NodeFactory<? extends AbstractMessageNode> old;

    if (arity == 1) {
      old = unaryMessages.put(selector,   (com.oracle.truffle.api.dsl.NodeFactory<? extends UnaryMessageNode>)   nodeFactory);
    } else if (arity == 2) {
      old = binaryMessages.put(selector,  (com.oracle.truffle.api.dsl.NodeFactory<? extends BinaryMessageNode>)  nodeFactory);
    } else if (arity == 3) {
      old = ternaryMessages.put(selector, (com.oracle.truffle.api.dsl.NodeFactory<? extends TernaryMessageNode>) nodeFactory);
    } else {
      old = keywordMessages.put(selector, (com.oracle.truffle.api.dsl.NodeFactory<? extends KeywordMessageNode>) nodeFactory);
    }

    if (old != null && old != nodeFactory) {
      throw new RuntimeException("There is already a factory registered for '" + selector + "'. The corresponding node classes need to be unified into one class.");
    }
  }


  private final HashMap<String, com.oracle.truffle.api.dsl.NodeFactory<? extends UnaryMessageNode>>   unaryMessages;
  private final HashMap<String, com.oracle.truffle.api.dsl.NodeFactory<? extends BinaryMessageNode>>  binaryMessages;
  private final HashMap<String, com.oracle.truffle.api.dsl.NodeFactory<? extends TernaryMessageNode>> ternaryMessages;
  private final HashMap<String, com.oracle.truffle.api.dsl.NodeFactory<? extends KeywordMessageNode>> keywordMessages;

  private static final NodeFactory factory = new NodeFactory();
}
