#!/bin/sh
# -G:TruffleCompilationThreshold=3 -Xbootclasspath/a:build/classes \
java -server -cp build/classes:libs/truffle.jar \
        som.VM --platform core-lib/Platform.som \
		"$@"
