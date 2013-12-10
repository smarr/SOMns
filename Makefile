#!/usr/bin/env make -f

all: compile

clean:
	ant clean

compile: build/som.jar

build/som.jar:
	ant jar

test:
	ant test
