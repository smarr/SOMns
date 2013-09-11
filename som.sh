#!/bin/sh
# -G:TruffleCompilationThreshold=3 -Xbootclasspath/a:build/classes \
java -server -cp build/classes:libs/com.oracle.truffle.api.jar \
        som.vm.Universe \
		"$@"
