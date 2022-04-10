#!/bin/bash
## Exit on first error
if [ "$1" != "update" ]
then
  # quit on first error
  set -e
fi

## Determine absolute path of script
pushd `dirname $0` > /dev/null
SCRIPT_PATH=`pwd`
popd > /dev/null

SOM_DIR=$SCRIPT_PATH/../..

if [ "$1" = "--coverage" ]
then
  COVERAGE=""
  echo Java coverage tracking not currently supported. JaCoCo crashes JDK17 at the moment.
  # COVERAGE="--java-coverage $SOM_DIR/jacoco.exec "
fi

## create folder for new results
mkdir -p $SCRIPT_PATH/results/

## extract expected results
tar --exclude='._*' -xf $SCRIPT_PATH/expected-results.tar.bz2 -C $SCRIPT_PATH/

NEEDS_UPDATE=false

function doDiff {
  EXPECTED=$1
  NEW=$2

  diff -r $EXPECTED $NEW
  if [ $? -ne 0 ]; then
    NEEDS_UPDATE=true
  fi
}

function runBenchmark {
  BENCH=$1
  HARNESS="$SOM_DIR/som -dm -Ddm.metrics=$SCRIPT_PATH/results/$BENCH \
    $COVERAGE --coverage $SOM_DIR/all.gcov \
    -G $SOM_DIR/core-lib/Benchmarks/Harness.ns"
  echo $HARNESS $@
  $HARNESS $@

  doDiff $SCRIPT_PATH/expected-results/$BENCH $SCRIPT_PATH/results/$BENCH
}

function runActorBenchmark {
  BENCH=$1
  HARNESS="$SOM_DIR/som -dm -t1 -Ddm.metrics=$SCRIPT_PATH/results/$BENCH \
    $COVERAGE --coverage $SOM_DIR/all.gcov \
    -G $SOM_DIR/core-lib/Benchmarks/AsyncHarness.ns Savina."
  echo ${HARNESS}$@
  ${HARNESS}$@

  doDiff $SCRIPT_PATH/expected-results/$BENCH $SCRIPT_PATH/results/$BENCH
}

function runTest {
  TEST=$1
  HARNESS="$SOM_DIR/som -dm -t1 -Ddm.metrics=$SCRIPT_PATH/results/$TEST \
    -G $SCRIPT_PATH/$TEST.ns"
  echo $HARNESS $@
  $HARNESS $@

  doDiff $SCRIPT_PATH/expected-results/$TEST $SCRIPT_PATH/results/$TEST
}

runTest ActorDynamicMetricsTests

runBenchmark LanguageFeatures.Fibonacci    1 0 2
runBenchmark LanguageFeatures.Dispatch     1 0 2
runBenchmark LanguageFeatures.Loop         1 0 2
runBenchmark LanguageFeatures.Recurse      1 0 2
runBenchmark LanguageFeatures.IntegerLoop  1 0 2
runBenchmark LanguageFeatures.FieldLoop    1 0 1

runBenchmark Sort.QuickSort  1 0 2
runBenchmark Sort.TreeSort   1 0 2
runBenchmark Sort.BubbleSort 1 0 2

runBenchmark Richards    1 0 1
runBenchmark DeltaBlue   1 0 2
runBenchmark Mandelbrot  1 0 3
runBenchmark NBody       1 0 3
runBenchmark Json        1 0 1
runBenchmark GraphSearch 1 0 1
# runBenchmark PageRank    1 0 1   # takes too long
runBenchmark Fannkuch    1 0 4
runBenchmark List        1 0 1
runBenchmark Bounce      1 0 1
runBenchmark Permute     1 0 5
runBenchmark Queens      1 0 5
runBenchmark Storage     1 0 2
runBenchmark Sieve       1 0 2
runBenchmark Towers      1 0 2

# Micro
runActorBenchmark PingPong                1 0 40000
runActorBenchmark Counting                1 0 300000
runActorBenchmark ForkJoinThroughput      1 0 3000:60
runActorBenchmark ForkJoinActorCreation   1 0 20000
runActorBenchmark ThreadRing              1 0 100:50000
runActorBenchmark Chameneos               1 0 100:20000
runActorBenchmark BigContention           1 0 500:120

# Concurrency
runActorBenchmark ConcurrentDictionary           1 0 20:600:20
runActorBenchmark ConcurrentSortedLinkedList     1 0 10:500:10:1
runActorBenchmark ProducerConsumerBoundedBuffer  1 0 40:5:5:10
runActorBenchmark Philosophers                   1 0 20:2000
runActorBenchmark SleepingBarber                 1 0 800:500:500:200
runActorBenchmark CigaretteSmokers               1 0 1000:200
runActorBenchmark LogisticsMapSeries             1 0 10000:10:346
runActorBenchmark BankTransaction                1 0 1000:10000

# Parallelism
runActorBenchmark RadixSort                1 0 10000:65536:74755
runActorBenchmark UnbalancedCobwebbedTree  1 0 10000:10:0:1
runActorBenchmark TrapezoidalApproximation 1 0 100:100000:1:5
runActorBenchmark AStarSearch              1 0 100:10
runActorBenchmark NQueens                  1 0 20:8:4


if [ "$1" = "update" ] && [ "$NEEDS_UPDATE" = true ]
then
  ## move old results out of the way, and new results to expected folder
  rm -Rf $SCRIPT_PATH/old-results
  mv $SCRIPT_PATH/expected-results $SCRIPT_PATH/old-results
  mv $SCRIPT_PATH/results $SCRIPT_PATH/expected-results
  ## update the archive
  tar --exclude='._*' -cjf $SCRIPT_PATH/expected-results.tar.bz2 -C $SCRIPT_PATH expected-results
fi
