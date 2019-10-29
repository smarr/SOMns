#!/bin/bash
# quit on first error
set -e

if [ "$1" = "1" ]
then
  declare -a Savina=(
    "PingPong 1 0 40000"
    "Counting 1 0 200000"
    "ForkJoinThroughput 1 0 3000:60"
    "ForkJoinActorCreation 1 0 40000"
    "ThreadRing 1 0 100:100000"
    "Chameneos 1 0 100:100000"
    "BigContention 1 0 2000:120"
    "ConcurrentDictionary 1 0 20:1000:20"
    "ConcurrentSortedLinkedList 1 0 10:1500:10:1"
    "ProducerConsumerBoundedBuffer 1 0 40:10:10:60"
    "Philosophers 1 0 20:5000"
    "SleepingBarber 1 0 2500:1000:1000:1000"
    "CigaretteSmokers 1 0 10000:200"
    "LogisticsMapSeries 1 0 25000:10:346"
  )

  declare -a Validation=()

  declare -a CSP=()

  declare -a Threads=()
else
  declare -a Savina=(
    "BankTransaction 1 0 1000:100000"
    "RadixSort 1 0 50000:65536:74755"
    "UnbalancedCobwebbedTree 1 0 100000:10:500:100"
    "TrapezoidalApproximation 1 0 100:1000000:1:5"
    "AStarSearch 1 0 100:20"
    "NQueens 1 0 20:10:4"
  )

  declare -a Validation=(
    "Counting 100 0 1000"
    "Philosophers 100 0 5:5 25"
    "DeadLock 100 0 4:2:3"
    "Messages 100 0 1000"
    "Sequence 100 0 100"
  )

  declare -a CSP=(
    "PingPong 1 0 4000 2"
    "ForkJoinThroughput 1 0 4000 4"
    "Philosophers 1 0 500 4"
  )

  declare -a Threads=(
    "ProducerConsumer 1 0 10 4"
    "Philosophers 1 0 500 4"
  )
fi

## Determine absolute path of script
pushd `dirname $0` > /dev/null
SCRIPT_PATH=`pwd`
popd > /dev/null

SOM_DIR=$SCRIPT_PATH/../..


echo   "===================== Actor Replay ====================="
for args in "${Savina[@]}"
do
  echo "$args"
  echo "Tracing:"
  $SOM_DIR/som -G -JXmx1500m -at core-lib/Benchmarks/AsyncHarness.ns Savina.$args
  echo ""
  echo "Replay:"
  $SOM_DIR/som -G -JXmx1500m -vmd -r core-lib/Benchmarks/AsyncHarness.ns Savina.$args
  echo ""
  echo "========================================================"
  echo ""
done

for args in "${Validation[@]}"
do
  echo "$args"
  echo "Tracing:"
  $SOM_DIR/som -G -JXmx1500m -at core-lib/Benchmarks/ImpactHarness.ns Validation.$args | grep -o 'success: .*' > $SCRIPT_PATH/orig.txt
  echo ""
  echo "Replay:"
  $SOM_DIR/som -G -JXmx1500m -vmd -r core-lib/Benchmarks/ImpactHarness.ns Validation.$args | grep -o 'success: .*' > $SCRIPT_PATH/repl.txt
  diff $SCRIPT_PATH/orig.txt $SCRIPT_PATH/repl.txt
  echo ""
  echo "========================================================"
  echo ""
done

echo   "====================== CSP Replay ======================"
for args in "${CSP[@]}"
do
  echo "$args"
  echo "Tracing:"
  $SOM_DIR/som -t8 -G -JXmx1500m -at core-lib/Benchmarks/Harness.ns SavinaCSP.$args
  echo ""
  echo "Replay:"
  $SOM_DIR/som -t8 -G -JXmx1500m -vmd -r core-lib/Benchmarks/Harness.ns SavinaCSP.$args
  echo ""
  echo "========================================================"
  echo ""
done

echo   "================ Thread & Locks Replay ================="
for args in "${Threads[@]}"
do
  echo "$args"
  echo "Tracing:"
  $SOM_DIR/som -t8 -G -JXmx1500m -at core-lib/Benchmarks/Harness.ns MutexSuite.$args
  echo ""
  echo "Replay:"
  $SOM_DIR/som -t8 -G -JXmx1500m -vmd -r core-lib/Benchmarks/Harness.ns MutexSuite.$args
  echo ""
  echo "========================================================"
  echo ""
done