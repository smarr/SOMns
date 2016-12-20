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

SOM_DIR=$SCRIPT_PATH/../../..

## create folder for new results
mkdir -p $SCRIPT_PATH/results/

## extract expected results
tar xf $SCRIPT_PATH/expected-results.tar.bz2 -C $SCRIPT_PATH/

function runBenchmark {
  BENCH=$1
  HARNESS="$SOM_DIR/som -dm -Ddm.metrics=$SCRIPT_PATH/results/$BENCH \
    -G $SOM_DIR/core-lib/Benchmarks/Harness.som"
  echo $HARNESS $@
  $HARNESS $@
  
  diff -r $SCRIPT_PATH/expected-results/$BENCH $SCRIPT_PATH/results/$BENCH
}

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

if [ "$1" = "update" ]
then
  ## move old results out of the way, and new results to expected folder
  rm -Rf $SCRIPT_PATH/old-results
  mv $SCRIPT_PATH/expected-results $SCRIPT_PATH/old-results
  mv $SCRIPT_PATH/results $SCRIPT_PATH/expected-results
  ## update the archive
  tar cjf $SCRIPT_PATH/expected-results.tar.bz2 -C $SCRIPT_PATH expected-results
fi
