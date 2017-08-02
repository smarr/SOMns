package som.interpreter.nodes.dispatch;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.nodes.DirectCallNode;

import som.compiler.MixinDefinition;
import som.interpreter.Invokable;
import som.interpreter.nodes.InstantiationNode.ClassInstantiationNode;
import som.interpreter.nodes.InstantiationNodeFactory.ClassInstantiationNodeGen;
import som.vm.constants.Nil;
import som.vmobjects.SClass;
import som.vmobjects.SObject;


/**
 * Similar to {@link CachedSlotRead field reads}, access to class objects
 * is realized as a simple message send in Newspeak, and as a result, part of
 * the dispatch chain.
 *
 * <p>The Newspeak semantics defines that class objects are allocated lazily.
 * This is realized here using read/write nodes on a slot of the enclosing
 * object, where the initialized class object is cached.
 */
public final class ClassSlotAccessNode extends CachedSlotRead {
  private final MixinDefinition mixinDef;
  @Child protected DirectCallNode superclassAndMixinResolver;
  @Child protected ClassInstantiationNode instantiation;

  @Child protected CachedSlotRead read;
  @Child protected CachedSlotWrite write;

  public ClassSlotAccessNode(final MixinDefinition mixinDef,
      final CachedSlotRead read, final CachedSlotWrite write) {
    super(SlotAccess.CLASS_READ, read.guard, read.nextInCache);

    // TODO: can the slot read be an unwritten read? I'd think so.
    this.read = read;
    this.write = write;
    this.mixinDef = mixinDef;
    this.instantiation = ClassInstantiationNodeGen.create(mixinDef);
  }

  @Override
  public SClass read(final SObject rcvr) {
    // here we need to synchronize, because this is actually something that
    // can happen concurrently, and we only want a single instance of the
    // class object
    Object cachedValue = read.read(rcvr);

    assert cachedValue != null;
    if (cachedValue != Nil.nilObject) {
      return (SClass) cachedValue;
    }

    synchronized (rcvr) {
      cachedValue = read.read(rcvr);

      // check whether cache is initialized with class object
      if (cachedValue == Nil.nilObject) {
        SClass classObject = instantiateClassObject(rcvr);
        write.doWrite(rcvr, classObject);
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

  private SClass instantiateClassObject(final SObject rcvr) {
    if (superclassAndMixinResolver == null) {
      CompilerDirectives.transferToInterpreterAndInvalidate();
      createResolverCallTargets();
    }

    Object superclassAndMixins = superclassAndMixinResolver.call(new Object[] {rcvr});
    SClass classObject = instantiation.execute(rcvr, superclassAndMixins);
    return classObject;
  }
}
