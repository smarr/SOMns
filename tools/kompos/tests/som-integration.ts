import { expect } from "chai";
import { spawn } from "child_process";
import { SomConnection, closeConnection, SOM, execSom, HandleFirstSuspendEvent,
  startSomAndConnect, expectStack } from "./test-setup";


describe("Command-line Behavior", function() {
  it("should show help", done => {
    let sawOutput = false;
    const somProc = spawn(SOM, ["-h"]);
    somProc.stdout.on("data", (_data) => { sawOutput = true; });
    somProc.on("exit", (code) => {
      expect(sawOutput).to.be.true;
      expect(code).to.equal(0);
      done();
    });
  });

});

describe("Stack trace output", () => {
  it("should be correct for #doesNotUnderstand", () => {
    const result = execSom(["dnu"]);
    expect(result.output[1].toString().replace(/\d/g, "")).to.equal("Stack Trace\n\
\tPlatform>>#start                             Platform.som::\n\
\tBlock>>#on:do:                               Kernel.som::\n\
\tvmMirror>>#exceptionDo:catch:onException:    ExceptionDoOnPrimFactory::\n\
\tPlatform>>#$blockMethod@@                Platform.som::\n\
\tPingPongApp>>#main:                          pingpong.som::\n\
\tPingPongApp>>#testDNU                        pingpong.som::\n\
ERROR: MessageNotUnderstood(Integer>>#foobar)\n");
  });

  it("should be correct for `system printStackTrace`", () => {
    const result = execSom(["stack"]);
    expect(result.output[1].toString().replace(/\d/g, "")).to.equal("Stack Trace\n\
\tPlatform>>#start                             Platform.som::\n\
\tBlock>>#on:do:                               Kernel.som::\n\
\tvmMirror>>#exceptionDo:catch:onException:    ExceptionDoOnPrimFactory::\n\
\tPlatform>>#$blockMethod@@                Platform.som::\n\
\tPingPongApp>>#main:                          pingpong.som::\n\
\tPingPongApp>>#testPrintStackTrace            pingpong.som::\n");
  });
});

describe("Language Debugger Integration", function() {

  let connectionP: Promise<SomConnection> = null;
  const closeConnectionAfterSuite = (done) => {
    connectionP.then(c => { closeConnection(c, done); });
    connectionP.catch(reason => done(reason));
  };

  describe("execute `1 halt` and get suspended event", () => {
    const event = new HandleFirstSuspendEvent();

    before("Start SOMns and Connect", () => {
      connectionP = startSomAndConnect(event.getSuspendEvent, [], ["halt"]);
    });

    after(closeConnectionAfterSuite);

    it("should halt on expected source section", () => {
      return event.suspendP.then(msg => {
        expectStack(msg.stack, 7, "PingPongApp>>#testHalt", 84);
      });
    });
  });
});
