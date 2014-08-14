/*
 * $Id: Terminal.c 426 2008-05-22 08:22:07Z stefan.marr $
 *
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


/*************************************************/
#pragma mark * Included Headers                  *
/*************************************************/


#include <vmobjects/VMObject.h>
#include <vmobjects/VMFrame.h>
#include <vmobjects/VMString.h>
#include <vm/Universe.h>

#include <primitivesCore/Routine.h>

#include <termios.h>
#include <fcntl.h>

#include "Terminal.h"

/*************************************************/
#pragma mark * Primitive Foreward Declaration    *
/*************************************************/



/*************************************************/
/*************************************************/
/*************************************************/
#pragma mark * Primitive Implementatition here   *
/*************************************************/
/*************************************************/
/*************************************************/


struct termios old_tty;
Terminal::Terminal() : PrimitiveContainer() {
    this->terminalStream = 0;
    this->SetPrimitive("getChar", static_cast<PrimitiveRoutine*>(
                        new (_HEAP) Routine<Terminal>(this, &Terminal::getChar)));
    this->SetPrimitive("uninit", static_cast<PrimitiveRoutine*>(
                        new (_HEAP) Routine<Terminal>(this, &Terminal::uninit)));
    this->SetPrimitive("init", static_cast<PrimitiveRoutine*>(
                        new (_HEAP) Routine<Terminal>(this, &Terminal::init)));
    this->SetPrimitive("sleepFor_",  static_cast<PrimitiveRoutine*>(
                        new (_HEAP) Routine<Terminal>(this, &Terminal::sleepFor_)));
}

void Terminal::getChar(VMObject* object, VMFrame* frame) {
  char chr;
  char result[2];
  pString str = NULL;
  VMObject* vmStr = NULL;
  frame->Pop();
  //VMObject self __attribute__((unused)) = SEND(frame, pop);
  
  if (read(terminalStream, &chr, sizeof(chr)) > 0) {
	  result[0] = chr;
	  result[1] = 0;
	  
	  str = pString(result);
      vmStr = (VMObject*)GetUniverse()->NewString(str);
      frame->Push(vmStr);
  } else {
      frame->Push(Globals::NilObject());
  }
}

void Terminal::uninit(VMObject* object, VMFrame* frame) {
	close(terminalStream);
	tcsetattr(0, TCSANOW, &old_tty);
}

void Terminal::init(VMObject* object, VMFrame* frame) {
	struct termios tty;
	
	// Perpare terminal settings and change to non-canonical mode for char-wise input
	tcgetattr(0, &old_tty);
	tty = old_tty;
	tty.c_lflag = tty.c_lflag & ~(ECHO | ECHOK | ICANON);
	tty.c_cc[VTIME] = 1;
	tcsetattr(0, TCSANOW, &tty);
	
	terminalStream = open("/dev/tty", O_RDONLY | O_NONBLOCK);
	if (terminalStream < 0) {
        GetUniverse()->ErrorExit("Could not open /dev/tty for read\n");
	}
}

void Terminal::sleepFor_(VMObject* object, VMFrame* frame) {
    VMInteger* miliSeconds = (VMInteger*)frame->Pop();
    int64_t sec = (int64_t)miliSeconds->GetEmbeddedInteger() * 1000;
    sync();
    usleep(sec);
}

/*************************************************/
/*************************************************/
/*************************************************/
#pragma mark * EOF                               *
/*************************************************/
/*************************************************/
/*************************************************/

