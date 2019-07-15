// Copyright (c) 2001-2016 see AUTHORS.md file
//
// Permission is hereby granted, free of charge, to any person obtaining a copy
// of this software and associated documentation files (the 'Software'), to deal
// in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
// copies of the Software, and to permit persons to whom the Software is
// furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in
// all copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED 'AS IS', WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
// AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
// THE SOFTWARE.
//
//
// Adapted for Grace by Richard Roberts
//   2018, June
//

def Array: List = platform.kernel.Array
def initialSize: Number = 10.asInteger
def initialCapacity: Number = 16.asInteger

type Pair = interface {
  key
  value
}

type Vector = interface {
  firstIdx
  lastIdx
  storage
  at(index)
  at(index)put(val)
  append(element)
  isEmpty
  forEach(block)
  hasSome(block)
  getOne(block)
  removeFirst
  removeAll
  remove(obj)
  size
  capacity
  sort(aBlock)
  sort(i)to(j)with(sortBlock)
}

type Set = interface {
  items
  forEach(block)
  hasSome(block)
  getOne(block)
  add(anObject)
  collect(block)
  contains(anObject)
  size
  removeAll
}

type Dictionary = interface {
  hash(key)
  bucketIdx(hash)
  bucket(hash)
  at(aKey)
  containsKey(aKey)
  at(aKey)put(aVal)
  newEntry(aKey)value(value)hash(hash)
  insertBucketEntry(key)value(value)hash(hash)head(head)
  resize
  transferEntries(oldStorage)
  splitBucket(oldStorage)bucket(i)head(head)
  size
  isEmpty
  removeAll
  keys
  values
}

class newPairWithKey (key: Unknown) andValue (value: Unknown) -> Pair {}

