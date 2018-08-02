// Ported from the adapted JavaScript and Java versions.
//
// Copyright (c) 2001-2010, Purdue University. All rights reserved.
// Copyright (C) 2015 Apple Inc. All rights reserved.
//
// Redistribution and use in source and binary forms, with or without
// modification, are permitted provided that the following conditions are met:
//  * Redistributions of source code must retain the above copyright
//    notice, this list of conditions and the following disclaimer.
//  * Redistributions in binary form must reproduce the above copyright
//    notice, this list of conditions and the following disclaimer in the
//    documentation and/or other materials provided with the distribution.
//  * Neither the name of the Purdue University nor the
//    names of its contributors may be used to endorse or promote products
//    derived from this software without specific prior written permission.
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
// ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
// WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
// DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER BE LIABLE FOR ANY
// DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
// (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
// LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
// ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
// (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
// SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
//
//
// Adapted for Grace by Richard Roberts
//   2018, June
//

import "harness" as harness
import "Core" as core

type Symbol = interface {
  customName
}

type Vector2D = interface {
  x
  y
  plus(other)
  minus(other)
  compareTo(other)
  compare(a)and(b)
}

type Vector3D = interface {
  x
  y
  z
  plus(other)
  minus(other)
  dot(other)
  squaredMagnitude
  magnitude
  times(amount)
}

type Node = interface {
  key
  value
  left
  right
  parent
  color
  successor
}

type CallSign = interface {
  value
  compareTo(other)
}

type Collision = interface {
  aircraftA
  aircraftB
  position
}

type CollisionDectector = interface {
  state
  handleNewFrame(frame)
  isInVoxel(voxel)motion(motion)
  put(motion)and(voxel)into(voxelMap)
  recurse(voxelMap)seen(seen)voxel(nextVoxel)motion(motion)
  reduceCollisionSet(motions)
  voxelHash(position)
  draw(motion)on(voxelMap)
}

type EntryWithKeyValue = interface {
  key
  value
}

type InsertResult = interface {
  isNewEntry
  newNode
  oldValue
}

type RedBlackTree = interface {
  root
  at(key)put(value)
  remove(key)
  at(key)
  forEach(block)
  findNode(key)
  treeAt(key)insert(value)
  leftRotate(x)
  rightRotate(y)
  remove(anX)andFixup(anXParent)
}

type Motion = interface {
  callsign
  posOne
  posTwo
  delta
  findIntersection(other)
}

type Aircraft = interface {
  callsign
  position
}

type Simulator = interface {
  numAircrafts
  aircrafts
  simulate(time)
}

def MinX: Number = 0.0
def MinY: Number = 0.0
def MaxX: Number = 1000.0
def MaxY: Number = 1000.0
def MinZ: Number = 0.0
def MaxZ: Number = 10.0
def ProximityRadius: Number = 1.0
def GoodVoxelSize: Number = ProximityRadius * 2.0

def horizontal: Vector2D = newVector2DWith (GoodVoxelSize) and (0.0)
def vertical: Vector2D = newVector2DWith (0.0) and (GoodVoxelSize)


class newCD -> Benchmark {
  inherit harness.newBenchmark

  method benchmark (numAircrafts: Number) -> Number {
    var numFrames: Number := 200.asInteger

    var simulator: Simulator := newSimulator(numAircrafts)
    var detector: CollisionDectector := newCollisionDetector

    var actualCollisions: Number := 0.asInteger

    0.asInteger.to (numFrames - 1.asInteger) do { i: Number ->
      var time: Number := i / 10.0
      var collisions: Vector := detector.handleNewFrame(simulator.simulate(time))
      actualCollisions := actualCollisions + collisions.size
    }

    return actualCollisions
  }

  method innerBenchmarkLoop (innerIterations: Number) -> Boolean {
    return verify (benchmark(innerIterations)) resultFor (innerIterations)
  }

