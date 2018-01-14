# Interaction with Kompos Debugger

This section gives a brief overview of the interaction between SOMns and Kompos.

## Communication with Kompos

Communication between Kompos and SOMns is implemented with WebSockets, the code can be found in [tools.debugger.FrontendConnector](https://github.com/MetaConc/SOMns/blob/16af2c3e3a41d13ab8c10ec289181db9284b49aa/src/tools/debugger/FrontendConnector.java) and [vm-connection.ts](https://github.com/MetaConc/SOMns/blob/16af2c3e3a41d13ab8c10ec289181db9284b49aa/tools/kompos/src/vm-connection.ts). Data is transferred on two separate sockets, one is for JSON and the other one is for binary data.

JSON is used for two-way communication in a message-oriented style. Message objects are [serialized to JSON](https://github.com/MetaConc/SOMns/blob/16af2c3e3a41d13ab8c10ec289181db9284b49aa/src/tools/debugger/FrontendConnector.java#L181) when sending, and [received JSON data](https://github.com/smarr/SOMns/blob/16075443872e8de63e2bc71817552731f85eb1f0/src/tools/debugger/WebSocketHandler.java#L40) is used to create message objects.  Kompos also (de)serializes communication data, [messages.ts](https://github.com/MetaConc/SOMns/blob/16af2c3e3a41d13ab8c10ec289181db9284b49aa/tools/kompos/src/messages.ts) provides some interfaces that help accessing data of message objects.

On the SOMns side, message classes need to be "registered" in [tools.debugger.WebDebugger.createJsonProcessor()](https://github.com/MetaConc/SOMns/blob/16af2c3e3a41d13ab8c10ec289181db9284b49aa/src/tools/debugger/WebDebugger.java#L211), they extend either IncomingMessage or OutgoingMessage. When an IncomingMessage is received, its [process method](https://github.com/MetaConc/SOMns/blob/16af2c3e3a41d13ab8c10ec289181db9284b49aa/src/tools/debugger/message/Message.java#L9) is called.

The binary socket is used for directly sending tracing data, which is pushed to
Kompos whenever available. Kompos parses the tracing data according to the
trace format and uses the data to generate the actor graph.

## Trace Format

The trace data includes for instance the following events:

- Actor creation
- Promise creation
- Promise resolution: when a Promise is resolved with a value.
- Promise chained: when a Promise is resolved with another Promise.
- and various others

An academic write up of the tracing feature and its format is part of our paper
titled [A Concurrency-Agnostic Protocol for Multi-Paradigm Concurrent Debugging Tools](http://stefan-marr.de/papers/dls-marr-et-al-concurrency-agnostic-protocol-for-debugging/).

## Startup Protocol

The following diagram gives an overview of the startup protocol. For simplicity the binary WebSocket for trace data and the view object are not included.

<figure style="text-align:center">
<img style="width:600px" src="../kompos-startup.svg" alt="kompos startup protocol" />
<figcaption>
Simplified overview of startup protocol between SOMns interpreter and Kompos debugger.
</figcaption>
</figure>
