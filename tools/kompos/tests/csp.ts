import { resolve } from "path";
import { SomConnection, closeConnection, expectStack,
  HandleFirstSuspendEvent, startSomAndConnect } from "./test-setup";
import {createSectionBreakpointData} from "../src/messages";

const CSP_FILE = resolve("tests/pingpong-csp.som");
const CSP_URI  = "file:" + CSP_FILE;

describe("Setting CSP Breakpoints", () => {
  let connectionP: Promise<SomConnection> = null;
  const closeConnectionAfterSuite = (done) => {
    connectionP.then(c => { closeConnection(c, done); });
    connectionP.catch(reason => done(reason));
  };

  describe("Setting Before Read Breakpoint", () => {
    const event = new HandleFirstSuspendEvent();

    before("Start SOMns and Connect", () => {
      const breakpoint = createSectionBreakpointData(CSP_URI, 13, 12, 4,
        "MessageSenderBreakpoint", true);
      connectionP = startSomAndConnect(event.getSuspendEvent, [breakpoint],
        null, null, CSP_FILE);
    });

    after(closeConnectionAfterSuite);

    it("should break on #read", () => {
      return event.suspendP.then(msg => {
        expectStack(msg.stack, 1, "Ping>>#run", 13);
      });
    });
  });

  describe("Setting After Write Breakpoint", () => {
    const event = new HandleFirstSuspendEvent();

    before("Start SOMns and Connect", () => {
      const breakpoint = createSectionBreakpointData(CSP_URI, 13, 12, 4,
        "ChannelOppositeBreakpoint", true);
      connectionP = startSomAndConnect(event.getSuspendEvent, [breakpoint],
        null, null, CSP_FILE);
    });

    after(closeConnectionAfterSuite);

    it("should break after #write:", () => {
      return event.suspendP.then(msg => {
        expectStack(msg.stack, 6, "PingPongCSP>>#main:", 24);
      });
    });
  });

  describe("Setting Before Write Breakpoint", () => {
    const event = new HandleFirstSuspendEvent();

    before("Start SOMns and Connect", () => {
      const breakpoint = createSectionBreakpointData(CSP_URI, 12, 13, 12,
        "MessageSenderBreakpoint", true);
      connectionP = startSomAndConnect(event.getSuspendEvent, [breakpoint],
        null, null, CSP_FILE);
    });

    after(closeConnectionAfterSuite);

    it("should break on #write:", () => {
      return event.suspendP.then(msg => {
        expectStack(msg.stack, 1, "Ping>>#run", 12);
      });
    });
  });

  describe("Setting After Read Breakpoint", () => {
    const event = new HandleFirstSuspendEvent();

    before("Start SOMns and Connect", () => {
      const breakpoint = createSectionBreakpointData(CSP_URI, 12, 13, 12,
        "ChannelOppositeBreakpoint", true);
      connectionP = startSomAndConnect(event.getSuspendEvent, [breakpoint],
        null, null, CSP_FILE);
    });

    after(closeConnectionAfterSuite);

    it("should break after #read", () => {
      return event.suspendP.then(msg => {
        expectStack(msg.stack, 6, "PingPongCSP>>#main:", 23);
      });
    });
  });
});
