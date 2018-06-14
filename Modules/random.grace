var seed: Number := 74755.asInteger

method setSeed(s: Number) -> Done {
  seed := s
}

method resetSeed -> Done {
  seed := 74755.asInteger
}

method next -> Number {
  seed.isNil.ifTrue {  }
  seed := ((seed * 1309.asInteger) + 13849.asInteger) & 65535.asInteger
  seed
}

method random -> Number {
  (next + 0) / 65535.0
}

method randomBetween(x: Number)and(y: Number) -> Number {
  (x + ((y + 1) - x) * random).asInteger
}


// Robert Jenkins 32 bit integer hash function.
class Jenkins(seed': Number) -> Unknown {
  setSeed(seed')
    
  // Original version, with complete set of conversions.
  method next -> Number {
    seed := ((seed      + 2127912214.asInteger)       + (seed.as32BitUnsignedValue.bitLeftShift (12.asInteger)).as32BitSignedValue).as32BitSignedValue
    seed := ((seed.bitXor(3345072700.asInteger)).bitXor((seed.as32BitUnsignedValue.bitRightShift(19.asInteger))).as32BitSignedValue)
    seed := ((seed      +  374761393.asInteger)       + (seed.as32BitUnsignedValue.bitLeftShift  (5.asInteger)).as32BitSignedValue).as32BitSignedValue
    seed := ((seed      + 3550635116.asInteger ).bitXor((seed.as32BitUnsignedValue.bitLeftShift  (9.asInteger)).as32BitSignedValue).as32BitSignedValue)
    seed := ((seed      + 4251993797.asInteger)       + (seed.as32BitUnsignedValue.bitLeftShift  (3.asInteger)).as32BitSignedValue).as32BitSignedValue
    seed := ((seed.bitXor(3042594569.asInteger)).bitXor((seed.as32BitUnsignedValue.bitRightShift(16.asInteger))).as32BitSignedValue)
    seed
  }
}
