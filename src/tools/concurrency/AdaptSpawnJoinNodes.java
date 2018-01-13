package tools.concurrency;

import java.util.ArrayList;

import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeVisitor;

import som.compiler.Variable.Local;
import som.interpreter.NodeVisitorUtil;
import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.LocalVariableNode;
import som.interpreter.nodes.MessageSendNode.AbstractUninitializedMessageSendNode;
import som.interpreter.nodes.NonLocalVariableNode;
import som.interpreter.nodes.OptJoinNode;
import som.interpreter.nodes.OptTaskNode;
import som.interpreter.nodes.ResolvingImplicitReceiverSend;
import som.interpreter.nodes.literals.ArrayLiteralNode;
import som.interpreter.nodes.nary.EagerBinaryPrimitiveNode;
import som.interpreter.nodes.nary.EagerTernaryPrimitiveNode;
import som.interpreter.nodes.nary.EagerUnaryPrimitiveNode;
import som.primitives.BlockPrimsFactory.ValueNonePrimFactory;
import som.primitives.BlockPrimsFactory.ValueTwoPrimFactory;
import som.vm.Symbols;
import som.vm.VmSettings;
import som.vmobjects.SSymbol;


public final class AdaptSpawnJoinNodes implements NodeVisitor {

  private static final SSymbol SPAWN_WITH = Symbols.symbolFor("spawn:with:");
  private static final SSymbol SPAWN      = Symbols.symbolFor("spawn:");
  private static final SSymbol JOIN       = Symbols.symbolFor("join");
  private static final SSymbol WITH_WITH  = Symbols.symbolFor("with:with:");
  private static final SSymbol THREAD     = Symbols.symbolFor("Thread");
  private static final SSymbol PROCESSES  = Symbols.symbolFor("processes");

  private static ArrayList<Local> wvar = new ArrayList<Local>();

  public static ExpressionNode doInline(final ExpressionNode body) {
    return NodeVisitorUtil.applyVisitor(body, new AdaptSpawnJoinNodes());
  }

  private AdaptSpawnJoinNodes() {}

  @Override
  public boolean visit(final Node node) {
    if (VmSettings.ENABLE_SEQUENTIAL) {
      convertSpawnJoinToSequential(node);
      return true;
    }

    if (node instanceof EagerTernaryPrimitiveNode) {
      EagerTernaryPrimitiveNode msgSend = (EagerTernaryPrimitiveNode) node;

      if (msgSend.getSelector() == SPAWN_WITH) {
        ExpressionNode[] exp = msgSend.getArguments();

        if (doesntSpawnTask(exp)) {
          return true;
        }

        ExpressionNode[] spawnArgs;

        if (!(exp[2] instanceof AbstractUninitializedMessageSendNode)
            && !(exp[2] instanceof ArrayLiteralNode)) {
          // can't handle this at the moment
          return true;
        }

        if (exp[2] instanceof AbstractUninitializedMessageSendNode) {
          // assuming this is `Array with: a with: b`
          AbstractUninitializedMessageSendNode o =
              (AbstractUninitializedMessageSendNode) exp[2];
          if (o.getSelector() != WITH_WITH) {
            // can't handle this at the moment
            return true;
          }
          ExpressionNode[] args = o.getArguments();
          spawnArgs = new ExpressionNode[] {args[1], args[2]};
        } else {
          assert exp[2] instanceof ArrayLiteralNode;
          ArrayLiteralNode arr = (ArrayLiteralNode) exp[2];
          spawnArgs = arr.getElementNodes();
          if (spawnArgs.length != 2) {
            // can't handle this at the moment
            return true;
          }
        }

        recordVars(exp);

        Node replacement = new OptTaskNode(
            ValueTwoPrimFactory.create(null, null, null).initialize(node.getSourceSection()),
            exp[1], spawnArgs).initialize(node.getSourceSection());
        node.replace(replacement);
      }
    }

    if (node instanceof EagerBinaryPrimitiveNode) {
      EagerBinaryPrimitiveNode msgSend = (EagerBinaryPrimitiveNode) node;

      if (msgSend.getSelector() == SPAWN) {
        ExpressionNode[] exp = msgSend.getArguments();
        if (doesntSpawnTask(exp)) {
          return true;
        }

        Node replacement = new OptTaskNode(
            ValueNonePrimFactory.create(null).initialize(node.getSourceSection()),
            exp[1], new ExpressionNode[] {}).initialize(node.getSourceSection());

        recordVars(exp);

        node.replace(replacement);
      }
    }

    if (node instanceof EagerUnaryPrimitiveNode) {
      EagerUnaryPrimitiveNode joinNode = (EagerUnaryPrimitiveNode) node;

      if (joinNode.getSelector() == JOIN) {
        ExpressionNode exp = joinNode.getReceiver();
        Node replacement = new OptJoinNode(exp).initialize(node.getSourceSection());

        node.replace(replacement);
      }
    }
    return true;
  }

