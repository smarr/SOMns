package som.interpreter.nodes.literals;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.frame.VirtualFrame;

import som.compiler.MixinDefinition;
import som.interpreter.InliningVisitor;
import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.InstantiationNode.ObjectLiteralInstantiationNode;
import som.interpreter.nodes.InstantiationNodeFactory.ObjectLiteralInstantiationNodeGen;
import som.interpreter.nodes.MessageSendNode.AbstractMessageSendNode;
import som.vmobjects.SClass;
import som.vmobjects.SObjectWithClass;


public class ObjectLiteralNode extends LiteralNode {

  private MixinDefinition mixinDef;
  private RootCallTarget  superClassResolver;

  @Child private ExpressionNode                 outerRead;
  @Child private AbstractMessageSendNode        newMessage;
  @Child private ObjectLiteralInstantiationNode instantiation;

  /**
   * An object literal node. When invoked this node will instantiate a new object literal class
   * (defined by this node's `MixinDefinition`) that has the current activation as its
   * enclosing
   * scope. Once this class has been created, this node will instantiate and return a new
   * object
   * from that class.
   */
  public ObjectLiteralNode(final MixinDefinition mixinDefiniton,
      final ExpressionNode outerRead, final ExpressionNode newMessage) {
    this.mixinDef = mixinDefiniton;
    this.superClassResolver =
        mixinDefiniton.getSuperclassAndMixinResolutionInvokable().createCallTarget();

    this.outerRead = outerRead;
    this.newMessage = (AbstractMessageSendNode) newMessage;
    this.instantiation = ObjectLiteralInstantiationNodeGen.create(mixinDefiniton);
  }

  @Override
  public Object executeGeneric(final VirtualFrame frame) {
    SObjectWithClass outer = (SObjectWithClass) outerRead.executeGeneric(frame);
    Object superclassAndMixins = superClassResolver.call(outer);
    SClass sClassObject =
        instantiation.execute(outer, superclassAndMixins, frame.materialize());
    return newMessage.doPreEvaluated(frame, new Object[] {sClassObject});
  }

  @Override
  public void replaceAfterScopeChange(final InliningVisitor inliner) {
    MixinDefinition adaptedMixinDef = mixinDef.cloneAndAdaptAfterScopeChange(
        inliner.getCurrentScope(), inliner.contextLevel);
    replace(createNode(adaptedMixinDef));
  }

  protected ObjectLiteralNode createNode(final MixinDefinition mixinDefinition) {
    return new ObjectLiteralNode(mixinDefinition, outerRead, newMessage).initialize(
        sourceSection);
  }
}
