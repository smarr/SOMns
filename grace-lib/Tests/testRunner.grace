import "io" as io
import "mirrors" as mirrors

method getSuiteByName(name: String) -> Unknown {
  io.importModuleByName(name)
}

method findTestsInSuite(module: Unknown) -> List {
  def names: List = mirrors.methodNamesForObject(module)

  var n: Number := 0
  names.do { name: String ->
    (name.beginsWith("test")) .ifTrue {
      n := n + 1
    }
  }

  def testNames: List = platform.kernel.Array.new(n.asInteger)
  var i: Number := 1
  names.do { name: String ->
    (name.beginsWith("test")) .ifTrue {
      testNames.at (i.asInteger) put(name)
      i := i + 1
    }
  }

  testNames
}

method runTests(moduleName: String) -> Done {
  var suite: Unknown := getSuiteByName(moduleName)
  var names: List := findTestsInSuite(suite)

  print("Running tests in " + suite.asString)
  names.do { name: String ->
    print("  " + mirrors.invoke (name) on (suite))
  }
  Done
}

method exe(args: Unknown) -> Done {
  def n: Number = args.size
  (n == 1).ifTrue {
    runTests("grace-lib/Tests/language.grace")
    runTests("grace-lib/Tests/modules.grace")
    runTests("grace-lib/Tests/types.grace")
  } ifFalse {
    runTests(args.at(2.asInteger))
  }
  Done
}

exe(args)
