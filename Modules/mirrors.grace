method methodNamesForObject(obj: Unknown) -> List {
  def methodMirrors: List = platform.mirrors.ClassMirror.reflecting(obj).methods
  def methodNames: List = platform.kernel.Array.new(methodMirrors.size)

  var i: Number := 1
  methodMirrors.do { methodMirror: Unknown ->
    methodNames.at (i.asInteger) put (methodMirror.name)
    i := i + 1
  }

  methodNames
}

method invoke (methodName: Unknown) on (obj: Unknown) -> Unknown {
  def objMirror: Unknown = platform.mirrors.ObjectMirror.reflecting(obj)
  objMirror.perform(methodName.asSymbol)
}

method invoke (methodName: Unknown) on (obj: Unknown) withArguments (args: Unknown) -> Unknown {
  def objMirror: Unknown = platform.mirrors.ObjectMirror.reflecting(obj)
  objMirror.perform(methodName.asSymbol)withArguments(args)
}
