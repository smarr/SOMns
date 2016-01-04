package som.interpreter.nodes;

import som.compiler.MixinDefinition;
import som.interpreter.Invokable;
import som.interpreter.objectstorage.FieldReadNode;
import som.interpreter.objectstorage.FieldWriteNode.AbstractFieldWriteNode;
import som.vm.constants.Nil;
import som.vmobjects.SClass;
import som.vmobjects.SObject;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.InvalidAssumptionException;

public final class ClassSlotAccessNode extends FieldReadNode {
  private final MixinDefinition mixinDef;
  @Child protected DirectCallNode superclassAndMixinResolver;
  @Child protected ClassInstantiationNode instantiation;

  private final FieldReadNode  read;
  @Child protected AbstractFieldWriteNode write;

  public ClassSlotAccessNode(final MixinDefinition mixinDef,
      final FieldReadNode read, final AbstractFieldWriteNode write) {
    super(read.getSlot());
    this.read = read;
    this.write = write;
    this.mixinDef = mixinDef;
    this.instantiation = ClassInstantiationNodeGen.create(mixinDef);
  }

  @Override
  public SClass read(final VirtualFrame frame, final SObject rcvr) {
    // here we need to synchronize, because this is actually something that
    // can happen concurrently, and we only want a single instance of the
    // class object
    Object cachedValue;
    try {
      cachedValue = read.read(frame, rcvr);
    } catch (InvalidAssumptionException e) {
      CompilerDirectives.transferToInterpreter();
      throw new RuntimeException("This should never happen");
    }
    assert cachedValue != null;
    if (cachedValue != Nil.nilObject) {
      return (SClass) cachedValue;
    }

    synchronized (rcvr) {
      try {
        cachedValue = read.read(frame, rcvr);
      } catch (InvalidAssumptionException e) {
        CompilerDirectives.transferToInterpreter();
        throw new RuntimeException("This should never happen");
      }

      // check whether cache is initialized with class object
      if (cachedValue == Nil.nilObject) {
        SClass classObject = instantiateClassObject(frame, rcvr);
        write.write(rcvr, classObject);
        return classObject;
      } else {
        assert cachedValue instanceof SClass;
        return (SClass) cachedValue;
      }
    }
  }

  private void createResolverCallTargets() {
    CompilerAsserts.neverPartOfCompilation();
    Invokable invokable = mixinDef.getSuperclassAndMixinResolutionInvokable();
    superclassAndMixinResolver = insert(Truffle.getRuntime().createDirectCallNode(
        invokable.createCallTarget()));
  }

  private SClass instantiateClassObject(final VirtualFrame frame,
      final SObject rcvr) {
    if (superclassAndMixinResolver == null) {
      CompilerDirectives.transferToInterpreterAndInvalidate();
      createResolverCallTargets();
    }

    Object superclassAndMixins = superclassAndMixinResolver.call(frame,
        new Object[] {rcvr});
    SClass classObject = instantiation.execute(rcvr, superclassAndMixins);
    return classObject;
  }
}
