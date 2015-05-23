package som.interpreter.nodes.dispatch;

import som.compiler.ClassDefinition;
import som.interpreter.Invokable;
import som.interpreter.objectstorage.FieldAccessorNode.AbstractReadFieldNode;
import som.interpreter.objectstorage.FieldAccessorNode.AbstractWriteFieldNode;
import som.vm.constants.Nil;
import som.vmobjects.SClass;
import som.vmobjects.SObject;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;


public class CachedClassSlotAccessNode extends AbstractDispatchNode {

  @Child protected AbstractDispatchNode nextInCache;
  private final SClass rcvrClass;

  private final ClassDefinition classDefinition;
  @Child protected DirectCallNode classObjectInstantiation;

  @Child protected AbstractReadFieldNode  read;
  @Child protected AbstractWriteFieldNode write;

  public CachedClassSlotAccessNode(final ClassDefinition classDefinition,
      final SClass rcvrClass, final AbstractReadFieldNode read,
      final AbstractWriteFieldNode write, final AbstractDispatchNode next) {
    this.rcvrClass = rcvrClass;
    this.read = read;
    this.write = write;
    this.nextInCache = next;
    this.classDefinition = classDefinition;
  }

  @Override
  public Object executeDispatch(final VirtualFrame frame, final Object[] arguments) {
    assert arguments[0] instanceof SObject;
    SObject rcvr = (SObject) arguments[0];

    if (rcvr.getSOMClass() == rcvrClass) {
      return getClassObject(frame, rcvr);
    } else {
      return nextInCache.executeDispatch(frame, arguments);
    }
  }

  private SClass getClassObject(final VirtualFrame frame, final SObject rcvr) {
    Object cacheValue = read.read(rcvr);

    // check whether cache is initialized with class object
    if (cacheValue == Nil.nilObject) {
      SClass classObject = instantiateClassObject(frame, rcvr);
      write.write(rcvr, classObject);
      return classObject;
    } else {
      assert cacheValue instanceof SClass;
      return (SClass) cacheValue;
    }
  }

  private SClass instantiateClassObject(final VirtualFrame frame,
      final SObject rcvr) {
    if (classObjectInstantiation == null) {
      Invokable invokable = classDefinition.getSuperclassResolutionInvokable();
      classObjectInstantiation = Truffle.getRuntime().createDirectCallNode(
          invokable.createCallTarget());
    }

    SClass superClass = (SClass) classObjectInstantiation.call(frame,
        new Object[] {rcvr});
    SClass classObject = classDefinition.instantiateClass(rcvr, superClass);
    return classObject;
  }

  @Override
  public final int lengthOfDispatchChain() {
    return 1 + nextInCache.lengthOfDispatchChain();
  }

  @Override
  public String toString() {
    return "ClassSlotAccess(" + classDefinition.getName().getString() + ")";
  }
 }
