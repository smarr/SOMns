# Communicating Sequential Processes for Newspeak/SOMns

> These notes were originally written as blog post and published on
> [stefan-marr.de](http://stefan-marr.de/2017/01/communicating-sequential-processes-for-newspeak-somns/).

One possible way for modeling concurrent systems is Tony Hoare's classic approach of having isolated processes communicate via channels, which is called [Communicating Sequential Processes (CSP)](https://en.wikipedia.org/wiki/Communicating_sequential_processes). Today, we see the approach used for instance in [Go](https://blog.golang.org/share-memory-by-communicating) and [Clojure](http://clojure.com/blog/2013/06/28/clojure-core-async-channels.html).

While [Newspeak's specification](http://bracha.org/newspeak-spec.pdf) and implementation come with support for Actors, I want to experiment also with other abstractions, and CSP happens to be an interesting one, since it models systems with blocking synchronization, also known as channels with rendezvous semantics. I am not saying CSP is better in any specific case than actors. Instead, I want to find out where CSP's abstractions provide a tangible benefit.

But, the reason for this post is another one. One of my biggest quibbles with most CSP implementations is that they don't take isolation serious. Usually, they provide merely lightweight concurrency and channels, but they rarely ensure that different processes don't share any mutable memory. So, the door for low-level race conditions is wide open. The standard argument of language or library implementers is that guaranteeing isolation is not worth the performance overhead that comes with it. For me, concurrency is hard enough, so, I prefer to have the guarantee of proper isolation. Of course, another part of the argument is that you might need shared memory for some problems, but, I think we got [a more disciplined approach for those problems](http://stefan-marr.de/2016/02/domains-sharing-state-in-the-communicating-event-loop-actor-model/), too.

## Isolated Processes in Newspeak

Ok, so how can we realize isolated processes in Newspeak? As it turns out, it is pretty simple. Newspeak already got the notion of _values_. Values are deeply immutable objects. This means values can only contain values themselves, which as a consequence means, if you receive some value from a concurrent entity, you are guaranteed that the state never changes.

In [SOMns](https://github.com/smarr/SOMns), you can use the [`Value`](https://github.com/smarr/SOMns/blob/master/core-lib/Kernel.som#L82) mixin to mark a class as having value semantics. This means that none of the fields of the object are allowed to be mutable, and that we need to check that fields are only initialized with values in the object's constructor. Since Newspeak uses nested classes pretty much everywhere, we also need to check that the outer scope of a value class does not have any mutable state. Once that is verified, an object can be a proper deeply immutable value, and can be shared without introducing any data races between concurrent entities.

Using this as a foundation, we can require that all classes that represent CSP processes are values. This gives us the guarantee that a process does not have access to any shared mutable state by itself. Note, this is only about the class side. The object side can actually be a normal object an have mutable state, which means, within a process, we can have normal mutable state/objects.

Using the _value_ notion of Newspeak feels like a very natural solution to me. Alternative approaches could use a magic operator that cuts off lexical scope. This is something that I have seen for instance in AmbientTalk with its [isolates](https://soft.vub.ac.be/amop/at/tutorial/actors#isolates). While this magic `isolate` keyword gives some extra flexibility, it is also a new concept. Having to ensure that a process' class is a value requires that its outer lexical scope is a value, and thus, restricts a bit how we structure our modules, but, it doesn't require any new concepts. One other drawback is here that it is often not clear that the lexical scope is a value, but I think that's something where an IDE should help and provide the necessary insights.

In code, this looks then a bit like this:

```java
class ExampleModule = Value ()(
  class DoneProcess new: channelOut = Process (
  | private channelOut = channelOut. |
  )(
    public run = ( channelOut write: #done )
  )

  public start = (
    processes spawn: DoneProcess
               with: {Channel new out}
  )
)
```

So, we got a class `DoneProcess`, which has a `run` method that defines what the process does. Our `processes` module allows us to spawn the process with arguments, which is in this case the output end of a channel.

## Channels

The other aspect we need to think about is how can we design channels so that they preserve isolation. As a first step, I'll only allow to send values on the channel. This ensures isolation and is enabled by a simple and efficient check for whether the provided object is a value.

However, this approach is also very restrictive. Because of the deeply immutable semantics of values, they are quite inflexible in my experience.

When thinking of what it means to be a value, imagine a bunch of random objects: they all can point to values, but values can never point back to any mutable object. That's a very nice property from the concurrency perspective, but in practice this means that I often feel the need to represent data twice. Once as mutable, for instance for constructing complex data structures, and a second time as values so that I can send data to another process.

A possible solution might be objects with copy-on-transfer semantics, or actual ownership transfer. This could be modeled either with a new type of transfer objects, or a copying channel. Perhaps there are other options out there. But for the moment, I am already happy with seeing that we can have proper CSP semantics by merely checking that a process is constructed from values only and that channels only pass on values.

Since the [implementation is mostly a sketch](https://github.com/smarr/SOMns/pull/84), there are of course more things that need to be done. For instance, it doesn't yet support any nondeterminism, which requires an `alt` or `select` operation on channels.
