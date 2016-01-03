package som.interpreter.objectstorage;

import som.compiler.MixinDefinition.SlotDefinition;
import som.interpreter.nodes.ExpressionNode;
import som.interpreter.objectstorage.StorageLocation.AbstractObjectStorageLocation;
import som.interpreter.objectstorage.StorageLocation.DoubleStorageLocation;
import som.interpreter.objectstorage.StorageLocation.LongStorageLocation;
import som.interpreter.objectstorage.StorageLocation.UnwrittenStorageLocation;
import som.vmobjects.SObject;
import som.vmobjects.SObject.SImmutableObject;
import som.vmobjects.SObject.SMutableObject;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.profiles.IntValueProfile;
import com.oracle.truffle.api.source.SourceSection;


@NodeChildren({
  @NodeChild(value = "self",  type = ExpressionNode.class),
  @NodeChild(value = "value", type = ExpressionNode.class)})
public abstract class InitializerFieldWrite extends ExpressionNode {

  protected final SlotDefinition slot;

  protected InitializerFieldWrite(final SlotDefinition slot,
      final SourceSection source) {
    super(source);
    this.slot = slot;
  }

  protected final LongStorageLocation getLongLocation(final ObjectLayout cachedLayout) {
    StorageLocation loc = cachedLayout.getStorageLocation(slot);
    if (loc instanceof LongStorageLocation) {
      return (LongStorageLocation) loc;
    }
    return null;
  }

  protected final DoubleStorageLocation getDoubleLocation(final ObjectLayout cachedLayout) {
    StorageLocation loc = cachedLayout.getStorageLocation(slot);
    if (loc instanceof DoubleStorageLocation) {
      return (DoubleStorageLocation) cachedLayout.getStorageLocation(slot);
    }
    return null;
  }

  protected final AbstractObjectStorageLocation getObjectLocation(final ObjectLayout cachedLayout) {
    StorageLocation loc = cachedLayout.getStorageLocation(slot);
    if (loc instanceof AbstractObjectStorageLocation) {
      return (AbstractObjectStorageLocation) cachedLayout.getStorageLocation(slot);
    }
    return null;
  }

  protected final StorageLocation getUnwrittenOrPrimitiveLocation(final ObjectLayout cachedLayout) {
    StorageLocation loc = cachedLayout.getStorageLocation(slot);
    if (loc instanceof UnwrittenStorageLocation ||
        loc instanceof LongStorageLocation      ||
        loc instanceof DoubleStorageLocation) {
      return cachedLayout.getStorageLocation(slot);
    }
    return null;
  }

  protected final IntValueProfile createProfile() {
    return IntValueProfile.createIdentityProfile();
  }


  @Specialization(
      assumptions = {"isLatestLayout"},
      guards = {
          "location != null",
          "cachedLayout == rcvr.getObjectLayout()",
          "location.isSet(rcvr, primMarkProfile)"},
      limit = "1")
  public final long longValueSet(final SImmutableObject rcvr, final long value,
      @Cached("createProfile()") final IntValueProfile primMarkProfile,
      @Cached("rcvr.getObjectLayout()") final ObjectLayout cachedLayout,
      @Cached("cachedLayout.getAssumption()") final Assumption isLatestLayout,
      @Cached("getLongLocation(cachedLayout)") final LongStorageLocation location) {
    location.writeLongSet(rcvr, value);
    return value;
  }

  @Specialization(
      assumptions = {"isLatestLayout"},
      guards   = {"location != null",
                  "cachedLayout == rcvr.getObjectLayout()"},
      contains = "longValueSet")
  public final long longValueSetOrUnset(final SImmutableObject rcvr, final long value,
      @Cached("rcvr.getObjectLayout()") final ObjectLayout cachedLayout,
      @Cached("cachedLayout.getAssumption()") final Assumption isLatestLayout,
      @Cached("getLongLocation(cachedLayout)") final LongStorageLocation location) {
    location.writeLongSet(rcvr, value);
    location.markAsSet(rcvr);
    return value;
  }

  @Specialization(
      assumptions = {"isLatestLayout"},
      guards = {
          "location != null",
          "cachedLayout == rcvr.getObjectLayout()",
          "location.isSet(rcvr, primMarkProfile)"},
      limit = "1")
  public final long longValueSet(final SMutableObject rcvr, final long value,
      @Cached("createProfile()") final IntValueProfile primMarkProfile,
      @Cached("rcvr.getObjectLayout()") final ObjectLayout cachedLayout,
      @Cached("cachedLayout.getAssumption()") final Assumption isLatestLayout,
      @Cached("getLongLocation(cachedLayout)") final LongStorageLocation location) {
    location.writeLongSet(rcvr, value);
    return value;
  }

  @Specialization(
      assumptions = {"isLatestLayout"},
      guards   = {"location != null",
                  "cachedLayout == rcvr.getObjectLayout()"},
      contains = "longValueSet")
  public final long longValueSetOrUnset(final SMutableObject rcvr, final long value,
      @Cached("rcvr.getObjectLayout()") final ObjectLayout cachedLayout,
      @Cached("cachedLayout.getAssumption()") final Assumption isLatestLayout,
      @Cached("getLongLocation(cachedLayout)") final LongStorageLocation location) {
    location.writeLongSet(rcvr, value);
    location.markAsSet(rcvr);
    return value;
  }

