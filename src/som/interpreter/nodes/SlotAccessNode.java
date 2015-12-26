package som.interpreter.nodes;

import som.compiler.MixinDefinition;
import som.interpreter.Invokable;
import som.interpreter.SArguments;
import som.interpreter.objectstorage.FieldAccessorNode.AbstractReadFieldNode;
import som.interpreter.objectstorage.FieldAccessorNode.AbstractWriteFieldNode;
import som.vm.constants.Nil;
import som.vmobjects.SClass;
import som.vmobjects.SObject;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.profiles.ValueProfile;


public abstract class SlotAccessNode extends ExpressionNode {

  public SlotAccessNode() { super(null); }

  public abstract Object doRead(VirtualFrame frame, SObject rcvr);

  public static final class SlotReadNode extends SlotAccessNode {
    // TODO: may be, we can get rid of this completely?? could directly use AbstractReadFieldNode
    // TODO: we only got read support at the moment
    @Child protected AbstractReadFieldNode read;
    private final ValueProfile rcvrClass = ValueProfile.createClassProfile();

    public SlotReadNode(final AbstractReadFieldNode read) {
      this.read = read;
    }

    @Override
    public Object doRead(final VirtualFrame frame, final SObject rcvr) {
      return read.read(rcvr);
    }

    @Override
    public Object executeGeneric(final VirtualFrame frame) {
      return read.read((SObject) rcvrClass.profile(SArguments.rcvr(frame)));
    }
  }

  // TODO: try to remove, should only be used in getCallTarget version of mutator slots
  public static final class SlotWriteNode extends ExpressionNode {
    private final ValueProfile rcvrClass = ValueProfile.createClassProfile();
    @Child protected AbstractWriteFieldNode write;

    public SlotWriteNode(final AbstractWriteFieldNode write) {
      super(null);
      this.write = write;
    }

    @Override
    public Object executeGeneric(final VirtualFrame frame) {
      return write.write((SObject) rcvrClass.profile(SArguments.rcvr(frame)), SArguments.arg(frame, 1));
    }
  }

  public static final class ClassSlotAccessNode extends SlotAccessNode {
    private final MixinDefinition mixinDef;
    @Child protected DirectCallNode superclassAndMixinResolver;
    @Child protected ClassInstantiationNode instantiation;

    @Child protected AbstractReadFieldNode  read;
    @Child protected AbstractWriteFieldNode write;

    public ClassSlotAccessNode(final MixinDefinition mixinDef,
        final AbstractReadFieldNode read, final AbstractWriteFieldNode write) {
      this.read = read;
      this.write = write;
      this.mixinDef = mixinDef;
      this.instantiation = ClassInstantiationNodeGen.create(mixinDef);
    }

    @Override
    public SClass doRead(final VirtualFrame frame, final SObject rcvr) {
      // here we need to synchronize, because this is actually something that
      // can happen concurrently, and we only want a single instance of the
      // class object
      Object cachedValue = read.read(rcvr);
      if (cachedValue != Nil.nilObject) {
        return (SClass) cachedValue;
      }

      synchronized (rcvr) {
        cachedValue = read.read(rcvr);

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

    @Override
    public Object executeGeneric(final VirtualFrame frame) {
      return doRead(frame, (SObject) SArguments.rcvr(frame));
    }
  }
}
