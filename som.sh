#!/bin/sh
# -G:TruffleCompilationThreshold=3 -Xbootclasspath/a:build/classes \
java -server -cp build/classes:libs/com.oracle.truffle.api.jar:libs/com.oracle.truffle.api.dsl.jar \
        som.vm.Universe \
		"$@"
