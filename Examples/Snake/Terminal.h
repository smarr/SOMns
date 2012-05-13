#pragma once

#ifndef TERMINAL_H_
#define TERMINAL_H_

#include <primitivesCore/PrimitiveContainer.h>

class Terminal : public PrimitiveContainer {
public:
    Terminal();
    void sleepFor_(VMObject* object, VMFrame* frame);
    void getChar(VMObject* object, VMFrame* frame);
    void uninit(VMObject* object, VMFrame* frame);
    void init(VMObject* object, VMFrame* frame);
private:
    int terminalStream;
};

#endif;
