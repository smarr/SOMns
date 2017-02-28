#!/bin/bash
# quit on first error
set -e

declare -a arr=("PingPong 1 0 40000"
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
"BankTransaction 1 0 1000:100000"
"RadixSort 1 0 50000:65536:74755"
"UnbalancedCobwebbedTree 1 0 100000:10:500:100"
"TrapezoidalApproximation 1 0 100:1000000:1:5"
"AStarSearch 1 0 100:20"
"NQueens 1 0 20:10:4") 

## Determine absolute path of script
pushd `dirname $0` > /dev/null
SCRIPT_PATH=`pwd`
popd > /dev/null

SOM_DIR=$SCRIPT_PATH/../..

for args in "${arr[@]}"  
do 
	echo "$args"
	echo "Tracing:"
	$SOM_DIR/som -G -JXmx1500m -vmd -at core-lib/Benchmarks/AsyncHarness.som Savina.$args
	echo ""
        echo "Replay:"
	$SOM_DIR/som -G -JXmx1500m -vmd -at -r core-lib/Benchmarks/AsyncHarness.som Savina.$args
	echo ""
	echo "========================================================"
	echo ""
done

