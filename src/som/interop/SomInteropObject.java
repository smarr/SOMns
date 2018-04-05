package som.interop;

import com.oracle.truffle.api.interop.TruffleObject;


/**
 * This is a marker interface, which identifies {@link TruffleObject}s,
 * which are also SOMns objects, and thus, do not need to be handled as foreign ones.
 */
public interface SomInteropObject extends TruffleObject {

}
