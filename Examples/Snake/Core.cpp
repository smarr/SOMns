#include <string.h>

//#include "../misc/defs.h"
//#include "../vmobjects/VMObject.h"
#include "../../vmobjects/PrimitiveRoutine.h"

#include "Terminal.h"

#include "../../primitivesCore/Routine.h"
#include "../../primitivesCore/PrimitiveContainer.h"
#include "../../primitivesCore/PrimitiveLoader.h"

static PrimitiveLoader* loader = NULL;
//map<pString, PrimitiveContainer*> primitiveObjects;
//"Constructor"
static bool initialized = false;
extern "C" void setup() {
    if (!loader) {
        //Initialize loader
        loader = new PrimitiveLoader();
        loader->AddPrimitiveObject("Terminal", 
            static_cast<PrimitiveContainer*>(new Terminal()));
}

extern "C" bool supportsClass(const char* name) {
    //if (!loader) setup();
    return loader->SupportsClass(name);
}



extern "C" void tearDown() {
    //primitiveClasses.clear();
    if (loader) delete loader;
    //if (primitiveObjects) delete primitiveObjects;
}

extern "C" PrimitiveRoutine* create(const pString& cname, const pString& fname, bool isPrimitive) {

#ifdef __DEBUG
    cout << "Loading PrimitiveContainer: " << cname << "::" << fname << endl;
#endif
    //if (!loader) setup();
    
    return loader->GetPrimitiveRoutine(cname, fname, isPrimitive);
}

