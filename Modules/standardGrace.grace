type Done = interface {}

type Number = interface {
  + (other)
  - (other)
  / (other)
  * (other)
  asString
}

type String = interface {
  ++ (other)
}

type Boolean = interface {
  and (other)
  or (other)
}

type List = interface {
  at(ix)
  at(ix)put(value)
  size
}

type Invokable = interface {
  apply
}

method print(x: Unknown) -> Done {
  x.println
}

method if (cond: Boolean) then (blk: Invokable) -> Done {
  cond.ifTrue {
    blk.apply
  }
}
