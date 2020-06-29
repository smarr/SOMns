#!/bin/bash
# quit on first error
set -e

function cleanup()
{
  echo "saving trace"
  #tar -cjf traces.tar.bz2 traces
  #curl --upload-file ./traces.tar.bz2 https://transfer.sh/traces.tar.bz2
}

trap cleanup EXIT

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

  declare -a STM=()
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
    #Currently broken due to timerthread being incompatible
    #"Counting 100 0 1000"
    #"Philosophers 100 0 5:5 25"
    #"DeadLock 100 0 4:2:3"
    #"Messages 100 0 1000"
    #"Sequence 100 0 100"
  )

  declare -a CSP=(
    "PingPong 1 0 4000 2"
    "ForkJoinThroughput 1 0 4000 4"
    "Philosophers 1 0 500 4"
  )

  declare -a Threads=(
    "MutexSuite.ProducerConsumer 1 0 10 4"
    "MutexSuite.Philosophers 1 0 500 4"
    "Lee 1 0 4 4"
    "Vacation 1 0 7 4"
  )

  declare -a STM=(
    "LeeSTM 1 0 4 1"
    "VacationSTM 1 0 7 1"
  )

  declare -a Multi=(
    "MultiParadigmBench 10 0 100"
  )

fi

## Determine absolute path of script
pushd `dirname $0` > /dev/null
SCRIPT_PATH=`pwd`
popd > /dev/null

SOM_DIR=$SCRIPT_PATH/../..

function test {
  local title="$1"
  shift
  local settings="$1"
  shift
  local harness="$1"
  shift
  local argss=("$@")

  echo   "===================== $title ====================="
  for i in "${argss[@]}"
  do
    echo "$args"
    echo "Tracing:"
    echo "$SOM_DIR/som $settings -at core-lib/Benchmarks/$harness$i"
    $SOM_DIR/som $settings -at core-lib/Benchmarks/$harness$i
    echo ""
    echo "Replay:"
    $SOM_DIR/som $settings -r core-lib/Benchmarks/$harness$i
    echo ""
    echo "========================================================"
    echo ""
  done
}

test "Actor Replay Sender Side" "-G -JXmx1500m" "AsyncHarness.ns Savina." "${Savina[@]}"

test "Actor Replay Receiver Side" "-G -JXmx1500m -art" "AsyncHarness.ns Savina." "${Savina[@]}"

#for args in "${Validation[@]}"
#do
#  echo "$args"
#  echo "Tracing:"
#  $SOM_DIR/som -G -JXmx1500m -at core-lib/Benchmarks/ImpactHarness.ns Validation.$args | grep -o 'success: .*' > $SCRIPT_PATH/orig.txt
#  echo ""
#  echo "Replay:"
#  $SOM_DIR/som -G -JXmx1500m -vmd -r core-lib/Benchmarks/ImpactHarness.ns Validation.$args | grep -o 'success: .*' > $SCRIPT_PATH/repl.txt
#  diff $SCRIPT_PATH/orig.txt $SCRIPT_PATH/repl.txt
#  echo ""
#  echo "========================================================"
#  echo ""
#done

test "CSP Replay" "-t8 -G -JXmx1500m -vmd" "Harness.ns SavinaCSP." "${CSP[@]}"

test "Thread & Locks Replay" "-t8 -G -JXmx1500m -vmd" "Harness.ns " "${Threads[@]}"

test "STM Replay" "-t8 -G -JXmx1500m -vmd" "Harness.ns " "${STM[@]}"

test "STM Replay" "-t8 -G -JXmx1500m -vmd" "AsyncHarness.ns " "${Multi[@]}"
