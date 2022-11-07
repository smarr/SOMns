package som.interpreter.nodes.literals;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.frame.VirtualFrame;

import bd.inlining.ScopeAdaptationVisitor;
import som.compiler.MixinDefinition;
import som.interpreter.SArguments;
import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.InstantiationNode.ObjectLiteralInstantiationNode;
import som.interpreter.nodes.InstantiationNodeFactory.ObjectLiteralInstantiationNodeGen;
import som.interpreter.nodes.MessageSendNode.AbstractMessageSendNode;
import som.vm.VmSettings;
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
        mixinDefiniton.getSuperclassAndMixinResolutionInvokable().getCallTarget();

    this.outerRead = outerRead;
    this.newMessage = (AbstractMessageSendNode) newMessage;
    this.instantiation = ObjectLiteralInstantiationNodeGen.create(mixinDefiniton);
  }

  @Override
  public Object executeGeneric(final VirtualFrame frame) {
    SObjectWithClass outer = (SObjectWithClass) outerRead.executeGeneric(frame);

    Object superclassAndMixins;
    if (VmSettings.ACTOR_ASYNC_STACK_TRACE_STRUCTURE) {
      superclassAndMixins = superClassResolver.call(outer, SArguments.getShadowStackEntry(frame));
    } else {
        superclassAndMixins = superClassResolver.call(outer);
    }
    SClass sClassObject =
        instantiation.execute(outer, superclassAndMixins, frame.materialize());
    Object[] arguments;
    if (VmSettings.ACTOR_ASYNC_STACK_TRACE_STRUCTURE){
      arguments = new Object[] {sClassObject,null};
    } else {
      arguments = new Object[] {sClassObject};
    }
    return newMessage.doPreEvaluated(frame, arguments );
  }

  @Override
  public void replaceAfterScopeChange(final ScopeAdaptationVisitor inliner) {
    MixinDefinition adaptedMixinDef = mixinDef.cloneAndAdaptAfterScopeChange(
        inliner.getCurrentScope(), inliner.contextLevel);
    replace(createNode(adaptedMixinDef));
  }

  protected ObjectLiteralNode createNode(final MixinDefinition mixinDefinition) {
    return new ObjectLiteralNode(mixinDefinition, outerRead, newMessage).initialize(
        sourceSection);
  }
}
