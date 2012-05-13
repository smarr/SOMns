#!/usr/bin/env make

TARGET		=Terminal


################ ONLY CHANGE STUFF BELOW IF YOU REALLY HAVE ###################


CC			=g++
CFLAGS		=-Wno-endif-labels -O2 $(DBG_FLAGS) $(INCLUDES)
LDFLAGS		=-enable-auto-import $(LIBRARIES)

INSTALL		=install

############ global stuff -- overridden by ../Makefile

ROOT_DIR	?= $(PWD)/../..
BUILD_DIR   ?= $(ROOT_DIR)/build
SRC_DIR		?= $(ROOT_DIR)/src

ST_DIR		?= $(ROOT_DIR)/Smalltalk
EX_DIR		?= $(ROOT_DIR)/Examples
TEST_DIR	?= $(ROOT_DIR)/TestSuite

############# include path

INCLUDES		=-I$(SRC_DIR)
LIBRARIES		=-L$(ROOT_DIR)

ifneq ($(OS),)
# only Windows has OS predefined.
	LIBS		=-lm -l../../CPPSOM -l../../PrimitiveCore
endif

all: $(TARGET).csp

debug : DBG_FLAGS=-DDEBUG -g
debug: all

profiling : DBG_FLAGS=-g -pg
profiling : LDFLAGS+=-pg
profiling: all

$(TARGET).csp:
	$(CC) $(CFLAGS) $(LDFLAGS) -shared -g \
		$(PWD)/$(TARGET).cpp -o $(TARGET).csp $(LIBS)
	
clean:
	rm -Rf $(TARGET).csp

