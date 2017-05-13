/*
 * Copyright (c) 2017, Red Hat Inc.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 *
 *  * Neither the name of Oracle nor the names of its contributors may be used
 *    to endorse or promote products derived from this software without
 *    specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF
 * THE POSSIBILITY OF SUCH DAMAGE.
 */
package som.jcstress;

import java.io.IOException;

import org.openjdk.jcstress.annotations.Actor;
import org.openjdk.jcstress.annotations.Arbiter;
import org.openjdk.jcstress.annotations.Expect;
import org.openjdk.jcstress.annotations.JCStressTest;
import org.openjdk.jcstress.annotations.Outcome;
import org.openjdk.jcstress.annotations.State;
import org.openjdk.jcstress.infra.results.LI_Result;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.nodes.IndirectCallNode;

import som.interpreter.nodes.dispatch.Dispatchable;
import som.interpreter.objectstorage.ObjectTransitionSafepoint;
import som.vmobjects.SClass;
import som.vmobjects.SObject.SMutableObject;

@JCStressTest
// Outline the outcomes here. The default outcome is provided, you need to remove it:
@Outcome(id = "o, 1", expect = Expect.ACCEPTABLE, desc = "Default outcome.")
@State
public class ConcurrencyTest {

  private static final ObjectFact objectFact;
  private static IndirectCallNode node = Truffle.getRuntime().createIndirectCallNode();

  private final SMutableObject object;
  private final SClass clazz;
  private final Dispatchable writeA;
  private final Dispatchable writeB;

  static {
    try {
      objectFact = new ObjectFact();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public ConcurrencyTest() {
    object = objectFact.getObject();
    clazz = objectFact.getClazz();
    writeA = objectFact.getWriteA();
    writeB = objectFact.getWriteB();
  }

  @Actor
  public void actor1(final LI_Result r) {
    ObjectTransitionSafepoint.INSTANCE.register();
    r.r1 = writeA.invoke(node, new Object[] {clazz, object, object});
    ObjectTransitionSafepoint.INSTANCE.unregister();
  }

  @Actor
  public void actor2(final LI_Result r) {
    ObjectTransitionSafepoint.INSTANCE.register();
    r.r2 = ((Long) writeB.invoke(node, new Object[] {clazz, object, 1L})).intValue();
    ObjectTransitionSafepoint.INSTANCE.unregister();
  }

  @Arbiter
  public void arbiter(final LI_Result r) {
    if (r.r1 == object) {
      r.r1 = "o";
    }
  }
}