  method verify (actualCollisions: Number) resultFor (numAircrafts: Number) -> Boolean {
    (numAircrafts == 1000.asInteger). ifTrue { return actualCollisions == 14484.asInteger }
    (numAircrafts ==  500.asInteger). ifTrue { return actualCollisions == 14484.asInteger }
    (numAircrafts ==  250.asInteger). ifTrue { return actualCollisions == 10830.asInteger }
    (numAircrafts ==  200.asInteger). ifTrue { return actualCollisions ==  8655.asInteger }
    (numAircrafts ==  100.asInteger). ifTrue { return actualCollisions ==  4305.asInteger }
    (numAircrafts ==   10.asInteger). ifTrue { return actualCollisions ==   390.asInteger }

    print("No verification result for {numAircrafts} found.")
    print("Result is: {actualCollisions}")
    return false
  }
}

class newVector2DWith(x': Number)and(y': Number) -> Vector2D {
  method x -> Number { x' }
  method y -> Number { y' }

  method plus (other: Vector2D) -> Vector2D {
    return newVector2DWith(x' + other.x)and(y' + other.y)
  }

  method minus (other: Vector2D) -> Vector2D {
    return newVector2DWith(x' - other.x)and(y' - other.y)
  }

  method compareTo (other: Vector2D) -> Number {
    var result: Number := compare (x') and (other.x)
    (result != 0.asInteger). ifTrue { return result }
    return compare (y') and (other.y)
  }

  method compare (a: Number) and (b: Number) -> Number {
    (a == b). ifTrue { return  0.asInteger }
    (a  < b). ifTrue { return -1.asInteger }
    (a  > b). ifTrue { return  1.asInteger }

    // We say that NaN is smaller than non-NaN.
    (a == a). ifTrue { return 1.asInteger }
    return -1.asInteger
  }
}

class newVector3DWith (x': Number) and (y': Number) and (z': Number) -> Vector3D {
  method x -> Number { x' }
  method y -> Number { y' }
  method z -> Number { z' }

  method plus (other: Vector3D) -> Vector3D {
    return newVector3DWith (x' + other.x) and (y' + other.y) and (z' + other.z)
  }

  method minus (other: Vector3D) -> Vector3D {
    return newVector3DWith (x' - other.x) and (y' - other.y) and (z' - other.z)
  }

  method dot (other: Vector3D) -> Number {
    return (x' * other.x) + (y' * other.y) + (z' * other.z)
  }

  method squaredMagnitude -> Number {
    return self.dot(self)
  }

  method magnitude -> Number {
    return squaredMagnitude.sqrt
  }

  method times (amount: Number) -> Vector3D {
    return newVector3DWith (x' * amount) and (y' * amount) and (z' * amount)
  }
}

class newSym(customName': String) -> Symbol {
  method customName -> String {
    customName'
  }
}

def redSym: Symbol = newSym("red")
def blackSym: Symbol = blackSym

class newNodeWith (key': CallSign) and (value': Unknown) -> Node {
  method key -> CallSign { key' }
  var value: Unknown := value'
  var left: Node := Done
  var right: Node := Done
  var parent: Node := Done
  var color: Symbol := redSym

  method successor -> Node {
    var x: Node := self
    x.right.notNil.ifTrue { return treeMinimum(x.right) }

    var y: Node := x.parent
    { y.notNil.and { x == y.right }}. whileTrue {
      x := y
      y := y.parent
    }

    return y
  }
}

class newEntryWithKey (key': CallSign) andValue (value': Unknown) -> EntryWithKeyValue {
  method key -> CallSign { key' }
  method value -> Unknown { value' }
}

class newInsertResult (isNewEntry': Boolean) node (newNode': Node) value (oldValue': Unknown) -> InsertResult {
  method isNewEntry -> Boolean { isNewEntry' }
  method newNode -> Node { newNode' }
  method oldValue -> Unknown { oldValue' }
}


class newRedBlackTree -> RedBlackTree {
  var root: Node := Done

  method at (key: CallSign) put (value: Unknown) -> Unknown {
    var insertionResult: InsertResult := treeAt (key) insert (value)
    insertionResult.isNewEntry.ifFalse {
      return insertionResult.oldValue
    }

    var x: Node := insertionResult.newNode

    { (x != root). and { x.parent.color == redSym }}. whileTrue {
      (x.parent == x.parent.parent.left). ifTrue {
        var y: Node := x.parent.parent.right
        (y.notNil.and { y.color == redSym }).ifTrue {
          // Case 1
          x.parent.color (blackSym)
          y.color (blackSym)
          x.parent.parent.color (redSym)
          x := x.parent.parent
        } ifFalse {
          (x == x.parent.right). ifTrue {
            // Case 2
            x := x.parent
            leftRotate (x)
          }

          // Case 3
          x.parent.color (blackSym)
          x.parent.parent.color (redSym)
          rightRotate(x.parent.parent)
        }
      } ifFalse {
        // Same as "then" clause with "right" and "left" exchanged
        var y: Node := x.parent.parent.left
        (y.notNil.and { y.color == redSym }).ifTrue {
          // Case 1
          x.parent.color(blackSym)
          y.color(blackSym)
          x.parent.parent.color(redSym)
          x := x.parent.parent
        } ifFalse {
          (x == x.parent.left). ifTrue {
            // Case 2
            x := x.parent
            rightRotate(x)
          }

          // Case 3
          x.parent.color(blackSym)
          x.parent.parent.color(redSym)
          leftRotate(x.parent.parent)
        }
      }
    }

    root.color(blackSym)
    return Done
  }

  method remove (key: CallSign) -> Unknown {
    var x: Node
    var y: Node
    var z: Node
    var xParent: Node

    z := findNode(key)
    z.isNil.ifTrue { return Done }

    // Y is the node to be unlinked from the tree.
    (z.left.isNil.or { z.right.isNil }). ifTrue {
      y := z
    } ifFalse {
      y := z.successor
    }

    // Y is guaranteed to be non-null at this point.
    y.left.notNil.ifTrue {
      x := y.left
    } ifFalse {
      x := y.right
    }

    // X is the child of y which might potentially replace y in the tree.
    //   X might be null at this point.
    x.notNil.ifTrue {
      x.parent (y.parent)
      xParent := x.parent
    } ifFalse {
      xParent := y.parent
    }

    y.parent.isNil.ifTrue {
      root := x
    } ifFalse {
      (y == y.parent.left). ifTrue {
        y.parent.left (x)
      } ifFalse {
        y.parent.right (x)
      }
    }

    (y != z). ifTrue {
      (y.color == blackSym). ifTrue {
        remove (x) andFixup (xParent)
      }

      y.parent(z.parent)
      y.color(z.color)
      y.left(z.left)
      y.right(z.right)

      z.left.notNil.ifTrue { z.left.parent(y) }
      x.right.notNil.ifTrue { z.right.parent(y) }

      z.parent.notNil.ifTrue {
        (z.parent.left == z). ifTrue {
          z.parent.left(y)
        } ifFalse {
          z.parent.right(y)
        }
      } ifFalse {
        root := y
      }
    } ifFalse {
      (y.color == blackSym).ifTrue {
        remove (x) andFixup (xParent)
      }
    }

    return z.value
  }

  method at (key: CallSign) -> Unknown {
    var node: Node := findNode (key)
    node.isNil.ifTrue { return Done }
    return node.value
  }

  method forEach (block: Invokable) -> RedBlackTree {
    root.isNil.ifTrue { return self }
    var current: Node := treeMinimum(root)
    { current.notNil }. whileTrue {
      block.value (newEntryWithKey(current.key) andValue (current.value))
      current := current.successor
    }
  }

  method findNode (key: CallSign) -> Node {
    var current: Node := root
    { current.notNil }.whileTrue {
      var comparisonResult: Number := key.compareTo (current.key)
      (comparisonResult == 0). ifTrue { return current }
      (comparisonResult < 0).ifTrue {
        current := current.left
      } ifFalse {
        current := current.right
      }
    }
    return Done
  }

  method treeAt (key: CallSign) insert (value: Unknown) -> InsertResult {
    var y: Node := Done
    var x: Node := root

    { x.notNil }. whileTrue {
      y := x
      var comparisonResult: Number := key.compareTo(x.key)
      (comparisonResult < 0.asInteger). ifTrue {
        x := x.left
      } ifFalse {
        (comparisonResult > 0.asInteger). ifTrue {
          x := x.right
        } ifFalse {
          var oldValue: Unknown := x.value
          x.value(value)
          return newInsertResult (false) node (Done) value (oldValue)
        }
      }
    }

    var z: Node := newNodeWith (key) and (value)
    z.parent(y)
    y.isNil.ifTrue {
      root := z
    } ifFalse {
      (key.compareTo(y.key) < 0.asInteger). ifTrue {
        y.left(z)
      } ifFalse {
        y.right(z)
      }
    }

    return newInsertResult (true) node (z) value (Done)
  }

  method leftRotate (x: Node) -> Node {
    var y: Node := x.right

    // Turn y's left subtree into x's right subtree
    x.right(y.left)
    y.left.notNil.ifTrue {
      y.left.parent(x)
    }

    // Link x's parent to y
    y.parent(x.parent)
    x.parent.isNil.ifTrue {
      root := y
    } ifFalse {
      (x == x.parent.left).ifTrue {
        x.parent.left(y)
      } ifFalse {
        x.parent.right(y)
      }
    }

    // Put x on y's left
    y.left(x)
    x.parent(y)
    return y
  }

  method rightRotate (y: Node) -> Node {
    var x: Node := y.left

    // Turn x's right subtree into y's left subtree
    y.left(x.right)
    x.right.notNil.ifTrue { x.right.parent(y) }

    // Link y's parent to x
    x.parent(y.parent)
    y.parent.isNil.ifTrue {
      root := x
    } ifFalse {
      (y == y.parent.left). ifTrue {
        y.parent.left(x)
      } ifFalse {
        y.parent.right(x)
      }
    }

    x.right(y)
    y.parent(x)
    return x
  }

  method remove (anX: Node) andFixup (anXParent: Node) -> Done {
    var x: Node := anX
    var xParent: Node := anXParent

    (x != root). and { x.isNil.or { x.color == blackSym }}. whileTrue {
      (x == xParent.left).ifTrue {
        // Note: the text points out that w cannot be null. The reason is not obvious from
        //   simply looking at the code; it comes about from the properties of the red-black
        //   tree.
        var w: Node := xParent.right
        (w.color == redSym). ifTrue {
          // Case 1
          w.color(blackSym)
          xParent.color(redSym)
          leftRotate(xParent)
          w := xParent.right
        }

        ((w.left.isNil.or { w.left.color == blackSym }). and { w.right.isNil.or { w.right.color == blackSym }}). ifTrue {
          // Case 2
          w.color (redSym)
          x := xParent
          xParent := x.parent
        } ifFalse {
          (w.right.isNil.or { w.right.color == blackSym }). ifTrue {
            // Case 3
            w.left.color(blackSym)
            w.color(redSym)
            rightRotate(w)
            w := xParent.right
          }

          // Case 4
          w.color(xParent.color)
          xParent.color(blackSym)
          w.right.notNil.ifTrue { w.right.color(blackSym) }

          leftRotate(xParent)
          x := root
          xParent := x.parent
        }

      } ifFalse {

        // Same as "then" clause with "right" and "left" exchanged
        var w: Node := xParent.left
        (w.color == redSym). ifTrue {
          // Case 1
          w.color(blackSym)
          xParent.color(redSym)
          rightRotate(xParent)
          w := xParent.left
        }

        ((w.right.isNil.or { w.right.color == blackSym }). and { (w.left.isNil.or { w.left.color == blackSym })}). ifTrue {
          // Case 2
          w.color(redSym)
          x := xParent
          xParent := x.parent
        } ifFalse {
          w.left.isNil.or { w.left.color == blackSym }. ifTrue {
            // Case 3
            w.right.color(blackSym)
            w.color(redSym)
            leftRotate(w)
            w := xParent.left
          }

          // Case 4
          w.color(xParent.color)
          xParent.color(blackSym)
          w.left.notNil.ifTrue { w.left.color(blackSym) }

          rightRotate(xParent)
          x := root
          xParent := x.parent
        }
      }
    }

    x.notNil.ifTrue { x.color(blackSym) }
    Done
  }
}

class newCallSign (val: Number) -> CallSign {
  method value -> Number { val }

  method compareTo (other: CallSign) -> Number {
    return (val == other.value).ifTrue {
      0.asInteger
    } ifFalse {
      (val < other.value).ifTrue {
        -1.asInteger
      } ifFalse {
        1.asInteger
      }
    }
  }
}

class newCollisionWith (aircraftA': CallSign) and (aircraftB': CallSign) pos (position': Vector3D) -> Collision {
  method aircraftA -> CallSign { aircraftA' }
  method aircraftB -> CallSign { aircraftB' }
  method position -> Vector3D { position' }
}

class newCollisionDetector -> CollisionDectector {
  def state: RedBlackTree = newRedBlackTree

  method handleNewFrame (frame: Vector) -> Vector {
    var motions: Vector := core.newVector
    var seen: RedBlackTree := newRedBlackTree

    frame.forEach { aircraft: Aircraft ->
      var oldPosition: Vector3D := state.at (aircraft.callsign) put (aircraft.position)
      var newPosition: Vector3D := aircraft.position
      seen.at (aircraft.callsign) put (true)

      oldPosition.isNil.ifTrue {
        // Treat newly introduced aircraft as if they were stationary
        oldPosition := newPosition
      }

      motions.append (newMotion (aircraft.callsign) old (oldPosition) new (newPosition))
    }

    // Remove aircraft that are no longer present
    var toRemove: Vector := core.newVector
    state.forEach { e: EntryWithKeyValue ->
      seen.at (e.key).ifFalse {
        toRemove.append(e.key)
      }
    }

    toRemove.forEach { e: CallSign -> state.remove(e) }

    var allReduced: Vector := reduceCollisionSet (motions)
    var collisions: Vector := core.newVector
    allReduced.forEach { reduced: Vector ->
      1.asInteger.to (reduced.size) do { i: Number ->
        var motion1: Motion := reduced.at (i)
        (i + 1.asInteger). to (reduced.size) do { j: Number ->
          var motion2: Motion := reduced.at (j)
          var collision: Vector3D := motion1.findIntersection(motion2)
          collision.notNil.ifTrue {
            collisions.append (newCollisionWith (motion1.callsign) and (motion2.callsign) pos (collision))
          }
        }
      }
    }

    return collisions
  }

  method isInVoxel (voxel: Vector2D) motion (motion: Motion) -> Boolean {

    ((voxel.x > MaxX). or {
     (voxel.x < MinX). or {
     (voxel.y > MaxY). or {
     (voxel.y < MinY) }}}).ifTrue { return false }

    var init: Vector3D := motion.posOne
    var fin: Vector3D :=  motion.posTwo

    var v_s: Number := GoodVoxelSize
    var r: Number := ProximityRadius / 2

    var v_x: Number := voxel.x
    var x0: Number :=  init.x
    var xv: Number :=  fin.x - init.x

    var v_y: Number := voxel.y
    var y0: Number :=  init.y
    var yv: Number :=  fin.y - init.y

    var low_x: Number := (v_x - r - x0) / xv
    var high_x: Number := (v_x + v_s + r - x0) / xv

    (xv < 0). ifTrue {
      var tmp: Number := low_x
      low_x := high_x
      high_x := tmp
    }

    var low_y: Number := (v_y - r - y0) / yv
    var high_y: Number := (v_y + v_s + r - y0) / yv

    (yv < 0).ifTrue {
      var tmp: Number := low_y
      low_y := high_y
      high_y := tmp
    }

    return ((((xv == 0.0). and {(v_x <= (x0 + r)). and {(x0 - r) <= (v_x + v_s)}}). or { // no motion in x
             ((low_x <= 1.0). and {1.0 <= high_x}). or {
             ((low_x <= 0.0). and {0.0 <= high_x}). or {
             ((0.0 <= low_x). and {high_x <= 1.0})}}}). and {

             ((yv == 0.0). and {(v_y <= (y0 + r)). and {(y0 - r) <= (v_y + v_s)}}). or { // no motion in y
               ((low_y <= 1.0). and {1.0 <= high_y}). or {
               ((low_y <= 0.0). and {0.0 <= high_y}). or {
               ((0.0   <= low_y). and {high_y <= 1.0})}}}}). and {

               (xv == 0.0). or {
               (yv == 0.0). or { // no motion in x or y or both *)
               ((low_y <= high_x). and {high_x <= high_y}). or {
               ((low_y <= low_x).  and {low_x <= high_y}). or {
               ((low_x <= low_y).  and {high_y <= high_x}) }}}}}
  }

  method put (motion: Motion) and (voxel: Vector2D) into (voxelMap: RedBlackTree) -> Done {
    var array: Vector := voxelMap.at(voxel)
    array.isNil.ifTrue {
      array := core.newVector
      voxelMap.at (voxel) put (array)
    }
    array.append (motion)
    Done
  }

  method recurse (voxelMap: RedBlackTree) seen (seen: RedBlackTree) voxel (nextVoxel: Vector2D) motion (motion: Motion) -> Done {
    isInVoxel(nextVoxel)motion(motion). ifFalse { return self }
    (seen.at(nextVoxel)put(true) == true). ifTrue { return self }

    put(motion) and (nextVoxel) into (voxelMap)

    recurse (voxelMap) seen (seen) voxel (nextVoxel.minus (horizontal)) motion(motion)
    recurse (voxelMap) seen (seen) voxel (nextVoxel.plus  (horizontal)) motion(motion)
    recurse (voxelMap) seen (seen) voxel (nextVoxel.minus (  vertical)) motion(motion)
    recurse (voxelMap) seen (seen) voxel (nextVoxel.plus  (  vertical)) motion(motion)

    recurse (voxelMap) seen (seen) voxel ((nextVoxel.minus (horizontal)). minus (vertical)) motion (motion)
    recurse (voxelMap) seen (seen) voxel ((nextVoxel.minus (horizontal)). plus  (vertical)) motion (motion)
    recurse (voxelMap) seen (seen) voxel ((nextVoxel.plus  (horizontal)). minus (vertical)) motion (motion)
    recurse (voxelMap) seen (seen) voxel ((nextVoxel.plus  (horizontal)). plus  (vertical)) motion (motion)
    Done
  }

  method reduceCollisionSet (motions: Vector) -> Vector {
    var voxelMap: RedBlackTree := newRedBlackTree
    motions.forEach { motion: Motion -> draw (motion) on (voxelMap) }

    var result: Vector := core.newVector
    voxelMap.forEach { e: EntryWithKeyValue ->
      (e.value.size > 1.asInteger). ifTrue { result.append(e.value) }
    }
    return result
  }

  method voxelHash (position: Vector3D) -> Vector2D {
    var xDiv: Number := (position.x / GoodVoxelSize).asInteger
    var yDiv: Number := (position.y / GoodVoxelSize).asInteger

    var x: Number := GoodVoxelSize * xDiv
    var y: Number := GoodVoxelSize * yDiv

    (position.x < 0.asInteger).ifTrue { x := x - GoodVoxelSize }
    (position.y < 0.asInteger).ifTrue { y := y - GoodVoxelSize }

    return newVector2DWith(x)and(y)
  }

  method draw (motion: Motion) on (voxelMap: RedBlackTree) -> Done {
    var seen: RedBlackTree := newRedBlackTree
    recurse (voxelMap) seen (seen) voxel (voxelHash(motion.posOne)) motion (motion)
    Done
  }
}

class newMotion (callsign': CallSign) old (posOne': Vector3D) new (posTwo': Vector3D) -> Motion {
  method callsign -> CallSign { callsign' }
  method posOne -> Vector3D { posOne' }
  method posTwo -> Vector3D { posTwo' }

  method delta -> Vector3D {
    return posTwo'.minus(posOne')
  }

  method findIntersection (other: Motion) -> Vector3D {
    var init1: Vector3D := posOne'
    var init2: Vector3D := other.posOne
    var vec1: Vector3D := delta
    var vec2: Vector3D := other.delta
    var radius: Number := ProximityRadius

    // this test is not geometrical 3-d intersection test, it takes the fact that the aircraft move
    //   into account ; so it is more like a 4d test
    //   (it assumes that both of the aircraft have a constant speed over the tested interval)
    //
    //   we thus have two points, each of them moving on its line segment at constant speed ; we are looking
    //   for times when the distance between these two points is smaller than r
    //
    //   vec1 is vector of aircraft 1
    //   vec2 is vector of aircraft 2
    //
    //   a = (V2 - V1)returnT * (V2 - V1)
    var a: Number := vec2.minus(vec1).squaredMagnitude

    (a != 0).ifTrue {
      // we are first looking for instances of time when the planes are exactly r from each other
      //   at least one plane is moving ; if the planes are moving in parallel, they do not have constant speed
      //
      //   if the planes are moving in parallel, then
      //     if the faster starts behind the slower, we can have 2, 1, or 0 solutions
      //     if the faster plane starts in front of the slower, we can have 0 or 1 solutions
      //
      //   if the planes are not moving in parallel, then
      //
      //   point P1 = I1 + vV1
      //   point P2 = I2 + vV2
      //     - looking for v, such that dist(P1,P2) = || P1 - P2 || = r
      //
      //   it follows that || P1 - P2 || = sqrt( < P1-P2, P1-P2 > )
      //     0 = -rreturn2 + < P1 - P2, P1 - P2 >
      //    from properties of dot product
      //     0 = -rreturn2 + <I1-I2,I1-I2> + v * 2<I1-I2, V1-V2> + vreturn2 *<V1-V2,V1-V2>
      //     so we calculate a, b, c - and solve the quadratic equation
      //     0 = c + bv + avreturn2
      //
      //  b = 2 * <I1-I2, V1-V2>
      var b: Number := 2 * init1.minus(init2).dot(vec1.minus(vec2))

      // c = -rreturn2 + (I2 - I1)returnT * (I2 - I1)
      var c: Number := ((0 - radius) * radius) + init2.minus(init1).squaredMagnitude

      var discr: Number := (b * b) - (4 * a * c)
      (discr < 0).ifTrue { return Done }

      var v1: Number := ((0 - b) - discr.sqrt) / (2 * a)
      var v2: Number := ((0 - b) + discr.sqrt) / (2 * a)


      ( (v1 <= v2).and {(((v1  <= 1). and { 1 <= v2}).or {
                         ((v1  <= 0). and { 0 <= v2}).or {
                         (( 0 <= v1). and {v2 <= 1 })}})} ). ifTrue {

        // Pick a good "time" at which to report the collision
        var v: Number
        (v1 <= 0). ifTrue {
            // The collision started before this frame. Report it at the start of the frame
            v := 0
        } ifFalse {
            // The collision started during this frame. Report it at that moment
            v := v1
        }

        var result1: Vector3D := init1.plus(vec1.times(v))
        var result2: Vector3D := init2.plus(vec2.times(v))

        var result: Vector3D := result1.plus(result2).times(0.5)

        ((result.x >= MinX).and {
         (result.x <= MaxX).and {
         (result.y >= MinY).and {
         (result.y <= MaxY).and {
         (result.z >= MinZ).and {
          result.z <= MaxZ }}}}}).ifTrue {
          return result
        }
      }

      return Done
    }

    // the planes have the same speeds and are moving in parallel (or they are not moving at all)
    //   they  thus have the same distance all the time ; we calculate it from the initial point
    //
    //   dist = || i2 - i1 || = sqrt(  ( i2 - i1 )returnT * ( i2 - i1 ) ) *)
    var dist: Number := init2.minus(init1).magnitude
    (dist <= radius). ifTrue {
      return init1.plus(init2).times(0.5)
    }

    return Done
  }
}

class newAircraft (callsign': CallSign) pos (position': Vector3D) -> Aircraft {
  method callsign -> CallSign { callsign' }
  method position -> Vector3D { position' }
}

class newSimulator(numAircrafts': Number) -> Simulator {
  method numAircrafts -> Number { numAircrafts' }
  def aircrafts: Vector = core.newVector

  0.asInteger.to (numAircrafts' - 1.asInteger) do { i: Number ->
    aircrafts.append(newCallSign(i))
  }

  method simulate (time: Number) -> Vector {
    var frame: Vector := core.newVector

    0.asInteger.to (aircrafts.size - 2.asInteger) by (2.asInteger) do { i: Number ->
      frame.append (newAircraft (aircrafts.at(i + 1.asInteger))
                              pos (newVector3DWith( time                     )
                                              and ( (time.cos * 2) + (i * 3) )
                                              and ( 10                       )))
      frame.append (newAircraft (aircrafts.at(i + 2.asInteger))
                              pos (newVector3DWith( time                     )
                                               and( (time.sin * 2) + (i * 3) )
                                               and( 10                       )))
    }
    return frame
  }
}

method treeMinimum (x: Node) -> Node {
  var current: Node := x
  { current.left.notNil }. whileTrue {
    current := current.left
  }
  return current
}

method newInstance -> Benchmark { newCD }