  @Specialization(
      assumptions = {"isLatestLayout"},
      guards = {"location != null",
                "cachedLayout == rcvr.getObjectLayout()",
                "location.isSet(rcvr, primMarkProfile)"},
      limit = "1")
  public final double doubleValueSet(final SMutableObject rcvr, final double value,
      @Cached("createProfile()") final IntValueProfile primMarkProfile,
      @Cached("rcvr.getObjectLayout()") final ObjectLayout cachedLayout,
      @Cached("cachedLayout.getAssumption()") final Assumption isLatestLayout,
      @Cached("getDoubleLocation(cachedLayout)") final DoubleStorageLocation location) {
    location.writeDoubleSet(rcvr, value);
    return value;
  }

  @Specialization(
      assumptions = {"isLatestLayout"},
      guards   = {"location != null",
                  "cachedLayout == rcvr.getObjectLayout()"},
      contains = "doubleValueSet")
  public final double doubleValueSetOrUnset(final SMutableObject rcvr, final double value,
      @Cached("rcvr.getObjectLayout()") final ObjectLayout cachedLayout,
      @Cached("cachedLayout.getAssumption()") final Assumption isLatestLayout,
      @Cached("getDoubleLocation(cachedLayout)") final DoubleStorageLocation location) {
    location.writeDoubleSet(rcvr, value);
    location.markAsSet(rcvr);
    return value;
  }

  @Specialization(
      assumptions = {"isLatestLayout"},
      guards = {"location != null",
                "cachedLayout == rcvr.getObjectLayout()",
                "location.isSet(rcvr, primMarkProfile)"},
      limit = "1")
  public final double doubleValueSet(final SImmutableObject rcvr, final double value,
      @Cached("createProfile()") final IntValueProfile primMarkProfile,
      @Cached("rcvr.getObjectLayout()") final ObjectLayout cachedLayout,
      @Cached("cachedLayout.getAssumption()") final Assumption isLatestLayout,
      @Cached("getDoubleLocation(cachedLayout)") final DoubleStorageLocation location) {
    location.writeDoubleSet(rcvr, value);
    return value;
  }

  @Specialization(
      assumptions = {"isLatestLayout"},
      guards   = {"location != null",
                  "cachedLayout == rcvr.getObjectLayout()"},
      contains = "doubleValueSet")
  public final double doubleValueSetOrUnset(final SImmutableObject rcvr, final double value,
      @Cached("rcvr.getObjectLayout()") final ObjectLayout cachedLayout,
      @Cached("cachedLayout.getAssumption()") final Assumption isLatestLayout,
      @Cached("getDoubleLocation(cachedLayout)") final DoubleStorageLocation location) {
    location.writeDoubleSet(rcvr, value);
    location.markAsSet(rcvr);
    return value;
  }

  @Specialization(
      assumptions = {"isLatestLayout"},
      guards   = {"location != null",
                  "cachedLayout == rcvr.getObjectLayout()"})
  public final Object objectValue(final SImmutableObject rcvr, final Object value,
      @Cached("rcvr.getObjectLayout()") final ObjectLayout cachedLayout,
      @Cached("cachedLayout.getAssumption()") final Assumption isLatestLayout,
      @Cached("getObjectLocation(cachedLayout)") final AbstractObjectStorageLocation location) {
    location.write(rcvr, value);
    return value;
  }

  @Specialization(
      assumptions = {"isLatestLayout"},
      guards   = {"location != null",
                  "cachedLayout == rcvr.getObjectLayout()"})
  public final Object objectValue(final SMutableObject rcvr, final Object value,
      @Cached("rcvr.getObjectLayout()") final ObjectLayout cachedLayout,
      @Cached("cachedLayout.getAssumption()") final Assumption isLatestLayout,
      @Cached("getObjectLocation(cachedLayout)") final AbstractObjectStorageLocation location) {
    location.write(rcvr, value);
    return value;
  }

  @Specialization(
      assumptions = {"isLatestLayout"},
      guards   = {"location != null",
                  "cachedLayout == rcvr.getObjectLayout()"})
  public final Object unwrittenOrGeneralizingValue(final SObject rcvr, final Object value,
      @Cached("rcvr.getObjectLayout()") final ObjectLayout cachedLayout,
      @Cached("cachedLayout.getAssumption()") final Assumption isLatestLayout,
      @Cached("getUnwrittenOrPrimitiveLocation(cachedLayout)") final StorageLocation location) {
    CompilerAsserts.neverPartOfCompilation("should never be part of a compiled AST.");
    rcvr.writeSlot(slot, value);
    return value;
  }

  @Fallback
  public final Object writeFallback(final Object rcvr, final Object value) {
    CompilerAsserts.neverPartOfCompilation("should never be part of a compiled AST.");
    ((SObject) rcvr).writeSlot(slot, value);
    return value;
  }
}