  private boolean doesntSpawnTask(final ExpressionNode[] exp) {
    return exp[0] instanceof ResolvingImplicitReceiverSend
        && (((ResolvingImplicitReceiverSend) exp[0]).getSelector() == THREAD) ||
        ((ResolvingImplicitReceiverSend) exp[0]).getSelector() == PROCESSES;
  }

  private void convertSpawnJoinToSequential(final Node node) {
    if (node instanceof EagerTernaryPrimitiveNode) {
      EagerTernaryPrimitiveNode msgSend = (EagerTernaryPrimitiveNode) node;

      if (msgSend.getSelector() == SPAWN_WITH) {
        ExpressionNode[] exp = msgSend.getArguments();
        ExpressionNode[] spawnArgs;

        if (exp[2] instanceof AbstractUninitializedMessageSendNode) {
          // assuming this is `Array with: a with: b`
          AbstractUninitializedMessageSendNode o =
              (AbstractUninitializedMessageSendNode) exp[2];
          if (o.getSelector() != WITH_WITH) {
            // can't handle this at the moment
            return;
          }
          ExpressionNode[] args = o.getArguments();
          spawnArgs = new ExpressionNode[] {args[1], args[2]};
        } else {
          assert exp[2] instanceof ArrayLiteralNode;
          ArrayLiteralNode arr = (ArrayLiteralNode) exp[2];
          spawnArgs = arr.getElementNodes();
          if (spawnArgs.length != 2) {
            // can't handle this at the moment
            return;
          }
        }

        Node replacement = ValueTwoPrimFactory.create(exp[1], spawnArgs[1], spawnArgs[2])
                                              .initialize(node.getSourceSection());
        node.replace(replacement);
      }
    }

    if (node instanceof EagerBinaryPrimitiveNode) {
      EagerBinaryPrimitiveNode msgSend = (EagerBinaryPrimitiveNode) node;

      if (msgSend.getSelector() == SPAWN) {
        ExpressionNode[] exp = msgSend.getArguments();
        Node replacement =
            ValueNonePrimFactory.create(exp[1]).initialize(node.getSourceSection());

        node.replace(replacement);
      }
    }

    if (node instanceof EagerUnaryPrimitiveNode) {
      EagerUnaryPrimitiveNode joinNode = (EagerUnaryPrimitiveNode) node;

      if (joinNode.getSelector() == JOIN) {
        ExpressionNode exp = joinNode.getReceiver();
        node.replace(exp);
      }
    }
  }

  private void recordVars(final ExpressionNode[] exp) {
    Node n = exp[1];

    while (n.getParent() != null) {
      n = n.getParent();

      if (n instanceof NonLocalVariableNode) {
        wvar.add(((NonLocalVariableNode) n).getLocal());
      } else if (n instanceof LocalVariableNode) {
        wvar.add(((LocalVariableNode) n).getLocal());
        break;
      }
    }
  }

  @Override
  public String toString() {
    return getClass().getSimpleName();
  }
}
