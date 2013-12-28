#!/bin/sh
# -G:TruffleCompilationThreshold=3 -Xbootclasspath/a:build/classes \
java -server -cp build/classes:libs/truffle.jar \
        som.vm.Universe \
		"$@"
