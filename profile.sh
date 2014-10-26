#!/bin/bash
BASE_DIR=`pwd`
if [ -z "$GRAAL_HOME" ]; then
  GRAAL_HOME='/home/smarr/Projects/SOM/graal'
  if [ ! -d "$GRAAL_HOME" ]
  then
    GRAAL_HOME='/Users/smarr/Projects/PostDoc/Truffle/graal'
  fi
fi

# -G:Dump=TruffleTree
if [ -z "$GRAAL_FLAGS" ]; then
  GRAAL_FLAGS='-G:-TraceTruffleInlining -G:-TraceTruffleCompilation -G:+TruffleSplittingNew -G:+TruffleCompilationExceptionsAreFatal'
fi

if [ ! -z "$DBG" ]; then
  GRAAL_DEBUG_SWITCH='-d'
fi

#GRAAL_FLAGS="$GRAAL_FLAGS -G:Dump=TruffleTree "
#GRAAL_FLAGS="$GRAAL_FLAGS -G:+TraceTruffleCompilation "
#GRAAL_FLAGS="$GRAAL_FLAGS -XX:+UnlockDiagnosticVMOptions -XX:CompileCommand=print,*::callRoot "

#GRAAL_FLAGS="$GRAAL_FLAGS -G:TruffleGraphMaxNodes=1500000 -G:TruffleInliningMaxCallerSize=10000 -G:TruffleInliningMaxCalleeSize=10000 -G:TruffleInliningTrivialSize=10000 -G:TruffleSplittingMaxCalleeSize=100000"


# -esa -ea
$GRAAL_HOME/mxtool/mx $GRAAL_DEBUG_SWITCH --vm graal vm -XX:+BootstrapGraal -XX:GraalCounterSize=30000 "-G:BenchmarkDynamicCounters=out,warmup finished,finished" -G:+ProfileCompiledMethods $GRAAL_FLAGS $GF \
   -Xbootclasspath/a:build/classes:libs/truffle.jar \
   som.vm.Universe "$@"