class newVector (size': Number) -> Vector {
  var firstIdx: Number := 1.asInteger
  var lastIdx: Number := 1.asInteger
  var storage: List := Array.new(size')

  method at(index: Number) -> Unknown {
    (index > storage.size).ifTrue { return Done }
    return storage.at(index)
  }

  method at(index: Number) put (val: Unknown) -> Done {
    (index > storage.size).ifTrue {
      var newLength: Number := storage.size
      { newLength < index }.whileTrue { newLength := newLength * 2.asInteger }
      var newStorage: List := Array.new(newLength)
      storage.doIndexes { i: Number -> newStorage.at (i) put (storage.at(i)) }
      storage := newStorage
    }

    storage.at (index) put (val)
    (lastIdx < (index + 1.asInteger)).ifTrue {
      lastIdx := index + 1.asInteger
    }

    Done
  }

  method append(element: Unknown) -> Vector {
    (lastIdx > storage.size).ifTrue {
      // Need to expand capacity first
      var newStorage: List := Array.new(2.asInteger * storage.size)
      storage.doIndexes { i: Number -> newStorage. at (i) put (storage.at(i)) }
      storage := newStorage
    }

    storage.at (lastIdx) put (element)
    lastIdx := lastIdx + 1.asInteger
    return self
  }

  method isEmpty -> Boolean { return lastIdx == firstIdx }

  method forEach(block: Invokable) -> Done {
    firstIdx.to (lastIdx - 1.asInteger) do { i: Number -> block.value(storage.at(i)) }
    Done
  }

  method hasSome(block: Invokable) -> Boolean {
    firstIdx.to (lastIdx - 1.asInteger) do { i: Number ->
      block.value(storage.at(i)).ifTrue { return true }
    }
    return false
  }

  method getOne(block: Invokable) -> Unknown {
    firstIdx.to(lastIdx - 1.asInteger) do { i: Number ->
      var e: Unknown := storage.at(i)
      block.value(e).ifTrue { return e }
    }
    return Done
  }

  method removeFirst -> Unknown {
    isEmpty.ifTrue { return Done }

    firstIdx := firstIdx + 1.asInteger
    return storage.at(firstIdx - 1.asInteger)
  }

  method removeAll -> Done {
    firstIdx := 1.asInteger
    lastIdx  := 1.asInteger
    storage := Array.new (storage.size)
    Done
  }

  method remove(obj: Unknown) -> Unknown {
    var newArray: List := Array.new (capacity)
    var newLast: Number := 1.asInteger
    var found: Boolean := false

    forEach { it: Unknown ->
      (it == obj).ifTrue {
        found := true
      } ifFalse {
          newArray.at (newLast) put (it)
          newLast := newLast + 1.asInteger
      }
    }

    storage := newArray
    lastIdx := newLast
    firstIdx := 1.asInteger
    return found
  }

  method size -> Number { return lastIdx - firstIdx }

  method capacity -> Number { return storage.size }

  // Make the argument, aBlock, be the criterion for ordering elements of
  // the receiver.
  // sortBlocks with side effects may not work right
  method sort(aBlock: Invokable) -> Done {
    (size > 0).ifTrue {
      sort (firstIdx) to (lastIdx - 1.asInteger) with (aBlock)
    }
    Done
  }

  // Sort elements i through j of self to be non-descending according to
  // sortBlock.
  method sort(i: Number) to (j: Number) with (sortBlock: Invokable) -> Done {
    sortBlock.isNil.ifTrue { return defaultSort (i) to (j)  }

    // The prefix d means the data at that index.
    var n: Number := j + 1.asInteger - i
    (n <= 1).ifTrue { return self } // Nothing to sort.

    // Sort di,dj.
    var di: Unknown := storage.at(i)
    var dj: Unknown := storage.at(j)

    // i.e., should di precede dj?

    sortBlock.apply (di, dj). ifFalse {
      storage.swap(i) with (j)
      var tt: Unknown := di
      di := dj
      dj := tt
    }

    // More than two elements.
    (n > 2.asInteger).ifTrue {
      // ij is the midpoint of i and j.
      var ij: Number := ((i + j) / 2).asInteger
      var dij: Unknown := storage.at(ij)

      // Sort di,dij,dj. Make dij be their median.
      sortBlock.value (di) with (dij). ifTrue {
        // i.e. should di  precede dij?
        sortBlock.value (dij) with (dj). ifFalse {
          // i.e. should dij precede dj ?
          storage. swap (j) with (ij)
          dij := dj
        }
      } ifFalse {
        // i.e. di should come after dij
        storage.swap(i) with (ij)
        dij := di
      }

      (n > 3).ifTrue {
        // More than three elements.
        // Find k>i and l<j such that dk,dij,dl are in reverse order.
        // Swap k and l.  Repeat this procedure until k and l pass each other.
        var k: Number := i
        var l: Number := j

        { { l := l - 1.asInteger
            (k <= l).and { sortBlock.value (dij) with (storage.at(l)) }
          }.whileTrue  // i.e. while dl succeeds dij
          { k := k + 1.asInteger
            (k <= l).and { sortBlock.value (storage.at(k)) with (dij) }
            whileTrue  // i.e. while dij succeeds dk
          }
          k <= l
        }.whileTrue {
          storage. swap (k) with (l)
        }

        // Now l<k (either 1 or 2 less), and di through dl are all less than or equal to dk
        // through dj.  Sort those two segments.
        sort (i) to (l) with (sortBlock)
        sort (k) to (j) with (sortBlock)
      }
    }
  }

  Done
}

method newVector -> Vector {
  return newVector(50.asInteger)
}

method newVectorWith(elem: Unknown) -> Vector {
  var v: Vector := newVector(1.asInteger)
  v.append(elem)
  return v
}

class newSet(size': Number) -> Set {
  var items: Vector := newVector(size')

  method forEach (block: Invokable) -> Done {
    items.forEach(block)
    Done
  }

  method hasSome (block: Invokable) -> Boolean {return items.hasSome (block) }

  method getOne (block: Invokable) -> Unknown {return items.getOne (block) }

  method add (anObject: Unknown) -> Done {
    contains(anObject).ifFalse { items.append(anObject) }
    Done
  }

  method collect (block: Invokable) -> Vector {
    var coll: Vector := Vector.new
    forEach { e: Unknown -> coll.append(block.value(e)) }
    return coll
  }

  method contains (anObject: Unknown) -> Boolean {
    return hasSome { it: Unknown -> it == anObject }
  }

  method size -> Number { return items.size }
  method removeAll -> Done { return items.removeAll }
}

method newSet -> Set { return newSet(initialSize) }

class newIdentitySet(size': Number) -> Set {
  inherit newSet(size')

  method contains (anObject: Unknown) -> Boolean {
    return hasSome { it: Unknown -> it == anObject }
  }
}

method newIdentitySet -> Set { return newIdentitySet(initialSize) }


type Entry = interface {
  value
  next
  match(aHash)key(aKey)
}

class newDictionary(size': Number) -> Dictionary {
  var buckets: List := Array.new(size')
  var size_: Number   := 0.asInteger

  class newEntryWithHashKeyValueNext (hash': Number, key': Unknown, val': Unknown, next': Entry) -> Entry {
    def hash: Number = hash'
    def key: Unknown = key'
    var value: Unknown := val'
    var next: Entry := next'

    method match (aHash: Number) key (aKey: Unknown) -> Boolean {
      return (hash == aHash).and { key == aKey }
    }
  }

  method hash(key: Unknown) -> Number {
    key.isNil.ifTrue { return 0.asInteger }
    var hash: Number := key.customHash
    return hash.bitXor(hash.bitRightShift(16.asInteger))
  }

  method bucketIdx(hash: Number) -> Number {
    return 1.asInteger + (buckets.size - 1.asInteger).bitAnd(hash)
  }

  method bucket(hash: Number) -> Entry {
    return buckets.at(bucketIdx(hash))
  }

  method at(aKey: Unknown) -> Unknown {
    var hash: Number := hash(aKey)
    var e: Entry := bucket(hash)

    { e.notNil }. whileTrue {
      e.match(hash)key(aKey).ifTrue { return e.value }
      e := e.next
    }
    return Done
  }

  method containsKey(aKey: Unknown) -> Boolean {
    var hash: Number := hash(aKey)
    var e: Entry := bucket(hash)

    { e.notNil }. whileTrue {
      e.match(hash.key(aKey)).ifTrue { return true }
      e := e.next
    }
    return false
  }

  method at(aKey: Unknown) put (aVal: Unknown) -> Done {
    var hash: Number := hash(aKey)
    var i: Number := bucketIdx(hash)
    var current: Entry := buckets.at(i)

    current.isNil.ifTrue {
      buckets.at(i) put (newEntry (aKey) value (aVal) hash (hash))
      size_ := size_ + 1.asInteger
    } ifFalse {
      insertBucketEntry (aKey) value (aVal) hash (hash) head (current )
    }

    (size_ > buckets.size).ifTrue { resize }
    Done
  }

  method newEntryWithKeyValueHash(aKey: Unknown, value: Unknown, hash: Number) -> Entry {
    return newEntryWithHashKeyValueNext (hash, aKey, value, Done)
  }

  method insertBucketEntry(key: Unknown) value (value: Unknown) hash (hash: Number) head (head: Entry) -> Dictionary {
    var current: Entry := head

    { true }. whileTrue {
      current.match(hash)key(key).ifTrue {
        current.value := value
        return self
      }

      current.next.isNil.ifTrue {
        size_ := size_ + 1.asInteger
        current.next := newEntry (key) value (value) hash (hash)
        return self
      }
      current := current.next
    }
  }

  method resize -> Done {
    var oldStorage: List := buckets
    buckets := Array.new (oldStorage.size * 2.asInteger)
    transferEntries(oldStorage)
    Done
  }

  method transferEntries(oldStorage: List) -> Done {
    1.asInteger.to(oldStorage.size) do { i: Number ->
      var current: Entry := oldStorage.at(i)

      current.notNil.ifTrue {
        oldStorage.at (i) put (Done)
        current.next.isNil.ifTrue {
          buckets.at (1.asInteger + (current.hash.bitAnd(buckets.size - 1.asInteger))) put (current)
        } ifFalse {
          splitBucket(oldStorage) bucket (i) head (current)
        }
      }
    }
    Done
  }

  method splitBucket(oldStorage: List) bucket (i: Number) head (head: Entry) -> Done {
    var loHead: Entry := Done
    var loTail: Entry := Done
    var hiHead: Entry := Done
    var hiTail: Entry := Done
    var current: Entry := head

    { current.notNil }. whileTrue {
      (current.hash.bitAnd(oldStorage.size) == 0.asInteger).ifTrue {
          loTail.isNil.ifTrue {
            loHead := current
          } ifFalse {
            loTail.next := current
          }
          loTail := current
      } ifFalse {
          hiTail.isNil.ifTrue {
            hiHead := current
          } ifFalse {
            hiTail.next := current
          }
          hiTail := current
      }
      current := current.next
    }

    loTail.notNil.ifTrue {
      loTail.next := Done
      buckets.at(i) put (loHead )
    }

    hiTail.notNil.ifTrue {
      hiTail.next := Done
      buckets.at(i + oldStorage.size) put (hiHead)
    }

    Done
  }

  method size -> Number { return size_ }

  method isEmpty -> Boolean { return size_ == 0.asInteger }

  method removeAll -> Done {
    buckets := Array.new(buckets.size)
    size_ := 0.asInteger
    Done
  }

  method keys -> Vector {
    var keys: Vector := newVector(size_)
    buckets.do { b: Entry ->
      var current: Unknown := b
      { current.notNil }. whileTrue {
        keys.append(current.key)
        current := current.next
      }
    }
    return keys
  }

  method values -> Vector {
    var values: Vector := newVector(size_)
    buckets.do { b: Entry ->
      var current: Unknown := b
      { current.notNil }. whileTrue {
        values.append(current.value)
        current := current.next
      }
    }
    return values
  }
}

method newDictionary -> Dictionary { return newDictionary(initialCapacity) }

class newIdentityDictionary(size': Number) -> Dictionary {
  inherit newDictionary(size')

  class newIdEntryWithHashKeyValueNext (hash: Number, key: Unknown, val: Unknown, next: Entry) -> Entry {
    inherit newEntryWithHashKeyValueNext (hash, key, val, next)

    method match(aHash: Number) key(aKey: Unknown) -> Boolean {
      return (hash == aHash). and { key == aKey }
    }

  }

  method newEntry(aKey: Unknown) value (value: Unknown) hash (hash: Number) -> Entry {
    return newIdEntryWithHashKeyValueNext (hash, aKey, value, Done)
  }
}

method newIdentityDictionary -> Dictionary { return newIdentityDictionary(initialCapacity) }
