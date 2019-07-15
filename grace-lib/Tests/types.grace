method asString {"types.grace"}

type Foo = {
  x
  y
}

def aFoo = object {
  var x
  var y
}

method withNumberArg(x: Number) {}
method withStringArg(x: String) {}
method withBooleanArg(x: Boolean) {}
method withFooArg(x: Foo) {}

method testTypedArgPasses {
  withNumberArg(1)
  withStringArg("hello")
  withBooleanArg(true)
  withFooArg(aFoo)
  "testTypedArgPasses passed"
}

method testTypedArgFailures {
  { 
    withNumberArg(true);
    error("testTypedArgFailures failed, didn't produce error for Boolean (expected Number) ")
  }.on (platform.kernel.TypeError) do {} 

  { 
    withNumberArg("hello");
    error("testTypedArgFailures failed, didn't produce error for String (expected Number) ")
  }.on (platform.kernel.TypeError) do {} 

  { 
    withNumberArg(aFoo);
    error("testTypedArgFailures failed, didn't produce error for Foo (expected Number) ")
  }.on (platform.kernel.TypeError) do {} 

  { 
    withStringArg(true);
    error("testTypedArgFailures failed, didn't produce error for Boolean (expected String) ")
  }.on (platform.kernel.TypeError) do {} 

  { 
    withStringArg(1);
    error("testTypedArgFailures failed, didn't produce error for Number (expected String) ")
  }.on (platform.kernel.TypeError) do {} 

  { 
    withStringArg(aFoo);
    error("testTypedArgFailures failed, didn't produce error for Foo (expected String) ")
  }.on (platform.kernel.TypeError) do {} 

  { 
    withBooleanArg(1);
    error("testTypedArgFailures failed, didn't produce error for Number (expected Boolean) ")
  }.on (platform.kernel.TypeError) do {} 

  { 
    withBooleanArg("hello");
    error("testTypedArgFailures failed, didn't produce error for String (expected Boolean) ")
  }.on (platform.kernel.TypeError) do {} 

  { 
    withBooleanArg(aFoo);
    error("testTypedArgFailures failed, didn't produce error for Foo (expected Boolean) ")
  }.on (platform.kernel.TypeError) do {} 

  { 
    withFooArg(1);
    error("testTypedArgFailures failed, didn't produce error for Number (expected Foo) ")
  }.on (platform.kernel.TypeError) do {} 

  { 
    withFooArg("hello");
    error("testTypedArgFailures failed, didn't produce error for String (expected Foo) ")
  }.on (platform.kernel.TypeError) do {} 

  { 
    withFooArg(true);
    error("testTypedArgFailures failed, didn't produce error for Boolean (expected Foo) ")
  }.on (platform.kernel.TypeError) do {} 

  "testTypedArgFailures passed"
}

method returnArgAsNumber(x) -> Number { x }
method returnArgAsString(x) -> String { x }
method returnArgAsBoolean(x) -> Boolean { x }
method returnArgAsFoo(x) -> Foo { x }

method testTypedReturnPasses {
  returnArgAsNumber(1)
  returnArgAsString("hello")
  returnArgAsBoolean(true)
  returnArgAsFoo(aFoo)
  "testTypedReturnPasses passed"
}

method testTypedReturnFailures {
  { 
    returnArgAsNumber(true);
    error("testTypedReturnFailures failed, didn't produce error for Boolean (expected Number) ")
  }.on (platform.kernel.TypeError) do {} 

  { 
    returnArgAsNumber("hello");
    error("testTypedReturnFailures failed, didn't produce error for String (expected Number) ")
  }.on (platform.kernel.TypeError) do {} 

  { 
    returnArgAsNumber(aFoo);
    error("testTypedReturnFailures failed, didn't produce error for Foo (expected Number) ")
  }.on (platform.kernel.TypeError) do {} 

  { 
    returnArgAsString(true);
    error("testTypedReturnFailures failed, didn't produce error for Boolean (expected String) ")
  }.on (platform.kernel.TypeError) do {} 

  { 
    returnArgAsString(1);
    error("testTypedReturnFailures failed, didn't produce error for Number (expected String) ")
  }.on (platform.kernel.TypeError) do {} 

  { 
    returnArgAsString(aFoo);
    error("testTypedReturnFailures failed, didn't produce error for Foo (expected String) ")
  }.on (platform.kernel.TypeError) do {} 

  { 
    returnArgAsBoolean(1);
    error("testTypedReturnFailures failed, didn't produce error for Number (expected Boolean) ")
  }.on (platform.kernel.TypeError) do {} 

  { 
    returnArgAsBoolean("hello");
    error("testTypedReturnFailures failed, didn't produce error for String (expected Boolean) ")
  }.on (platform.kernel.TypeError) do {} 

  { 
    returnArgAsBoolean(aFoo);
    error("testTypedReturnFailures failed, didn't produce error for Foo (expected Boolean) ")
  }.on (platform.kernel.TypeError) do {} 

  { 
    returnArgAsFoo(1);
    error("testTypedReturnFailures failed, didn't produce error for Number (expected Foo) ")
  }.on (platform.kernel.TypeError) do {} 

  { 
    returnArgAsFoo("hello");
    error("testTypedReturnFailures failed, didn't produce error for String (expected Foo) ")
  }.on (platform.kernel.TypeError) do {} 

  { 
    returnArgAsFoo(true);
    error("testTypedReturnFailures failed, didn't produce error for Boolean (expected Foo) ")
  }.on (platform.kernel.TypeError) do {} 

  "testTypedReturnFailures passed"
}

