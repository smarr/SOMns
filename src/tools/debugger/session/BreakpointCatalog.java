package tools.debugger.session;

/**
 * Different breakpoints supported.
 *
 * @author carmentorres
 *
 */
public class BreakpointCatalog {

  //conditional breakpoint---

  //code breakpoint---
  //defines a breakpoint which is tied to a particular line of code.
  public void codeBreakpoint(final int lineNumber, final String fileName) {

  }

  //message breakpoint---
//defines a breakpoint on an asynchronous message send
 // the execution pauses before the receiver invokes the method
 // corresponding to the aysnchronous message sent in the given of code.


  //messageResolvedBreakpoint---

  //methodBreakpoint---
//defines a breakpoint on a method
 // the execution pauses before the receiver invokes the method
 // defined in the given line of code.
 // It only pauses executions as a result of an asynchronous message.


  //futureResolutionBreakpoint---
//defines a breakpoint on the resolution of a future attached to a message
 // it is used internally by the debugger while executing an stepReturn command on a future-type message send,
 // and for future-type message sends matching a messageReturnBreakpoints


  //symbolBreakpoint---
  // defines a breakpoint on a method name
  // the execution pauses before the receiver invokes a method
  // with the given name ( as a result of an asynchronously message)
}
