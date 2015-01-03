/*
Copyright (c) 2001-2008 see AUTHORS file
Software Architecture Group, Hasso Plattner Institute, Potsdam, Germany
http://www.hpi.uni-potsdam.de/swa/

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
  */

#include <string.h>
#include <stdlib.h>
#include <stdio.h>
#include <stdbool.h>
#include <stddef.h>


/*************************************************/
#pragma mark * Included Headers                  *
/*************************************************/


#include <vmobjects/VMFrame.h>
#include <vmobjects/VMString.h>
#include <vm/Universe.h>


#include <termios.h>
#include <fcntl.h>

/*************************************************/
#pragma mark * Primitive Foreward Declaration    *
/*************************************************/

extern "C" {

void _Terminal_sleepFor_(pVMObject object, pVMFrame frame);
void _Terminal_getChar(pVMObject object, pVMFrame frame);
void _Terminal_uninit(pVMObject object, pVMFrame frame);
void _Terminal_init(pVMObject object, pVMFrame frame);

}

/*************************************************/
#pragma mark * Internal functions and init.      *
/*************************************************/

/*** Lib initialization **/
#ifdef __GNUC__
void init(void) __attribute__((constructor));
void fini(void) __attribute__((destructor));
#else
void _init(void);
void _fini(void);
#pragma init _init
#pragma fini _fini
#endif

#ifdef __GNUC__
void init(void)
#else
void _init(void)
#endif
{
	// Call init funcions.
	

}

#ifdef __GNUC__
void fini(void)
#else
void _fini(void)
#endif
{
	
}

// Classes supported by this lib.
static char *supported_classes[] = {
    "Terminal",
    nullptr
};


/*************************************************/
#pragma mark * Exported functions starting here  *
/*************************************************/

extern "C" {

// returns, whether this lib is responsible for a specific class
bool		supports_class(const char* name) {
	
	char **iter=supported_classes;
	while(*iter)
		if (strcmp(name,*iter++)==0)
			return true;
	return false;
	
}

bool initialized = false;


/**
 * init_csp()
 *
 * the library initializer. It is not equal to the init/fini-pair, as for them, 
 * the init is executed upon every loading of the shared library.
 *
 * All work that needs to be done before the actual primitives are assigned
 * should be called from this function.
 */
void init_csp(void) {
	initialized = false;
}


ptrdiff_t terminalStream;
struct termios old_tty;

void init_the_terminal() {
	
	if (initialized)
		return;
	
	struct termios tty;
	
	// Perpare terminal settings and change to non-canonical mode for char-wise input
	tcgetattr(0, &old_tty);
	tty = old_tty;
	tty.c_lflag = tty.c_lflag & ~(ECHO | ECHOK | ICANON);
	tty.c_cc[VTIME] = 1;
	tcsetattr(0, TCSANOW, &tty);
	
	terminalStream = open("/dev/tty", O_RDONLY | O_NONBLOCK);
	if (terminalStream < 0) {
		Universe_error_exit("Could not open /dev/tty for read\n");
	}
	initialized = true;
}


/*************************************************/
/*************************************************/
/*************************************************/
#pragma mark * Primitive Implementatition here   *
/*************************************************/
/*************************************************/
/*************************************************/



void Terminal_getChar(pVMObject object, pVMFrame frame) {
  init_the_terminal();
  char chr;
  char result[2];
  pString str = nullptr;
  VMObject* vmStr = nullptr;
  
  pVMObject self __attribute__((unused)) = SEND(frame, pop);
  
  if (read(terminalStream, &chr, sizeof(chr)) > 0) {
	  result[0] = chr;
	  result[1] = 0;
	  
	  str = String_new(result);
	  vmStr = (pVMObject)VMString_new_with(str);
	  SEND(frame, push, vmStr);
  } else {
	  SEND(frame, push, nil_object);
  }
}

void Terminal_uninit(pVMObject object, pVMFrame frame) {
	struct termios tty;

	close(terminalStream);
	tcsetattr(0, TCSANOW, &tty);
	tcsetattr(0, TCSANOW, &old_tty);
}

void Terminal_init(pVMObject object, pVMFrame frame) {
}

void Terminal_sleepFor_(pVMObject object, pVMFrame frame) {
    pVMInteger miliSeconds = (pVMInteger)SEND(frame, pop);
    int64_t sec = (int64_t)SEND(miliSeconds, get_embedded_integer) * 1000;
    sync();
    usleep(sec);
}

}

/*************************************************/
/*************************************************/
/*************************************************/
#pragma mark * EOF                               *
/*************************************************/
/*************************************************/
/*************************************************/