method testTypedLocalAssignmentPasses {
  var aNumberField: Number := 1
  var aStringField: String := "hello"
  var aBooleanField: Boolean := true
  var aFooField: Foo := aFoo
  "testTypedLocalAssignmentPasses passed"
}

method testTypedLocalAssignmentFailures {
  {
    var x: Number := "hello"
  }.on (platform.kernel.TypeError) do {}

  {
    var x: Number := true
    error("testTypedLocalAssignmentFailures failed, didn't produce error for Boolean (expected Number) ")
  }.on (platform.kernel.TypeError) do {}

  {
    var x: Number := aFoo
    error("testTypedLocalAssignmentFailures failed, didn't produce error for Foo (expected Number) ")
  }.on (platform.kernel.TypeError) do {}

  {
    var x: String := 1
    error("testTypedLocalAssignmentFailures failed, didn't produce error for Number (expected String) ")
  }.on (platform.kernel.TypeError) do {}

  {
    var x: String := true
    error("testTypedLocalAssignmentFailures failed, didn't produce error for Boolean (expected String) ")
  }.on (platform.kernel.TypeError) do {}

  {
    var x: String := aFoo
    error("testTypedLocalAssignmentFailures failed, didn't produce error for Foo (expected String) ")
  }.on (platform.kernel.TypeError) do {}

  {
    var x: Boolean := 1
    error("testTypedLocalAssignmentFailures failed, didn't produce error for Number (expected Boolean) ")
  }.on (platform.kernel.TypeError) do {}

  {
    var x: Boolean := "hello"
    error("testTypedLocalAssignmentFailures failed, didn't produce error for String (expected Boolean) ")
  }.on (platform.kernel.TypeError) do {}

  {
    var x: Boolean := aFoo
    error("testTypedLocalAssignmentFailures failed, didn't produce error for Foo (expected Boolean) ")
  }.on (platform.kernel.TypeError) do {}
  
  {
    var x: Foo := 1
    error("testTypedLocalAssignmentFailures failed, didn't produce error for Number (expected Foo) ")
  }.on (platform.kernel.TypeError) do {}

  {
    var x: Foo := "hello"
    error("testTypedLocalAssignmentFailures failed, didn't produce error for String (expected Foo) ")
  }.on (platform.kernel.TypeError) do {}

  {
    var x: Foo := true
    error("testTypedLocalAssignmentFailures failed, didn't produce error for Boolean (expected Foo) ")
  }.on (platform.kernel.TypeError) do {}

  "testTypedLocalAssignmentFailures passed"
}

method testTypedFieldAssignmentPasses {
  object {
    var aNumberField: Number := 1
    var aStringField: String := "hello"
    var aBooleanField: Boolean := true
    var aFooField: Foo := aFoo
  }

  "testTypedFieldAssignmentPasses passed"
}

method testTypedFieldAssignmentFailures {
  {
    object {
      var x: Number := "hello"
    }
    error("testTypedFieldAssignmentFailures failed, didn't produce error for String (expected Number) ")
  }.on (platform.kernel.TypeError) do {}

  {
    object {
      var x: Number := true
    }
    error("testTypedFieldAssignmentFailures failed, didn't produce error for Boolean (expected Number) ")
  }.on (platform.kernel.TypeError) do {}

  {
    object {
      var x: Number := aFoo
    }
    error("testTypedFieldAssignmentFailures failed, didn't produce error for Foo (expected Number) ")
  }.on (platform.kernel.TypeError) do {}

  {
    object {
      var x: String := 1
    }
    error("testTypedFieldAssignmentFailures failed, didn't produce error for Number (expected String) ")
  }.on (platform.kernel.TypeError) do {}

  {
    object {
      var x: String := true
    }
    error("testTypedFieldAssignmentFailures failed, didn't produce error for Boolean (expected String) ")
  }.on (platform.kernel.TypeError) do {}

  {
    object {
      var x: String := aFoo
    }
    error("testTypedFieldAssignmentFailures failed, didn't produce error for Foo (expected String) ")
  }.on (platform.kernel.TypeError) do {}

  {
    object {
      var x: Boolean := 1
    }
    error("testTypedFieldAssignmentFailures failed, didn't produce error for Number (expected Boolean) ")
  }.on (platform.kernel.TypeError) do {}

  {
    object {
      var x: Boolean := "hello"
    }
    error("testTypedFieldAssignmentFailures failed, didn't produce error for String (expected Boolean) ")
  }.on (platform.kernel.TypeError) do {}

  {
    object {
      var x: Boolean := aFoo
    }
    error("testTypedFieldAssignmentFailures failed, didn't produce error for Foo (expected Boolean) ")
  }.on (platform.kernel.TypeError) do {}
  
  {
    object {
      var x: Foo := 1
    }
    error("testTypedFieldAssignmentFailures failed, didn't produce error for Number (expected Foo) ")
  }.on (platform.kernel.TypeError) do {}

  {
    object {
      var x: Foo := "hello"
    }
    error("testTypedFieldAssignmentFailures failed, didn't produce error for String (expected Foo) ")
  }.on (platform.kernel.TypeError) do {}

  {
    object {
      var x: Foo := true
    }
    error("testTypedFieldAssignmentFailures failed, didn't produce error for Boolean (expected Foo) ")
  }.on (platform.kernel.TypeError) do {}

  "testTypedFieldAssignmentFailures passed"
}
